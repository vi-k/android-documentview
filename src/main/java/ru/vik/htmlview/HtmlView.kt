package ru.vik.htmlview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

import ru.vik.html2text.Html2Text
import ru.vik.parser.StringParser

class HtmlView(context: Context,
               attrs: AttributeSet?,
               defStyleAttr: Int)
    : View(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    var html2Text: Html2Text? = null
    //    Html2Text(BlockStyle(), ParagraphStyle(), CharacterStyle())
    var fontList: FontList? = null
    val baseFont = Font(Typeface.SERIF)

    class Word(val spaces: Int,
               val start: Int,
               val end: Int,
               var spacesWidth: Float = 0.0f,
               var textWidth: Float = 0.0f)

    private val textPaint = TextPaint()
    private var words = mutableListOf<Word>()
    var text: String = ""
        set(value) {
            field = value

            val parser = StringParser(value)

            this.words.clear()

            while (!parser.eof()) {
                // Отделяем пробелы
                parser.start()
                var spaces = 0
                while (!parser.eof() && parser.get() == ' ') {
                    spaces++
                    parser.next()
                }

                // Ищем слово
                parser.start()
                while (!parser.eof() && parser.get() != ' ') {
                    parser.next()
                }

                this.words.add(Word(spaces, parser.start, parser.pos))
            }
        }

    init {
        this.text =
                "удщрфц удщрфц удщрфц удщрфц удщрфц удщрфц удщрфц " +
                "ЙА\u0301ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ " +
                "удщрфц удщрфц удщрфц удщрфц удщрфц удщрфц удщрфц " +
                "ЙА\u0301ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ " +
                "удщрфц удщрфц удщрфц удщрфц удщрфц удщрфц удщрфц " +
                "ЙА\u0301ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ ЙА́ДУ "
//                "Бл҃го\u00ADсло\u00ADвѝ дꙋ\u00ADшѐ моѧ̀ гдⷭ҇а: гдⷭ҇и бж҃е мо́й, воз\u00ADве\u00ADли́\u00ADчил\u00ADсѧ є҆сѝ ѕѣ\u00ADлѡ̀, " +
//                "во и҆с\u00ADпо\u00ADвѣ\u00AD́да\u00ADнїе и҆ въ ве\u00ADле\u00ADлѣ\u00AD́по\u00ADтꙋ ѡ҆б\u00ADле́кл\u00ADсѧ є҆сѝ. Ѡ҆дѣ\u00ADѧ́й\u00ADсѧ свѣ\u00AD́томъ " +
//                "ꙗ҆́кѡ ри́\u00ADзою, прос\u00ADти\u00ADра́\u00ADѧй нб҃о ꙗ҆́кѡ ко́\u00ADжꙋ. Пок\u00ADры\u00ADва́\u00ADѧй во\u00ADда́\u00ADми пре\u00ADвы́с\u00ADпрєн\u00ADнѧѧ " +
//                "своѧ̑, по\u00ADла\u00ADга́\u00ADѧй ѻ҆́б\u00ADла\u00ADки " +
//                "на вос\u00ADхож\u00ADде́\u00ADнїе своѐ, хо\u00ADдѧ́й на кри\u00ADлꙋ̀ вѣ́т\u00ADрє\u00ADню."
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()

        val font: Font = fontList?.get("ponomar~") ?: baseFont

        this.textPaint.typeface = font.typeface
        this.textPaint.isAntiAlias = true
        this.textPaint.textSize = 17.0f * font.scale *
                this.context.resources.displayMetrics.scaledDensity
        this.textPaint.textScaleX = 0.85f

        val fontMetrics = font.correctFontMetrics(this.textPaint.fontMetrics)
        var baseline = -fontMetrics.top
        val rowHeight = fontMetrics.descent - fontMetrics.ascent + fontMetrics.leading

        var start = 0
        var pos = 0
        val end = this.words.size
        var rest = canvas.width.toFloat()
        var commonSpacesWidth = 0
        var first = true

        while (pos < end) {
            val word = this.words[pos++]

            word.spacesWidth =
                    if (first) 0.0f
                    else textPaint.measureText(this.text, word.start - word.spaces, word.start)
            word.textWidth = textPaint.measureText(this.text, word.start, word.end)

            first = false

            if (rest - word.spacesWidth - word.textWidth < 0.0f || pos >= end) {
                if (pos < end) pos--

                var x = 0.0f
                var drawnFirst = true
                while (start < pos) {
                    val drawnWord = this.words[start++]
                    if (drawnFirst) {
                        drawText(canvas, text,
                                drawnWord.start, drawnWord.end,
                                x, baseline, textPaint, true)
                        drawnFirst = false
                    } else {
                        drawText(canvas, text,
                                drawnWord.start - drawnWord.spaces, drawnWord.end,
                                x, baseline, textPaint, true)
                    }
                    x += drawnWord.spacesWidth + drawnWord.textWidth
                }

                baseline += rowHeight
                rest = canvas.width.toFloat()
                first = true
            } else {
                rest -= word.spacesWidth + word.textWidth
            }
        }

//        textPaint.textAlign = Paint.Align.LEFT
//        drawText(canvas, text, 0.0f, baseline, textPaint, true)

//        textPaint.textAlign = Paint.Align.CENTER
//        drawText(canvas, text, 0.0f, baseline + height, textPaint, true)

//        textPaint.textAlign = Paint.Align.RIGHT
//        textPaint.baselineShift += 40
//        drawText(canvas, text, canvas.width.toFloat(), baseline + 2 * height, textPaint, true)

        canvas.restore()
    }

    fun drawText(canvas: Canvas,
                 text: String,
                 x: Float,
                 y: Float,
                 paint: Paint,
                 drawBaseline: Boolean = false) {

        drawText(canvas, text, 0, text.length, x, y, paint, drawBaseline)
    }

    fun drawText(canvas: Canvas,
                 text: String,
                 start: Int,
                 end: Int,
                 x: Float,
                 y: Float,
                 paint: Paint,
                 drawBaseline: Boolean = false) {

        if (!drawBaseline) {
            canvas.drawText(text, start, end, x, y, paint)
        } else {
            val width = paint.measureText(text, start, end)
            val left = when (paint.textAlign) {
                Paint.Align.CENTER -> x - width / 2
                Paint.Align.RIGHT  -> x - width
                else               -> x
            }

            val right = left + width

            val textColor = paint.color
            paint.color = Color.RED
            canvas.drawLine(left, y, right, y, paint)
            paint.color = textColor

            canvas.drawText(text, start, end, x, y, paint)
        }
    }
}