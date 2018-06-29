package ru.vik.documentview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet

import ru.vik.utils.color.Color
import ru.vik.utils.document.BlockStyle
import ru.vik.utils.document.Border
import ru.vik.utils.document.CharacterStyle
import ru.vik.utils.document.Paragraph
import ru.vik.utils.document.ParagraphStyle
import ru.vik.utils.document.Section

class DebugDocumentView(context: Context,
                        attrs: AttributeSet?,
                        defStyleAttr: Int)
    : DocumentView(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    private val debugPaint = TextPaint()

    var bigPoint = false

    init {
        this.debugPaint.isAntiAlias = true
        this.debugPaint.color = Color.rgb(255, 0, 0)
        this.debugPaint.isFakeBoldText = true
        this.debugPaint.textSize = 10f * this.scaledDensity
    }

    override fun drawPoint(canvas: Canvas, x: Float, y: Float, color: Int) {
        if (!this.bigPoint) {
            super.drawPoint(canvas, x, y, color)
            return
        }

        val SZ = 30f
        this.paint.style = Paint.Style.FILL
        this.paint.color = color
        canvas.drawRect(x * SZ, y * SZ,
                (x + 1f) * SZ, (y + 1f) * SZ,
                this.paint)
    }

    override fun drawLine(canvas: Canvas,
                          x1: Float,
                          y1: Float,
                          x2: Float,
                          y2: Float,
                          color: Int) {
        if (!this.bigPoint) {
            super.drawLine(canvas, x1, y1, x2, y2, color)
            return
        }

        if (y1 == y2) {
            var x = x1
            while (x < x2) {
                drawPoint(canvas, x, y1, color)
                x += 1f
            }
        } else if (x1 == x2) {
            var y = y1
            while (y < y2) {
                drawPoint(canvas, x1, y, color)
                y += 1f
            }
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        this.bigPoint = true

        val bs = BlockStyle.default()
//        bs.setBorder(Border(2.5f, Color.argb(255, 255, 255, 255)),
//                Border(3.5f, Color.argb(0, 0, 0, 0)))
//        bs.setBorder(Border(2.5f, Color.argb(255, 255, 128, 0)),
//                Border(3.5f, Color.argb(255, 0, 255, 128)))
        bs.setBorder(Border(2.5f, Color.argb(255, 255, 0, 0)),
                Border(3.5f, Color.argb(255, 0, 0, 255)))

        val leftBorder = bs.border.left
        drawBorder(canvas, bs, 6f, 8f, 11f, 15f)
        drawBorder(canvas, bs, 24.5f, 8.5f, 29.5f, 15.5f)
        bs.border.left = null
        drawBorder(canvas, bs, 42.5f, 8.5f, 47.5f, 15.5f)
        bs.border.right = null
        drawBorder(canvas, bs, 60.5f, 8.5f, 65.5f, 15.5f)
        bs.setBorder(null, leftBorder)
        drawBorder(canvas, bs, 78.5f, 8.5f, 83.5f, 15.5f)

        this.bigPoint = false
    }

    private fun drawTimeElapsed(canvas: Canvas, timeMillisElapsed: Long, x: Float, y: Float) {
        drawText(canvas, String.format("%.3f", timeMillisElapsed / 1000f),
                x, y, false, this.debugPaint)
    }

    override fun drawSection(canvas: Canvas?,
                             section: Section,
                             parentPs: ParagraphStyle,
                             parentCs: CharacterStyle,
                             clipTop: Float,
                             clipLeft: Float,
                             clipRight: Float): Float {

        val t = System.currentTimeMillis()

        val bs = section.bs
        val bottom = super.drawSection(canvas, section, parentPs, parentCs, clipTop, clipLeft,
                clipRight)

        if (canvas != null) {
            drawTimeElapsed(canvas, System.currentTimeMillis() - t,
                    clipLeft + (bs.margin.left + bs.borderLeftWidth) * this.density,
                    bottom)
        }

        return bottom
    }

    // Отрисовка абзаца
    override fun drawParagraph(canvas: Canvas?,
                               paragraph: Paragraph,
                               parentPs: ParagraphStyle,
                               parentCs: CharacterStyle,
                               clipTop: Float,
                               clipLeft: Float,
                               clipRight: Float): Float {

        val t = System.currentTimeMillis()

        val bs = paragraph.bs
        val bottom =
                super.drawParagraph(canvas, paragraph, parentPs, parentCs, clipTop, clipLeft,
                        clipRight)

        if (canvas != null) {
            drawTimeElapsed(canvas, System.currentTimeMillis() - t,
                    clipLeft + (bs.margin.left + bs.borderLeftWidth) * this.density,
                    bottom)
        }

        return bottom
    }
}