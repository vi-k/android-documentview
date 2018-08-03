package ru.vik.documentview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet

import ru.vik.utils.color.Color
import ru.vik.utils.document.*

class DebugDocumentView(context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int
) : DocumentView(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    private val debugPaint = TextPaint()

    init {
        this.debugPaint.isAntiAlias = true
        this.debugPaint.color = Color.rgb(255, 0, 0)
        this.debugPaint.isFakeBoldText = true
        this.debugPaint.textSize = 8f * this.deviceMetrics.scaledDensity
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    private fun drawTimeElapsed(canvas: Canvas, timeMillisElapsed: Long, x: Float, y: Float) {
        drawText(canvas, String.format("%.3f", timeMillisElapsed / 1000f), x, y, this.debugPaint)
    }

//    override fun drawSection(canvas: Canvas?,
//        section: Section,
//        parentParagraphStyle: ParagraphStyle,
//        parentCharacterStyle: CharacterStyle,
//        parentLocalMetrics: Size.LocalMetrics,
//        clipTop: Float,
//        clipLeft: Float,
//        clipRight: Float
//    ): Float {
//
//        val t = System.currentTimeMillis()
//
//        val bottom = super.drawSection(canvas, section, parentParagraphStyle,
//                parentCharacterStyle, parentLocalMetrics, clipTop, clipLeft, clipRight)
//
//        val data = section.data as SectionDrawingData
//
//        if (canvas != null) {
//            data.characterStyle.copyFrom(parentCharacterStyle)
//                    .attach(section.characterStyle, this.deviceMetrics, parentLocalMetrics)
//
//            // Метрики, необходимые для рассчёта размеров (если они указаны в em, ratio и fh).
//            // Размеры уже рассчитаны с учётом density и scaledDensity
//            applyCharacterStyle(data.characterStyle, data.localMetrics)
//            data.localMetrics.parentSize = clipRight - clipLeft
//
//            drawTimeElapsed(canvas, System.currentTimeMillis() - t,
//                    clipLeft + Size.toPixels(section.borderStyle.marginLeft, this.deviceMetrics,
//                            data.localMetrics) + Size.toPixels(section.borderStyle.borderLeft,
//                            this.deviceMetrics, data.localMetrics, useParentSize = false),
//                    bottom - Size.toPixels(section.borderStyle.marginBottom, this.deviceMetrics,
//                            data.localMetrics) - Size.toPixels(section.borderStyle.borderBottom,
//                            this.deviceMetrics, data.localMetrics, useParentSize = false))
//        }
//
//        return bottom
//    }

//    // Отрисовка абзаца
//    override fun drawParagraph(canvas: Canvas?,
//        paragraph: Paragraph,
//        parentParagraphStyle: ParagraphStyle,
//        parentCharacterStyle: CharacterStyle,
//        parentLocalMetrics: Size.LocalMetrics,
//        clipTop: Float,
//        clipLeft: Float,
//        clipRight: Float
//    ): Float {
//
//        val t = System.currentTimeMillis()
//
//        val bottom = super.drawParagraph(canvas, paragraph, parentParagraphStyle,
//                parentCharacterStyle, parentLocalMetrics, clipTop, clipLeft, clipRight)
//
//        val data = paragraph.data as ParagraphDrawingData
//
//        if (canvas != null) {
//            data.characterStyle
//                    .copyFrom(parentCharacterStyle)
//                    .attach(paragraph.characterStyle, this.deviceMetrics, parentLocalMetrics)
//
//            // Метрики, необходимые для рассчёта размеров (если они указаны в em, ratio и fh).
//            // Размеры уже рассчитаны с учётом density и scaledDensity
//            applyCharacterStyle(data.characterStyle, data.localMetrics)
//            data.localMetrics.parentSize = clipRight - clipLeft
//
//            drawTimeElapsed(canvas, System.currentTimeMillis() - t,
//                    clipLeft + Size.toPixels(paragraph.borderStyle.marginLeft, this.deviceMetrics,
//                            data.localMetrics) + Size.toPixels(
//                            paragraph.borderStyle.borderLeft, this.deviceMetrics,
//                            data.localMetrics, useParentSize = false),
//                    bottom - Size.toPixels(paragraph.borderStyle.marginBottom, this.deviceMetrics,
//                            data.localMetrics) - Size.toPixels(
//                            paragraph.borderStyle.borderBottom, this.deviceMetrics,
//                            data.localMetrics, useParentSize = false))
//        }
//
//        return bottom
//    }
}
