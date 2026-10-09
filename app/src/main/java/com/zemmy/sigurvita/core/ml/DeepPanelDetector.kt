package com.zemmy.sigurvita.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import com.zemmy.sigurvita.core.util.PanelInfo
import com.zemmy.sigurvita.core.util.PanelMap
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

private const val TAG = "DeepPanelDetector"

/**
 * Tunable minimum area ratio for panel detection.
 * DeepPanel Android defaults to 3% (0.03), but 0.8% (0.008) preserves small inset panels.
 */
const val MIN_PANEL_AREA_RATIO = 0.008f

class DeepPanelDetector(private val context: Context) {

    private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var interpreter: Interpreter? = null
    private val modelInputImageSize = 224

    private suspend fun getInterpreter(): Interpreter = withContext(singleThreadDispatcher) {
        if (interpreter == null) {
            val fileDescriptor = context.assets.openFd("deeppanel.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(TAG, "DeepPanel interpreter loaded successfully")
        }
        interpreter!!
    }

    suspend fun detect(bitmap: Bitmap): PanelMap? = withContext(singleThreadDispatcher) {
        try {
            val interp = getInterpreter()
            val origW = bitmap.width
            val origH = bitmap.height

            val gain = min(modelInputImageSize.toFloat() / origW, modelInputImageSize.toFloat() / origH)
            val padX = (modelInputImageSize - origW * gain) / 2f
            val padY = (modelInputImageSize - origH * gain) / 2f

            val letterbox = LetterboxParams(
                inputSize = modelInputImageSize,
                originalWidth = origW,
                originalHeight = origH,
                gain = gain,
                padX = padX,
                padY = padY
            )

            // Letterbox original bitmap to 224x224 black background
            val letterboxedBitmap = Bitmap.createBitmap(modelInputImageSize, modelInputImageSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(letterboxedBitmap)
            val paint = Paint().apply { color = Color.BLACK }
            canvas.drawRect(0f, 0f, modelInputImageSize.toFloat(), modelInputImageSize.toFloat(), paint)

            val matrix = Matrix()
            matrix.setRectToRect(
                RectF(0f, 0f, origW.toFloat(), origH.toFloat()),
                RectF(0f, 0f, modelInputImageSize.toFloat(), modelInputImageSize.toFloat()),
                Matrix.ScaleToFit.CENTER
            )
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))

            // Preprocess float32 RGB / 255
            val inputBuffer = ByteBuffer.allocateDirect(1 * modelInputImageSize * modelInputImageSize * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            inputBuffer.rewind()

            val pixels = IntArray(modelInputImageSize * modelInputImageSize)
            letterboxedBitmap.getPixels(pixels, 0, modelInputImageSize, 0, 0, modelInputImageSize, modelInputImageSize)

            for (pixel in pixels) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
                inputBuffer.putFloat((pixel and 0xFF) / 255f)
            }

            // Output tensor shape: [1, 224, 224, 3] (0=bg, 1=border, 2=content)
            val prediction = Array(1) { Array(modelInputImageSize) { Array(modelInputImageSize) { FloatArray(3) } } }
            interp.run(inputBuffer, prediction)

            // Step 1: Content mask with x/y index swap matching deep-panel-android.cpp
            val rawMatrix = Array(modelInputImageSize) { IntArray(modelInputImageSize) }
            for (i in 0 until modelInputImageSize) {
                for (j in 0 until modelInputImageSize) {
                    val pixelScores = prediction[0][j][i]
                    val bg = pixelScores[0]
                    val border = pixelScores[1]
                    val content = pixelScores[2]

                    rawMatrix[i][j] = if (content > bg && content > border) 1 else 0
                }
            }

            // Step 2: 4-connected Hoshen-Kopelman component labeling
            val maxLabels = (modelInputImageSize * modelInputImageSize) / 2
            val parent = IntArray(maxLabels) { it }

            fun ufFind(x: Int): Int {
                var root = x
                while (parent[root] != root) root = parent[root]
                var curr = x
                while (curr != root) {
                    val nxt = parent[curr]
                    parent[curr] = root
                    curr = nxt
                }
                return root
            }

            fun ufUnion(x: Int, y: Int): Int {
                val rx = ufFind(x)
                val ry = ufFind(y)
                if (rx != ry) parent[rx] = ry
                return ry
            }

            var nextLabel = 1
            val labeled = Array(modelInputImageSize) { IntArray(modelInputImageSize) }

            for (j in 0 until modelInputImageSize) {
                for (i in 0 until modelInputImageSize) {
                    if (rawMatrix[i][j] != 0) {
                        val up = if (i == 0) 0 else labeled[i - 1][j]
                        val left = if (j == 0) 0 else labeled[i][j - 1]

                        when ((if (up != 0) 1 else 0) + (if (left != 0) 1 else 0)) {
                            0 -> {
                                val l = nextLabel++
                                parent[l] = l
                                labeled[i][j] = l
                            }
                            1 -> {
                                labeled[i][j] = if (up != 0) up else left
                            }
                            2 -> {
                                labeled[i][j] = ufUnion(up, left)
                            }
                        }
                    }
                }
            }

            // Count pixels per canonical label
            val labelCounts = mutableMapOf<Int, Int>()
            for (i in 0 until modelInputImageSize) {
                for (j in 0 until modelInputImageSize) {
                    val l = labeled[i][j]
                    if (l != 0) {
                        val canonical = ufFind(l)
                        labelCounts[canonical] = (labelCounts[canonical] ?: 0) + 1
                    }
                }
            }

            // Step 3: Area filter threshold
            val minPixels = (modelInputImageSize * modelInputImageSize * MIN_PANEL_AREA_RATIO).toInt()
            val validCanonicalLabels = labelCounts.filter { it.value >= minPixels }.keys

            // Relabel to sequential 1..N
            val canonicalToNew = mutableMapOf<Int, Int>()
            var currentNormLabel = 0
            val minXMap = mutableMapOf<Int, Int>()
            val maxXMap = mutableMapOf<Int, Int>()
            val minYMap = mutableMapOf<Int, Int>()
            val maxYMap = mutableMapOf<Int, Int>()
            val sumXMap = mutableMapOf<Int, Float>()
            val sumYMap = mutableMapOf<Int, Float>()
            val countMap = mutableMapOf<Int, Int>()

            val finalLabels = ShortArray(modelInputImageSize * modelInputImageSize)

            for (j in 0 until modelInputImageSize) {
                for (i in 0 until modelInputImageSize) {
                    val rawL = labeled[i][j]
                    if (rawL != 0) {
                        val canonical = ufFind(rawL)
                        if (canonical in validCanonicalLabels) {
                            if (!canonicalToNew.containsKey(canonical)) {
                                currentNormLabel++
                                canonicalToNew[canonical] = currentNormLabel
                            }
                            val newL = canonicalToNew[canonical]!!
                            finalLabels[j * modelInputImageSize + i] = newL.toShort()

                            minXMap[newL] = min(minXMap[newL] ?: Int.MAX_VALUE, i)
                            maxXMap[newL] = max(maxXMap[newL] ?: Int.MIN_VALUE, i)
                            minYMap[newL] = min(minYMap[newL] ?: Int.MAX_VALUE, j)
                            maxYMap[newL] = max(maxYMap[newL] ?: Int.MIN_VALUE, j)

                            sumXMap[newL] = (sumXMap[newL] ?: 0f) + i.toFloat()
                            sumYMap[newL] = (sumYMap[newL] ?: 0f) + j.toFloat()
                            countMap[newL] = (countMap[newL] ?: 0) + 1
                        }
                    }
                }
            }

            // Step 4: Build PanelInfo list
            val panelInfos = mutableListOf<PanelInfo>()
            for (l in 1..currentNormLabel) {
                val minGx = minXMap[l] ?: 0
                val maxGx = maxXMap[l] ?: 0
                val minGy = minYMap[l] ?: 0
                val maxGy = maxYMap[l] ?: 0

                val (normLeft, normTop) = letterbox.letterboxToNorm(minGx.toFloat(), minGy.toFloat())
                val (normRight, normBottom) = letterbox.letterboxToNorm((maxGx + 1).toFloat(), (maxGy + 1).toFloat())

                val count = countMap[l] ?: 1
                val avgGx = (sumXMap[l] ?: 0f) / count
                val avgGy = (sumYMap[l] ?: 0f) / count
                val (cx, cy) = letterbox.letterboxToNorm(avgGx, avgGy)

                val boxArea = (normRight - normLeft) * (normBottom - normTop)

                panelInfos.add(
                    PanelInfo(
                        label = l,
                        box = NormRect(normLeft, normTop, normRight, normBottom),
                        cx = cx,
                        cy = cy,
                        area = boxArea
                    )
                )
            }

            Log.i(TAG, "Detected ${panelInfos.size} panels for ${origW}x${origH} page")
            PanelMap(
                gridW = modelInputImageSize,
                gridH = modelInputImageSize,
                labels = finalLabels,
                letterbox = letterbox,
                panels = panelInfos
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error running DeepPanel detector", e)
            null
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
