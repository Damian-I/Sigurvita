package com.zemmy.sigurvita.core.ml

import android.util.Base64
import com.zemmy.sigurvita.core.model.BubbleMask
import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.util.PanelInfo
import com.zemmy.sigurvita.core.util.PanelMap
import kotlinx.serialization.Serializable

const val CURRENT_DETECTION_VERSION = "yolov8m_seg_1024_tails_v2"

@Serializable
data class PageDetectionsCache(
    val chapterId: Int,
    val modelVersion: String = CURRENT_DETECTION_VERSION,
    val confidence: Float = 0.10f,
    val pages: List<PageDetectionData>
)

@Serializable
data class PageDetectionData(
    val pageIndex: Int,
    val pageWidth: Int,
    val pageHeight: Int,
    val letterbox: LetterboxParamsData,
    val panels: List<PanelInfoData> = emptyList(),
    val panelGridW: Int = 224,
    val panelGridH: Int = 224,
    val panelLabelsBase64: String = "",
    val bubbles: List<DetectedBubbleData> = emptyList()
)

@Serializable
data class LetterboxParamsData(
    val inputSize: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val gain: Float,
    val padX: Float,
    val padY: Float
)

@Serializable
data class PanelInfoData(
    val label: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cx: Float,
    val cy: Float,
    val area: Float
)

@Serializable
data class DetectedBubbleData(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val classId: Int,
    val maskSize: Int,
    val maskProbsBase64: String
)

object DetectionsCacheHelper {
    fun toPageBubbles(data: PageDetectionData, pageIndex: Int): PageBubbles {
        val lb = LetterboxParams(
            inputSize = data.letterbox.inputSize,
            originalWidth = data.letterbox.originalWidth,
            originalHeight = data.letterbox.originalHeight,
            gain = data.letterbox.gain,
            padX = data.letterbox.padX,
            padY = data.letterbox.padY
        )

        val bubbles = data.bubbles.map { bData ->
            val probsBytes = if (bData.maskProbsBase64.isNotBlank()) {
                Base64.decode(bData.maskProbsBase64, Base64.DEFAULT)
            } else ByteArray(0)

            DetectedBubble(
                id = bData.id,
                box = NormRect(bData.left, bData.top, bData.right, bData.bottom),
                score = bData.score,
                classId = bData.classId,
                mask = BubbleMask(bData.maskSize, probsBytes)
            )
        }

        return PageBubbles(
            pageIndex = pageIndex,
            pageWidth = data.pageWidth,
            pageHeight = data.pageHeight,
            letterbox = lb,
            bubbles = bubbles
        )
    }

    fun toPanelMap(data: PageDetectionData): PanelMap {

        val lb = LetterboxParams(
            inputSize = data.letterbox.inputSize,
            originalWidth = data.letterbox.originalWidth,
            originalHeight = data.letterbox.originalHeight,
            gain = data.letterbox.gain,
            padX = data.letterbox.padX,
            padY = data.letterbox.padY
        )

        val panels = data.panels.map { pData ->
            PanelInfo(
                label = pData.label,
                box = NormRect(pData.left, pData.top, pData.right, pData.bottom),
                cx = pData.cx,
                cy = pData.cy,
                area = pData.area
            )
        }

        val labelsShort = if (data.panelLabelsBase64.isNotBlank()) {
            val bytes = Base64.decode(data.panelLabelsBase64, Base64.DEFAULT)
            val shorts = ShortArray(bytes.size / 2)
            for (i in shorts.indices) {
                shorts[i] = ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
            }
            shorts
        } else ShortArray(data.panelGridW * data.panelGridH)

        return PanelMap(
            gridW = data.panelGridW,
            gridH = data.panelGridH,
            labels = labelsShort,
            letterbox = lb,
            panels = panels
        )
    }

    fun fromPageData(
        pageIndex: Int,
        pageBubbles: PageBubbles?,
        panelMap: PanelMap?
    ): PageDetectionData {
        val lb = pageBubbles?.letterbox ?: panelMap?.letterbox ?: LetterboxParams(640, 1000, 1000, 1f, 0f, 0f)
        val lbData = LetterboxParamsData(
            inputSize = lb.inputSize,
            originalWidth = lb.originalWidth,
            originalHeight = lb.originalHeight,
            gain = lb.gain,
            padX = lb.padX,
            padY = lb.padY
        )

        val bubbleDataList = pageBubbles?.bubbles?.map { b ->
            val b64Mask = if (b.mask.probs.isNotEmpty()) {
                Base64.encodeToString(b.mask.probs, Base64.NO_WRAP)
            } else ""

            DetectedBubbleData(
                id = b.id,
                left = b.box.left,
                top = b.box.top,
                right = b.box.right,
                bottom = b.box.bottom,
                score = b.score,
                classId = b.classId,
                maskSize = b.mask.size,
                maskProbsBase64 = b64Mask
            )
        } ?: emptyList()

        val panelDataList = panelMap?.panels?.map { p ->
            PanelInfoData(
                label = p.label,
                left = p.box.left,
                top = p.box.top,
                right = p.box.right,
                bottom = p.box.bottom,
                cx = p.cx,
                cy = p.cy,
                area = p.area
            )
        } ?: emptyList()

        val labelsB64 = if (panelMap != null && panelMap.labels.isNotEmpty()) {
            val bytes = ByteArray(panelMap.labels.size * 2)
            for (i in panelMap.labels.indices) {
                val s = panelMap.labels[i].toInt()
                bytes[i * 2] = (s and 0xFF).toByte()
                bytes[i * 2 + 1] = (s shr 8 and 0xFF).toByte()
            }
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } else ""

        return PageDetectionData(
            pageIndex = pageIndex,
            pageWidth = pageBubbles?.pageWidth ?: 1000,
            pageHeight = pageBubbles?.pageHeight ?: 1000,
            letterbox = lbData,
            panels = panelDataList,
            panelGridW = panelMap?.gridW ?: 224,
            panelGridH = panelMap?.gridH ?: 224,
            panelLabelsBase64 = labelsB64,
            bubbles = bubbleDataList
        )
    }
}
