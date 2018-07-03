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
        this.debugPaint.textSize = 8f * this.scaledDensity
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

        val blockStyle = BlockStyle.default()
//        blockStyle.setBorder(Border(2.5f, Color.argb(255, 255, 255, 255)),
//                Border(3.5f, Color.argb(0, 0, 0, 0)))
//        blockStyle.setBorder(Border(2.5f, Color.argb(255, 255, 128, 0)),
//                Border(3.5f, Color.argb(255, 0, 255, 128)))
        blockStyle.setBorder(Border.dp(2.5f, Color.argb(255, 255, 0, 0)),
                Border.dp(3.5f, Color.argb(255, 0, 0, 255)))

        val leftBorder = blockStyle.borderLeft
        drawBorder(canvas, blockStyle, 6f, 8f, 11f, 15f, 0f, 0f)
        drawBorder(canvas, blockStyle, 24.5f, 8.5f, 29.5f, 15.5f, 0f, 0f)
        blockStyle.borderLeft = null
        drawBorder(canvas, blockStyle, 42.5f, 8.5f, 47.5f, 15.5f, 0f, 0f)
        blockStyle.borderRight = null
        drawBorder(canvas, blockStyle, 60.5f, 8.5f, 65.5f, 15.5f, 0f, 0f)
        blockStyle.setBorder(null, leftBorder)
        drawBorder(canvas, blockStyle, 78.5f, 8.5f, 83.5f, 15.5f, 0f, 0f)

        this.bigPoint = false
    }

    private fun drawTimeElapsed(canvas: Canvas, timeMillisElapsed: Long, x: Float, y: Float) {
        drawText(canvas, String.format("%.3f", timeMillisElapsed / 1000f), x, y,
                false, this.debugPaint)
    }

    override fun drawSection(canvas: Canvas?,
                             section: Section,
                             parentParagraphStyle: ParagraphStyle,
                             parentCharacterStyle: CharacterStyle,
                             clipTop: Float,
                             clipLeft: Float,
                             clipRight: Float): Float {

        val t = System.currentTimeMillis()

        val characterStyle =
                parentCharacterStyle.clone().attach(section.characterStyle)
        val blockStyle = section.blockStyle

        val bottom = super.drawSection(canvas, section, parentParagraphStyle,
                parentCharacterStyle, clipTop, clipLeft, clipRight)

        if (canvas != null) {
            // Размер шрифта и ширина родителя - параметры, необходимые для рассчёта размеров
            // (если они указаны в em и %). Размеры рассчитаны уже с учётом density и scaledDensity
            val (sectionFont, _) = getFont(characterStyle)
            val fontSize = getFontSize(characterStyle, sectionFont.scale)
            val parentWidth = clipRight - clipLeft

            drawTimeElapsed(canvas, System.currentTimeMillis() - t,
                    clipLeft +
                    Size.toPixels(blockStyle.marginLeft, this.density, fontSize, parentWidth) +
                    Size.toPixels(blockStyle.borderLeft, this.density, fontSize),
                    bottom -
                    Size.toPixels(blockStyle.marginBottom, this.density, fontSize, parentWidth) -
                    Size.toPixels(blockStyle.borderBottom, this.density, fontSize))
        }

        return bottom
    }

    // Отрисовка абзаца
    override fun drawParagraph(canvas: Canvas?,
                               paragraph: Paragraph,
                               parentParagraphStyle: ParagraphStyle,
                               parentCharacterStyle: CharacterStyle,
                               clipTop: Float,
                               clipLeft: Float,
                               clipRight: Float): Float {

        val t = System.currentTimeMillis()

        val characterStyle =
                parentCharacterStyle.clone().attach(paragraph.characterStyle)
        val blockStyle = paragraph.blockStyle

        val bottom = super.drawParagraph(canvas, paragraph, parentParagraphStyle,
                parentCharacterStyle, clipTop, clipLeft, clipRight)

        if (canvas != null) {
            // Размер шрифта и ширина родителя - параметры, необходимые для рассчёта размеров
            // (если они указаны в em и %). Размеры рассчитаны уже с учётом density и scaledDensity
            val (sectionFont, _) = getFont(characterStyle)
            val fontSize = getFontSize(characterStyle, sectionFont.scale)
            val parentWidth = clipRight - clipLeft

            drawTimeElapsed(canvas, System.currentTimeMillis() - t,
                    clipLeft +
                    Size.toPixels(blockStyle.marginLeft, this.density, fontSize, parentWidth) +
                    Size.toPixels(blockStyle.borderLeft, this.density, fontSize, parentWidth),
                    bottom -
                    Size.toPixels(blockStyle.marginBottom, this.density, fontSize, parentWidth) -
                    Size.toPixels(blockStyle.borderBottom, this.density, fontSize))
        }

        return bottom
    }
}