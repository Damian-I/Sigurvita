package com.zemmy.sigurvita.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.PageBubbles
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "BubbleDetector"

class BubbleDetector(
    private val context: Context,
    private val config: BubbleDetectorConfig = BubbleDetectorConfig()
) {

    private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var interpreter: Interpreter? = null
    private var inputSize = 640

    private var detTensorIdx = 0
    private var protoTensorIdx = 1

    private var detShape = intArrayOf(1, 37, 8400)
    private var protoShape = intArrayOf(1, 160, 160, 32)

    private var inputDataType: DataType = DataType.FLOAT32
    private var isInputNCHW: Boolean = false
    private var detDataType: DataType = DataType.FLOAT32
    private var protoDataType: DataType = DataType.FLOAT32

    private var inputQuantScale: Float = 0f
    private var inputQuantZeroPoint: Int = 0
    private var detQuantScale: Float = 0f
    private var detQuantZeroPoint: Int = 0
    private var protoQuantScale: Float = 0f
    private var protoQuantZeroPoint: Int = 0

    // Reusable buffers to avoid per-frame GC pressure (~7 MB for 640×640 input)
    private var reuseInputBuffer: ByteBuffer? = null
    private var reusePixels: IntArray? = null
    private var lastInputBufferSize: Int = 0

    private suspend fun getInterpreter(): Interpreter = withContext(singleThreadDispatcher) {
        if (interpreter == null) {
            val fileDescriptor = context.assets.openFd("model.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            }
            val interp = Interpreter(modelBuffer, options)

            val inputTensor = interp.getInputTensor(0)
            inputDataType = inputTensor.dataType()
            val inputShape = inputTensor.shape()
            isInputNCHW = (inputShape.size == 4 && inputShape[1] == 3)
            if (inputShape.size >= 3) {
                val s1 = inputShape[1]
                val s2 = inputShape[2]
                inputSize = if (s1 > 3) s1 else s2
            }
            val inQuant: Tensor.QuantizationParams? = inputTensor.quantizationParams()
            if (inQuant != null) {
                inputQuantScale = inQuant.scale
                inputQuantZeroPoint = inQuant.zeroPoint
            }
            Log.i(TAG, "Input tensor 0: shape=${inputShape.contentToString()}, type=$inputDataType, size=$inputSize, scale=$inputQuantScale, zeroPoint=$inputQuantZeroPoint")

            val numOutputs = interp.outputTensorCount
            for (i in 0 until numOutputs) {
                val tensor = interp.getOutputTensor(i)
                val shape = tensor.shape()
                val quant: Tensor.QuantizationParams? = tensor.quantizationParams()
                Log.i(TAG, "Output tensor $i: shape=${shape.contentToString()}, type=${tensor.dataType()}, quantScale=${quant?.scale}, quantZeroPoint=${quant?.zeroPoint}")

                if (shape.size == 3) {
                    detTensorIdx = i
                    detShape = shape
                    detDataType = tensor.dataType()
                    if (quant != null) {
                        detQuantScale = quant.scale
                        detQuantZeroPoint = quant.zeroPoint
                    }
                } else if (shape.size == 4) {
                    protoTensorIdx = i
                    protoShape = shape
                    protoDataType = tensor.dataType()
                    if (quant != null) {
                        protoQuantScale = quant.scale
                        protoQuantZeroPoint = quant.zeroPoint
                    }
                }
            }

            Log.i(TAG, "Resolved detTensorIdx=$detTensorIdx (shape=${detShape.contentToString()}), protoTensorIdx=$protoTensorIdx (shape=${protoShape.contentToString()})")

            interpreter = interp
        }
        interpreter!!
    }

    suspend fun detect(bitmap: Bitmap, pageIndex: Int, confidence: Float = 0.10f): PageBubbles = withContext(singleThreadDispatcher) {
        val interp = getInterpreter()
        val origW = bitmap.width
        val origH = bitmap.height

        val gain = min(inputSize.toFloat() / origW, inputSize.toFloat() / origH)
        val padX = (inputSize - origW * gain) / 2f
        val padY = (inputSize - origH * gain) / 2f

        val letterbox = LetterboxParams(
            inputSize = inputSize,
            originalWidth = origW,
            originalHeight = origH,
            gain = gain,
            padX = padX,
            padY = padY
        )

        // Draw original bitmap letterboxed into inputSize x inputSize bitmap
        val letterboxedBitmap = createBitmap(inputSize, inputSize)
        val canvas = Canvas(letterboxedBitmap)
        canvas.drawColor(Color.rgb(114, 114, 114))

        val destW = (origW * gain).toInt()
        val destH = (origH * gain).toInt()
        val srcRect = Rect(0, 0, origW, origH)
        val dstRect = RectF(padX, padY, padX + destW, padY + destH)
        canvas.drawBitmap(bitmap, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))

        // Preprocess input bitmap
        val isInputFloat = inputDataType == DataType.FLOAT32
        val requiredBufferSize = if (isInputFloat) {
            1 * inputSize * inputSize * 3 * 4
        } else {
            1 * inputSize * inputSize * 3
        }
        val inputBuffer = if (reuseInputBuffer != null && lastInputBufferSize == requiredBufferSize) {
            reuseInputBuffer!!.also { it.rewind() }
        } else {
            ByteBuffer.allocateDirect(requiredBufferSize).also {
                reuseInputBuffer = it
                lastInputBufferSize = requiredBufferSize
            }
        }
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val pixelCount = inputSize * inputSize
        val pixels = if (reusePixels != null && reusePixels!!.size == pixelCount) {
            reusePixels!!
        } else {
            IntArray(pixelCount).also { reusePixels = it }
        }
        letterboxedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        if (isInputFloat) {
            if (isInputNCHW) {
                for (pixel in pixels) inputBuffer.putFloat((pixel shr 16 and 0xFF) / 255f)
                for (pixel in pixels) inputBuffer.putFloat((pixel shr 8 and 0xFF) / 255f)
                for (pixel in pixels) inputBuffer.putFloat((pixel and 0xFF) / 255f)
            } else {
                for (pixel in pixels) {
                    inputBuffer.putFloat((pixel shr 16 and 0xFF) / 255f)
                    inputBuffer.putFloat((pixel shr 8 and 0xFF) / 255f)
                    inputBuffer.putFloat((pixel and 0xFF) / 255f)
                }
            }
        } else if (inputDataType == DataType.INT8) {
            if (isInputNCHW) {
                for (pixel in pixels) {
                    val r = (pixel shr 16 and 0xFF) / 255f
                    val rB = if (inputQuantScale > 0f) (r / inputQuantScale + inputQuantZeroPoint).roundToInt().coerceIn(-128, 127).toByte() else (r * 255f - 128f).toInt().toByte()
                    inputBuffer.put(rB)
                }
                for (pixel in pixels) {
                    val g = (pixel shr 8 and 0xFF) / 255f
                    val gB = if (inputQuantScale > 0f) (g / inputQuantScale + inputQuantZeroPoint).roundToInt().coerceIn(-128, 127).toByte() else (g * 255f - 128f).toInt().toByte()
                    inputBuffer.put(gB)
                }
                for (pixel in pixels) {
                    val b = (pixel and 0xFF) / 255f
                    val bB = if (inputQuantScale > 0f) (b / inputQuantScale + inputQuantZeroPoint).roundToInt().coerceIn(-128, 127).toByte() else (b * 255f - 128f).toInt().toByte()
                    inputBuffer.put(bB)
                }
            } else {
                for (pixel in pixels) {
                    val r = (pixel shr 16 and 0xFF) / 255f
                    val g = (pixel shr 8 and 0xFF) / 255f
                    val b = (pixel and 0xFF) / 255f

                    val rB = if (inputQuantScale > 0f) (r / inputQuantScale + inputQuantZeroPoint).roundToInt().coerceIn(-128, 127).toByte() else (r * 255f - 128f).toInt().toByte()
                    val gB = if (inputQuantScale > 0f) (g / inputQuantScale + inputQuantZeroPoint).roundToInt().coerceIn(-128, 127).toByte() else (g * 255f - 128f).toInt().toByte()
                    val bB = if (inputQuantScale > 0f) (b / inputQuantScale + inputQuantZeroPoint).roundToInt().coerceIn(-128, 127).toByte() else (b * 255f - 128f).toInt().toByte()

                    inputBuffer.put(rB)
                    inputBuffer.put(gB)
                    inputBuffer.put(bB)
                }
            }
        } else if (inputDataType == DataType.UINT8) {
            if (isInputNCHW) {
                for (pixel in pixels) inputBuffer.put((pixel shr 16 and 0xFF).toByte())
                for (pixel in pixels) inputBuffer.put((pixel shr 8 and 0xFF).toByte())
                for (pixel in pixels) inputBuffer.put((pixel and 0xFF).toByte())
            } else {
                for (pixel in pixels) {
                    inputBuffer.put((pixel shr 16 and 0xFF).toByte())
                    inputBuffer.put((pixel shr 8 and 0xFF).toByte())
                    inputBuffer.put((pixel and 0xFF).toByte())
                }
            }
        }

        // Prepare Output buffers dynamically
        val outputs = mutableMapOf<Int, Any>()

        var floatDetArray: Array<Array<FloatArray>>? = null
        var byteDetArray: Array<Array<ByteArray>>? = null

        if (detDataType == DataType.FLOAT32) {
            floatDetArray = Array(1) { Array(detShape[1]) { FloatArray(detShape[2]) } }
            outputs[detTensorIdx] = floatDetArray
        } else {
            byteDetArray = Array(1) { Array(detShape[1]) { ByteArray(detShape[2]) } }
            outputs[detTensorIdx] = byteDetArray
        }

        var floatProtoArray: Array<Array<Array<FloatArray>>>? = null
        var byteProtoArray: Array<Array<Array<ByteArray>>>? = null

        if (protoDataType == DataType.FLOAT32) {
            floatProtoArray = Array(1) { Array(protoShape[1]) { Array(protoShape[2]) { FloatArray(protoShape[3]) } } }
            outputs[protoTensorIdx] = floatProtoArray
        } else {
            byteProtoArray = Array(1) { Array(protoShape[1]) { Array(protoShape[2]) { ByteArray(protoShape[3]) } } }
            outputs[protoTensorIdx] = byteProtoArray
        }

        val inputs = arrayOf<Any>(inputBuffer)
        interp.runForMultipleInputsOutputs(inputs, outputs)

        // Convert / Dequantize detections to Array<Array<FloatArray>> [1, channels, anchors]
        var detArray = floatDetArray
        if (detArray == null && byteDetArray != null) {
            val d1 = detShape[1]
            val d2 = detShape[2]
            detArray = Array(1) { Array(d1) { FloatArray(d2) } }
            val scale = if (detQuantScale > 0f) detQuantScale else 1f
            val zp = detQuantZeroPoint
            val isUnsigned = (detDataType == DataType.UINT8)
            for (i in 0 until d1) {
                for (j in 0 until d2) {
                    val rawByte = byteDetArray[0][i][j]
                    val rawVal = if (isUnsigned) (rawByte.toInt() and 0xFF) else rawByte.toInt()
                    detArray[0][i][j] = (rawVal - zp) * scale
                }
            }
        }

        // Transpose detections if shape is [1, anchors, channels] instead of [1, channels, anchors]
        if (detArray != null && detArray[0].size > detArray[0][0].size && detArray[0][0].isNotEmpty()) {
            val numAnchors = detArray[0].size
            val numC = detArray[0][0].size
            val transposed = Array(1) { Array(numC) { FloatArray(numAnchors) } }
            for (i in 0 until numAnchors) {
                for (j in 0 until numC) {
                    transposed[0][j][i] = detArray[0][i][j]
                }
            }
            detArray = transposed
        }

        // Convert / Dequantize prototypes to Array<Array<Array<FloatArray>>>
        var protoArray = floatProtoArray
        if (protoArray == null && byteProtoArray != null) {
            val p1 = protoShape[1]
            val p2 = protoShape[2]
            val p3 = protoShape[3]
            protoArray = Array(1) { Array(p1) { Array(p2) { FloatArray(p3) } } }
            val scale = if (protoQuantScale > 0f) protoQuantScale else 1f
            val zp = protoQuantZeroPoint
            val isUnsigned = (protoDataType == DataType.UINT8)
            for (i in 0 until p1) {
                for (j in 0 until p2) {
                    for (k in 0 until p3) {
                        val rawByte = byteProtoArray[0][i][j][k]
                        val rawVal = if (isUnsigned) (rawByte.toInt() and 0xFF) else rawByte.toInt()
                        protoArray[0][i][j][k] = (rawVal - zp) * scale
                    }
                }
            }
        }

        if (detArray == null || protoArray == null) {
            Log.e(TAG, "Failed to decode outputs from model")
            return@withContext PageBubbles(pageIndex, origW, origH, letterbox, emptyList())
        }

        val effectiveConfig = config.copy(confThreshold = confidence)
        val localDecoder = YoloSegDecoder(effectiveConfig)
        val bubbles = localDecoder.decode(detArray, protoArray, letterbox)
        Log.i(TAG, "Page $pageIndex detected ${bubbles.size} speech bubbles at conf=$confidence")

        PageBubbles(
            pageIndex = pageIndex,
            pageWidth = origW,
            pageHeight = origH,
            letterbox = letterbox,
            bubbles = bubbles
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

