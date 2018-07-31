package ru.vik.documentview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import java.util.logging.Logger

import ru.vik.utils.parser.StringParser
import ru.vik.utils.color.mix
import ru.vik.utils.document.*
import kotlin.math.*

open class DocumentView(context: Context,
    attrs: AttributeSet?, defStyleAttr: Int
) : View(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context) : this(context, null, 0)

    enum class Baseline { NONE, INDENT, PARAGRAPH, VIEW }

    enum class GetFontType { BY_FULL_NAME, BY_SHORT_NAME, DEFAULT }

    class Segment(
        var isFirst: Boolean,
        val spaces: Int,
        var start: Int,
        val end: Int,
        val characterStyle: CharacterStyle,
        val font: Font,
        val ascent: Float,
        val descent: Float,
        val leading: Float?,
        var spacesWidth: Float,
        var textWidth: Float,
        val hyphenWidth: Float,
        val eol: Boolean,
        var baseline: Float = 0f
    )

    private val log = Logger.getLogger("DocumentView")!!

    var document = Document()

//    fun setDocument(document: Document) {
//        this.document = document
//    }

    // Для DSL
//    fun document(init: Document.() -> Unit) {
//        this.document.init()
//    }

    var fontList = FontList()
    var drawEmptyParagraph = false


    val deviceMetrics: Size.DeviceMetrics

    private val paragraphStyle = ParagraphStyle.default()
    private val characterStyle = CharacterStyle.default()
    private val cacheLocalMetrics = Size.LocalMetrics()

    init {
        val displayMetrics = this.context.resources.displayMetrics
        this.deviceMetrics = Size.DeviceMetrics(displayMetrics.density,
                displayMetrics.scaledDensity, displayMetrics.xdpi, displayMetrics.ydpi)
    }

    var baselineMode = Baseline.NONE
    var baselineColor = Color.rgb(255, 0, 0)

    internal val textPaint = TextPaint()
    internal val paint = Paint()
    internal var baseline: Float? = null

    init {
        this.paint.isAntiAlias = true
        this.paint.style = Paint.Style.FILL
    }

    private var segments = mutableListOf<Segment>()

    // Для DSL
    operator fun invoke(init: DocumentView.() -> Unit): DocumentView {
        this.init()
        return this
    }

    /**
     * Функция рисования точки с возможностью её подмены для debug
     */
    open fun drawPoint(canvas: Canvas, x: Float, y: Float, color: Int) {
        this.paint.color = color
        canvas.drawPoint(x, y, this.paint)
    }

    /**
     * Функция рисования линии с возможностью её подмены для debug
     */
    open fun drawLine(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        this.paint.color = color
        canvas.drawLine(x1, y1, x2, y2, this.paint)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawView(canvas)
    }

    /**
     * Рисование View или вычисление необходимой высоты (при canvas == null).
     *
     * @param canvas Если null, то только вычисление высоты.
     * @param width В OnMeasure() ещё не установлена ширина View, поэтому будущую ширину
     * необходимо задать вручную.
     */
    open fun drawView(canvas: Canvas?, width: Int = this.width): Float {
        this.baseline = null
        characterStyle2TextPaint(this.characterStyle, this.cacheLocalMetrics)

        return drawSection(canvas, this.document,
                this.paragraphStyle, this.characterStyle, this.cacheLocalMetrics,
                this.paddingTop.toFloat(), this.paddingLeft.toFloat(),
                (width - this.paddingRight).toFloat())
    }

    /**
     * Рисование секции или вычисление необходимой высоты секции (при canvas == null).
     *
     * @param canvas Если null, то тоько вычисление высоты.
     * @param section Секция, которую необходимо отрисовать.
     * @param parentParagraphStyle Стиль абзаца, переданный от родителя.
     * @param parentCharacterStyle Стиль знаков, переданный от родителя.
     * @param clipTop
     * @param clipLeft
     * @param clipRight Границы секции. Нижняя граница вычисляется.
     */
    open fun drawSection(canvas: Canvas?, section: Section,
        parentParagraphStyle: ParagraphStyle,
        parentCharacterStyle: CharacterStyle,
        parentLocalMetrics: Size.LocalMetrics,
        clipTop: Float, clipLeft: Float, clipRight: Float
    ): Float {

        section.cacheParagraphStyle
                .copy(parentParagraphStyle)
                .attach(section.paragraphStyle)

        section.cacheCharacterStyle
                .copy(parentCharacterStyle)
                .attach(section.characterStyle, this.deviceMetrics, parentLocalMetrics)

        // Метрики, необходимые для рассчёта размеров (если они указаны в em, ratio и fh).
        // Размеры уже рассчитаны с учётом density и scaledDensity
        characterStyle2TextPaint(section.cacheCharacterStyle, section.cacheLocalMetrics)
        section.cacheLocalMetrics.parentSize = clipRight - clipLeft

        // Границы абзаца (нижней границы нет, мы её вычисляем)
        val (sectionTop, sectionLeft, sectionRight) = getInnerBoundary(
                clipTop, clipLeft, clipRight, section.borderStyle,
                this.deviceMetrics, section.cacheLocalMetrics)
        var sectionBottom = sectionTop

        if (this.baseline == null && section.firstBaselineToTop) {
            this.baseline = round(sectionTop)
        }

        // Во время расчётов базовая линия будет смещаться. Важно её здесь сохранить, чтобы потом
        // восстановить перед отрисовкой
        val savedBaseline = this.baseline

        if (canvas == null || section.borderStyle.needForDraw()) {
            // Вычисление размеров, если это необходимо

            for (item in section.items) {
                when (item) {
                    is Section -> sectionBottom = drawSection(null, item,
                            section.cacheParagraphStyle, section.cacheCharacterStyle,
                            section.cacheLocalMetrics,
                            sectionBottom, sectionLeft, sectionRight)
                    is Paragraph -> sectionBottom = drawParagraph(null, item,
                            section.cacheParagraphStyle, section.cacheCharacterStyle,
                            section.cacheLocalMetrics,
                            sectionBottom, sectionLeft, sectionRight)
                }
            }

            if (canvas != null) {
                drawBorder(canvas, section.borderStyle, sectionTop, sectionLeft, sectionBottom,
                        sectionRight, section.cacheLocalMetrics)
            }
        }

        this.baseline = savedBaseline

        if (canvas != null) {
            // Отрисовка

            sectionBottom = sectionTop

            for (item in section.items) {
                when (item) {
                    is Section -> sectionBottom = drawSection(canvas, item,
                            section.cacheParagraphStyle, section.cacheCharacterStyle,
                            section.cacheLocalMetrics,
                            sectionBottom, sectionLeft, sectionRight)
                    is Paragraph -> sectionBottom = drawParagraph(canvas, item,
                            section.cacheParagraphStyle, section.cacheCharacterStyle,
                            section.cacheLocalMetrics,
                            sectionBottom, sectionLeft, sectionRight)
                }
            }
        }

        return sectionBottom +
                Size.toPixels(section.borderStyle.paddingBottom,
                        this.deviceMetrics, section.cacheLocalMetrics) +
                Size.toPixels(section.borderStyle.borderBottom,
                        this.deviceMetrics, section.cacheLocalMetrics, useParentSize = false) +
                Size.toPixels(section.borderStyle.marginBottom,
                        this.deviceMetrics, section.cacheLocalMetrics)
    }

    /**
     * Рисование абзаца или вычисление необходимой высоты абзаца (при canvas == null).
     *
     * @param canvas Если null, то только вычисление высоты.
     * @param paragraph Абзац, который необходимо отрисовать.
     * @param parentParagraphStyle Стиль абзаца, переданный от родителя.
     * @param parentCharacterStyle Стиль знаков, переданный от родителя.
     * @param clipTop
     * @param clipLeft
     * @param clipRight Границы секции. Нижняя граница вычисляется.
     */
    open fun drawParagraph(canvas: Canvas?, paragraph: Paragraph,
        parentParagraphStyle: ParagraphStyle,
        parentCharacterStyle: CharacterStyle,
        parentLocalMetrics: Size.LocalMetrics,
        clipTop: Float, clipLeft: Float, clipRight: Float
    ): Float {

        paragraph.cacheParagraphStyle
                .copy(parentParagraphStyle)
                .attach(paragraph.paragraphStyle)

        paragraph.cacheCharacterStyle
                .copy(parentCharacterStyle)
                .attach(paragraph.characterStyle, this.deviceMetrics, parentLocalMetrics)

        // Метрики, необходимые для рассчёта размеров (если они указаны в em, ratio и fh).
        // Размеры уже рассчитаны с учётом density и scaledDensity
        characterStyle2TextPaint(paragraph.cacheCharacterStyle, paragraph.cacheLocalMetrics)
        paragraph.cacheLocalMetrics.parentSize = clipRight - clipLeft

        // Границы абзаца (нижней границы нет, мы её вычисляем)
        val (paragraphTop, paragraphLeft, paragraphRight) =
                getInnerBoundary(clipTop, clipLeft, clipRight, paragraph.borderStyle,
                        this.deviceMetrics, paragraph.cacheLocalMetrics)
        var paragraphBottom = paragraphTop + Size.toPixels(
                paragraph.cacheParagraphStyle.spaceBefore,
                this.deviceMetrics, paragraph.cacheLocalMetrics)

        val baselineLeft by lazy {
            if (this.baselineMode == Baseline.PARAGRAPH) {
                paragraphLeft - Size.toPixels(paragraph.borderStyle.paddingLeft,
                        this.deviceMetrics, paragraph.cacheLocalMetrics, horizontal = true)
            } else 0f
        }

        val baselineRight by lazy {
            if (this.baselineMode == Baseline.PARAGRAPH) {
                paragraphRight + Size.toPixels(paragraph.borderStyle.paddingRight,
                        this.deviceMetrics, paragraph.cacheLocalMetrics, horizontal = true)
            } else this.width.toFloat()
        }

        if (paragraph.text.isNotEmpty() || this.drawEmptyParagraph) {
            // Вычисляем размеры абзаца, разбиваем абзац на строки

            val parser = StringParser(paragraph.text)

            val leftIndent = Size.toPixels(
                    paragraph.cacheParagraphStyle.leftIndent, this.deviceMetrics,
                    paragraph.cacheLocalMetrics, horizontal = true)
            val rightIndent = Size.toPixels(
                    paragraph.cacheParagraphStyle.rightIndent, this.deviceMetrics,
                    paragraph.cacheLocalMetrics, horizontal = true)
            val firstLeftIndent = Size.toPixels(
                    paragraph.cacheParagraphStyle.firstLeftIndent, this.deviceMetrics,
                    paragraph.cacheLocalMetrics, horizontal = true)
            val firstRightIndent = Size.toPixels(
                    paragraph.cacheParagraphStyle.firstRightIndent, this.deviceMetrics,
                    paragraph.cacheLocalMetrics, horizontal = true)

            val paragraphWidth = paragraphRight - paragraphLeft
            val lineWidth = paragraphWidth - leftIndent - rightIndent

//            this.log.warning("parentWidth=${paragraph.cacheLocalMetrics.parentSize} " +
//                    "paragraphWidth=$paragraphWidth " +
//                    "lineWidth=$lineWidth " +
//                    "leftIndent=$leftIndent " +
//                    "rightIndent=$rightIndent " +
//                    "firstLeftIndent=$firstLeftIndent " +
//                    "firstRightIndent=$firstRightIndent")

            var width = 0f
            var isFirst = true
            var first = 0
            var isFirstLine = true

            this.segments.clear()

            // Парсим строку абзаца
            while (!parser.eof()) {
                // Находим в тексте очередной сегмент с одним стилем
                val segmentCharacterStyle = paragraph.cacheCharacterStyle.clone()
                paragraph.cacheSegmentLocalMetrics.copy(paragraph.cacheLocalMetrics)

                val segmentFont = parseNextSegment(parser, paragraph,
                        segmentCharacterStyle, paragraph.cacheSegmentLocalMetrics)

                // Разбиваем этот сегмент на более мелкие сегменты по пробелам и знакам переноса

                val inParser = StringParser(paragraph.text, parser.start, parser.pos)

                while (!inParser.eof()) {
                    // Отделяем пробелы
                    inParser.start()
                    var spaces = 0
                    while (!inParser.eof() && inParser.get() == ' ') {
                        spaces++
                        inParser.next()
                    }

                    // Ищем сегмент без пробелов (прерываемся заодно на переносах строк)
                    inParser.start()

                    var withHyphen = false
                    var withEol = false
                    var segmentEnd = inParser.pos

                    loop@ while (!inParser.eof()) {
                        val char = inParser.get()
                        when (char) {
                            ' ' -> break@loop
                            '\u00AD' -> {
                                inParser.next()
                                withHyphen = true
                                break@loop
                            }
                            '\r', '\n', '\u0085', '\u2028', '\u2029' -> {
                                inParser.next()
                                withEol = true
                                if (char == '\r' && !inParser.eof() && inParser.get() == '\n') {
                                    inParser.next()
                                }
                                break@loop
                            }
                        }

                        inParser.next()
                        segmentEnd = inParser.pos
                    }

                    // Смещение уже приводится в attach() (parseNextSegment()) к PX
                    val leading = segmentCharacterStyle.leading
                            ?.takeIf { it.isNotAuto() }
                            ?.toPixels(
                                    this.deviceMetrics, paragraph.cacheSegmentLocalMetrics,
                                    useParentSize = false)

                    val ascent = paragraph.cacheSegmentLocalMetrics.fontAscent -
                                segmentCharacterStyle.baselineShift.size
                    val descent = paragraph.cacheSegmentLocalMetrics.fontDescent +
                                segmentCharacterStyle.baselineShift.size

                    val segment = Segment(
                            isFirst = isFirst,
                            spaces = spaces,
                            start = inParser.start,
                            end = segmentEnd,
                            characterStyle = segmentCharacterStyle,
                            font = segmentFont,
                            ascent = ascent,
                            descent = descent,
                            leading = leading,
                            spacesWidth = if (!isFirst) {
                                this.textPaint.measureText(paragraph.text,
                                        inParser.start - spaces, inParser.start)
                            } else 0f,
                            textWidth = this.textPaint.measureText(paragraph.text,
                                    inParser.start, segmentEnd),
                            hyphenWidth = if (withHyphen) {
                                this.textPaint.measureText("-")
                            } else 0f,
                            eol = withEol
                    )

//                    this.log.warning("\"${paragraph.text.toString().substring(segment.start,
//                            segment.end)}\" " +
//                            "ascent=$ascent " +
//                            "descent=$descent " +
//                            "leading=$leading"
//                    )

                    var parsed = false // В некоторых случаях надо будет делать два прохода

                    while (!parsed) {
                        // Собираем сегменты в строку и смотрим, не вышли ли мы за её пределы

                        val curLineWidth = max(0f, lineWidth -
                                if (isFirstLine) firstLeftIndent + firstRightIndent
                                else 0f)

//                        this.log.warning("\"${paragraph.text.toString().substring(segment.start,
//                                segment.end)}\" " +
//                                "curLineWidth=$curLineWidth " +
//                                "lineWidth=$lineWidth " +
//                                "isFirstLine=$isFirstLine " +
//                                "firstLeftIndent=$firstLeftIndent " +
//                                "firstRightIndent=$firstRightIndent " +
//                                "textPaint.size=${textPaint.textSize}")

                        val out = curLineWidth < width +
                                segment.spacesWidth + segment.textWidth

                        if (!out) {
                            // Если за пределы не вышли, то просто добавляем сегмент в список
                            isFirst = false
                            width += segment.spacesWidth + segment.textWidth
                            this.segments.add(segment)
                            parsed = true
                        }

//                        this.log.warning("width=$width " +
//                                "isFirst=${segment.isFirst} " +
//                                "eol=${segment.eol} " +
//                                "out=$out " +
//                                "spacesWidth=${segment.spacesWidth} " +
//                                "textWidth=${segment.textWidth} " +
//                                "fontSize=${segmentCharacterStyle.size.size} " +
//                                "parsed=$parsed " +
//                                "lm.fontsize=${paragraph.cacheSegmentLocalMetrics.fontSize}")

                        if (out || segment.end == parser.end || segment.eol) {

                            // При выходе за пределы строки, окончании абзаца
                            // и встрече символа переноса строки завершаем последнюю строку

                            var last = this.segments.lastIndex

                            if (out) {
                                if (segment.spaces == 0) {
                                    // Мы не можем переносить на другую строку по границе сегментов,
                                    // а только по границе слов. Учитываем это - ищем начало слова,
                                    // чтобы отделить его от текущей строки
                                    while (last >= first) {
                                        val hyphenWidth = this.segments[last].hyphenWidth
                                        if (hyphenWidth != 0f) {
                                            var w = hyphenWidth
                                            for (i in first..last) {
                                                w += this.segments[i].spacesWidth +
                                                        this.segments[i].textWidth
                                            }

                                            if (curLineWidth >= w) break
                                        }

                                        if (this.segments[last--].spaces != 0) break
                                    }
                                }

                                if (last < first) {
                                    // Если большой сегмент не вмещается в строку,
                                    // разбиваем, как можем

                                    // Расчитываем примерно, где будем делить сегмент. Исходим
                                    // из оставшегося незаполненного пространства в строке.
                                    // В соответствующей пропорции делим строку. Делим случайно,
                                    // в том числе можем разделить букву и диакритический знак.
                                    // Эту проблему решим позже
                                    val remainWidth = curLineWidth - width
                                    val divK = remainWidth / segment.textWidth
                                    var divPos = segment.start +
                                            ((segment.end - segment.start) * divK).toInt()

                                    var divWidth: Float

                                    // Если новый сегмент всё ещё больше оставшегося пространства,
                                    // уменьшаем ещё
                                    while (true) {
                                        divWidth = this.textPaint.measureText(paragraph.text,
                                                segment.start, divPos)
                                        if (divWidth <= remainWidth) break
                                        divPos--
                                    }

                                    // Хотя бы один символ, даже если и он выходит за границы,
                                    // всё же оставляем
                                    if (divPos == segment.start) divPos = segment.start + 1

                                    // А если слишком много убрали, то добавляем. Здесь же решается
                                    // проблема разделения между буквой и диакритическим знаком.
                                    // Как неимеющие положительной ширины, они автоматически
                                    // вернутся обратно
                                    while (divPos < segment.end - 1) {
                                        val divWidth2 = this.textPaint.measureText(
                                                paragraph.text, segment.start, divPos + 1)
                                        if (divWidth2 > remainWidth) break
                                        divPos++
                                        divWidth = divWidth2
                                    }

                                    val divSegment = Segment(
                                            isFirst = segment.isFirst,
                                            spaces = 0,
                                            start = segment.start,
                                            end = divPos,
                                            characterStyle = segment.characterStyle,
                                            font = segment.font,
                                            ascent = segment.ascent,
                                            descent = segment.descent,
                                            leading = segment.leading,
                                            spacesWidth = 0f,
                                            textWidth = divWidth,
                                            hyphenWidth = 0f,
                                            eol = false
                                    )

                                    // Новый сегмент добавляем, а с оставшимся разбираемся
                                    // следующим проходом
                                    this.segments.add(divSegment)
                                    last = this.segments.size - 1
                                    width += divSegment.textWidth

                                    segment.isFirst = true
                                    segment.start = divSegment.end
                                    segment.textWidth = this.textPaint.measureText(paragraph.text,
                                            segment.start, segment.end)
                                }
                            }

                            // Устанавливаем базовую линию для всей строки
                            var fullAscent = 0f
                            var fullDescent = 0f
                            var maxAscent = 0f
                            var maxDescent = 0f
                            var maxLeading = 0f
                            var isAutoLeading = false

                            for (i in first..last) {
                                val s = this.segments[i]

                                fullAscent = max(fullAscent, s.ascent)
                                fullDescent = max(fullDescent, s.descent)

                                if (s.leading != null) {
                                    maxLeading = max(maxLeading, s.leading)
                                } else {
                                    maxAscent = max(maxAscent, s.ascent)
                                    maxDescent = max(maxDescent, s.descent)
                                    isAutoLeading = true
                                }
                            }

                            // Варианты расчёта базовой линии:
                            // 1) Первая строка в документе (this.baseline == null). Интерлиньяж
                            //    не учитывается, отступ отсчитывается по ascent.
                            // 2) Все символы в строке имеют заданный интерлиньяж
                            //    (isAutoLeading == false), только его и учитываем.
                            // 3) В строке есть символы и с автоматическим интерлиньяжем и
                            //    с установленным. Рассчитываем по максимальному интерлиньяжу.
                            var baseline = when {
                                this.baseline == null -> paragraphBottom + fullAscent
                                !isAutoLeading -> this.baseline!! + maxLeading
                                else -> {
                                    max(paragraphBottom + maxAscent,
                                            this.baseline!! + maxLeading)
                                }
                            }

//                            this.log.warning("this.baseline=${this.baseline} " +
//                                    "baseline=$baseline " +
//                                    "maxLeading=$maxLeading " +
//                                    "maxAscent=$maxAscent " +
//                                    "maxDescent=$maxDescent " +
//                                    "fullDescent=$fullDescent"
//                            )

                            paragraphBottom = round(baseline + fullDescent)
                            baseline = round(baseline)
                            this.baseline = baseline

                            for (i in first..last) {
                                this.segments[i].baseline = baseline
                            }

                            width = 0f
                            isFirstLine = false
                            first = last + 1

                            if (!out) {
                                isFirst = true
                            } else {
                                var nextFirstSegment: Segment = segment

                                if (first < this.segments.size) {
                                    nextFirstSegment = this.segments[first]

                                    // Вычисляем ширину и базовую линию новой строки
                                    // (без последнего сегмента, т.к. он будет рассчитан отдельно)
                                    for (i in first until this.segments.size) {
                                        width += with(this.segments[i]) { spacesWidth + textWidth }
                                    }
                                }

                                nextFirstSegment.isFirst = true
                                nextFirstSegment.spacesWidth = 0f
                            }
                        }
                    }
                }
            }

            // Отрисовка
            if (canvas != null) {
                drawBorder(canvas, paragraph.borderStyle, paragraphTop, paragraphLeft,
                        paragraphBottom, paragraphRight, paragraph.cacheLocalMetrics)

                isFirstLine = true
                var leftOfLine: Float
                var rightOfLine: Float
                var x = 0f
                var spaceK = 1f
                lateinit var lastFirstSegment: Segment

                // Ищем последнюю строку
                for (i in this.segments.size - 1 downTo 0) {
                    if (this.segments[i].isFirst) {
                        lastFirstSegment = this.segments[i]
                        break
                    }
                }

                var lastSegmentIndex = 0
                var lastCharacterStyle: CharacterStyle? = null

                for (i in 0 until this.segments.size) {
                    val segment = this.segments[i]

                    // Т.к. текст разбит на слова, то мы не часто будем встречаться со сменой
                    // параметров шрифта. Незачем тогда и устанавливать их каждый раз заново
                    if (segment.characterStyle !== lastCharacterStyle) {
                        characterStyle2TextPaint(segment.characterStyle)
                    }
                    lastCharacterStyle = segment.characterStyle

                    // Начало новой строки
                    if (segment.isFirst) {
                        var align = paragraph.cacheParagraphStyle.align
                        spaceK = 1f
                        leftOfLine = paragraphLeft + leftIndent
                        rightOfLine = paragraphRight - rightIndent

                        if (isFirstLine) {
                            leftOfLine += firstLeftIndent
                            rightOfLine -= firstRightIndent
                            paragraph.cacheParagraphStyle.firstAlign?.also { align = it }
                            isFirstLine = false
                        } else if (segment === lastFirstSegment) {
                            if (paragraph.cacheParagraphStyle.lastAlign != null)
                                align = paragraph.cacheParagraphStyle.lastAlign
                            else if (align == ParagraphStyle.Align.JUSTIFY) {
                                align = ParagraphStyle.Align.LEFT
                            }
                        }

                        val curLineWidth = rightOfLine - leftOfLine
                        x = leftOfLine

                        for (j in i until this.segments.size) {
                            if (this.segments[j].isFirst && this.segments[j] !== segment) break
                            lastSegmentIndex = j
                        }

                        if (align != ParagraphStyle.Align.LEFT) {
                            width = this.segments[lastSegmentIndex].hyphenWidth
                            var spacesWidth = 0f
                            for (j in i..lastSegmentIndex) {
                                val p = this.segments[j]
                                width += p.spacesWidth + p.textWidth
                                spacesWidth += p.spacesWidth
                            }

                            val diff = curLineWidth - width
                            when (align) {
                                ParagraphStyle.Align.RIGHT -> x += diff
                                ParagraphStyle.Align.CENTER -> x += diff / 2f
                                else -> {
                                    spaceK = 1f + diff / spacesWidth
//                                    log.info(String.format(
//                                            "diff: %f  spacesWidth: %f  spaceK: %f",
//                                            diff, spacesWidth, spaceK))
                                }
                            }
                        }

                        // Базовая линия
                        if (this.baselineMode != Baseline.NONE) {
                            this.paint.color = this.baselineColor
                            if (this.baselineMode == Baseline.PARAGRAPH ||
                                    this.baselineMode == Baseline.VIEW) {
                                canvas.drawLine(baselineLeft, segment.baseline, baselineRight,
                                        segment.baseline,
                                        this.paint)
                            } else {
                                canvas.drawLine(leftOfLine, segment.baseline, rightOfLine,
                                        segment.baseline, this.paint)
                            }
                        }
                    }

                    x += segment.spacesWidth * if (spaceK.isInfinite()) 0f else spaceK

                    val withHyphen = i == lastSegmentIndex &&
                            this.segments[lastSegmentIndex].hyphenWidth != 0f

                    // Смещение уже приведено к PX
                    val baselineShift = segment.characterStyle.baselineShift.size

                    x += drawText(canvas, paragraph.text, segment.start, segment.end,
                            x, segment.baseline + baselineShift, this.textPaint)

                    if (withHyphen) {
                        x += drawText(canvas, segment.font.hyphen.toString(),
                                x, segment.baseline + baselineShift, this.textPaint)
                    }
                }
            }
        }

        return paragraphBottom +
                Size.toPixels(paragraph.cacheParagraphStyle.spaceAfter, this.deviceMetrics,
                        paragraph.cacheLocalMetrics, useParentSize = false) +
                Size.toPixels(paragraph.borderStyle.paddingBottom, this.deviceMetrics,
                        paragraph.cacheLocalMetrics) +
                Size.toPixels(paragraph.borderStyle.borderBottom, this.deviceMetrics,
                        paragraph.cacheLocalMetrics, useParentSize = false) +
                Size.toPixels(paragraph.borderStyle.marginBottom, this.deviceMetrics,
                        paragraph.cacheLocalMetrics)
    }

    private fun parseNextSegment(parser: StringParser, paragraph: Paragraph,
        characterStyle: CharacterStyle, localMetrics: Size.LocalMetrics
    ): Font {
        parser.start()

        val start = parser.start
        var end = parser.end
        var font: Font = characterStyle2TextPaint(characterStyle, localMetrics)

        for (span in paragraph.spans) {
            if (span.start > start) {
                end = min(end, span.start)
            } else if (span.end > start) {
                characterStyle.attach(span.characterStyle, this.deviceMetrics, localMetrics)
                font = characterStyle2TextPaint(characterStyle, localMetrics)
                end = min(end, span.end)
            }
        }

        parser.pos = end

        return font
    }

    /**
     * Установка параметров textPaint из стиля characterStyle.
     */
    internal fun characterStyle2TextPaint(characterStyle: CharacterStyle,
        localMetrics: Size.LocalMetrics? = null
    ): Font {

        this.textPaint.reset()
        this.textPaint.isAntiAlias = true

        val (font, getFontType) = getFont(characterStyle)

        if (getFontType != GetFontType.BY_FULL_NAME) {
            characterStyle.bold?.also { this.textPaint.isFakeBoldText = it }
            characterStyle.italic?.also { this.textPaint.textSkewX = if (it) -0.18f else 0f }
        }

        this.textPaint.typeface = font.typeface

        // Размер шрифта уже должен был быть приведён к абсолютному значению, иначе мы не сможем
        // его здесь вычислить. Структура localMetrics передаётся в эту функцию не для
        // использования, а для заполнения
        val fontSize =
                if (characterStyle.size.isAbsolute()) {
                    characterStyle.size.toPixels(this.deviceMetrics,
                            0f, 0f, 0f)
                } else {
                    1f
                }

        this.textPaint.textSize = fontSize * font.scale

        this.textPaint.textScaleX = characterStyle.scaleX
        characterStyle.color?.also { this.textPaint.color = it }
        characterStyle.strike?.also { this.textPaint.isStrikeThruText = it }
        characterStyle.underline?.also { this.textPaint.isUnderlineText = it }

        localMetrics?.also {
            val fontMetrics = font.correctFontMetrics(this.textPaint.fontMetrics)

            it.fontSize = fontSize
            it.fontAscent = fontMetrics.leading - fontMetrics.ascent
            it.fontDescent = fontMetrics.descent
            it.parentSize = null

            if (characterStyle.baselineShiftAdd.size != 0f) {
                characterStyle.baselineShift = Size.px(characterStyle.baselineShift.size +
                        characterStyle.baselineShiftAdd.toPixels(this.deviceMetrics, it))
                characterStyle.baselineShiftAdd = Size.px(0f)
            }
        }

        return font
    }

    /**
     * Рисование рамки с учётом разных размеров и цветов каждой из сторон.
     *
     * @param canvas Канвас.
     * @param borderStyle Параметры рамки и размеры отступов.
     * @param top
     * @param left
     * @param bottom
     * @param right Положение объекта (!) на канвасе.
     * @param localMetrics Параметры для вычисления отступов и размеров рамки.
     */
    internal fun drawBorder(canvas: Canvas, borderStyle: BorderStyle,
        top: Float, left: Float, bottom: Float, right: Float,
        localMetrics: Size.LocalMetrics
    ) {

        val inTop = top - Size.toPixels(borderStyle.paddingTop,
                this.deviceMetrics, localMetrics)
        val outTop = inTop - Size.toPixels(borderStyle.borderTop,
                this.deviceMetrics, localMetrics)

        val inBottom = bottom + Size.toPixels(borderStyle.paddingBottom,
                this.deviceMetrics, localMetrics)
        val outBottom = inBottom + Size.toPixels(borderStyle.borderBottom,
                this.deviceMetrics, localMetrics)

        val inLeft = left - Size.toPixels(borderStyle.paddingLeft,
                this.deviceMetrics, localMetrics, horizontal = true)
        val outLeft = inLeft - Size.toPixels(borderStyle.borderLeft,
                this.deviceMetrics, localMetrics, horizontal = true)

        val inRight = right + Size.toPixels(borderStyle.paddingRight,
                this.deviceMetrics, localMetrics, horizontal = true)
        val outRight = inRight + Size.toPixels(borderStyle.borderRight,
                this.deviceMetrics, localMetrics, horizontal = true)

        // Фон
        if (borderStyle.backgroundColor != 0) {
            this.paint.color = borderStyle.backgroundColor
            canvas.drawRect(outLeft, outTop, outRight, outBottom, this.paint)
        }

        var t = outTop
        var b = outBottom
        val l = if (Size.isZero(borderStyle.borderLeft)) outLeft else ceil(inLeft)
        val r = if (Size.isZero(borderStyle.borderRight)) outRight else floor(inRight)

        // Рамка
        if (Size.isNotZero(borderStyle.borderTop)) {
            t = ceil(inTop)

            if (Size.isNotZero(borderStyle.borderLeft)) {
                drawBorderCorner(canvas, this.paint, outLeft, outTop, inLeft, inTop,
                        borderStyle.borderLeft!!.color, borderStyle.borderTop!!.color)
            }

            if (Size.isNotZero(borderStyle.borderRight)) {
                drawBorderCorner(canvas, this.paint, outRight, outTop, inRight, inTop,
                        borderStyle.borderRight!!.color, borderStyle.borderTop!!.color)
            }

            drawBorderHLine(canvas, this.paint, l, r, outTop, inTop,
                    borderStyle.borderTop!!.color)
        }

        if (Size.isNotZero(borderStyle.borderBottom)) {
            b = floor(inBottom)

            if (Size.isNotZero(borderStyle.borderLeft)) {
                drawBorderCorner(canvas, this.paint, outLeft, outBottom, inLeft, inBottom,
                        borderStyle.borderLeft!!.color, borderStyle.borderBottom!!.color)
            }

            if (Size.isNotZero(borderStyle.borderRight)) {
                drawBorderCorner(canvas, this.paint, outRight, outBottom, inRight, inBottom,
                        borderStyle.borderRight!!.color, borderStyle.borderBottom!!.color)
            }

            drawBorderHLine(canvas, this.paint, l, r, inBottom, outBottom,
                    borderStyle.borderBottom!!.color)
        }

        if (Size.isNotZero(borderStyle.borderLeft)) {
            drawBorderVLine(canvas, this.paint, t, b, outLeft, inLeft,
                    borderStyle.borderLeft!!.color)
        }

        if (Size.isNotZero(borderStyle.borderRight)) {
            drawBorderVLine(canvas, this.paint, t, b, inRight, outRight,
                    borderStyle.borderRight!!.color)
        }
    }

    /**
     * Полное название шрифта в зависимости от параметров bold и italic в characterStyle.
     *
     * @return
     * 1) Если bold=false, italic=false, то "название_шрифта"
     * 2) Если bold=true, italic=false, то "название_шрифта:bold"
     * 3) Если bold=false, italic=true, то "название_шрифта:italic"
     * 4) Если bold=true, italic=true, то "название_шрифта:bold_italic"
     */
    private fun getFontFullName(characterStyle: CharacterStyle): String {
        var fontName = characterStyle.font ?: ""
        if (characterStyle.bold == true) {
            fontName += if (characterStyle.italic == true) ":bold_italic" else ":bold"
        } else if (characterStyle.italic == true) {
            fontName += ":italic"
        }

        return fontName
    }

    /**
     * Поиск необходимого шрифта, заданного в characterStyle, в списке шрифтов.
     *
     * @return
     * 1) Если шрифт найден по полному имени с учётом bold и italic, то найденный шрифт
     * с параметром GetFontType.BY_FULL_NAME.
     * 2) Если шрифт найден только по короткому имени, то найденный шрифт
     * с параметром GetFontType.BY_SHORT_NAME.
     * 3) Если шрифт не найден, то шрифт по-умолчанию с параметром GetFontType.DEFAULT.
     */
    private fun getFont(characterStyle: CharacterStyle): Pair<Font, GetFontType> {
        var getFontType = GetFontType.BY_FULL_NAME
        var font = this.fontList[getFontFullName(characterStyle)]

        if (font == null) {
            getFontType = GetFontType.BY_SHORT_NAME
            font = characterStyle.font?.let { this.fontList[it] }

            if (font == null) {
                getFontType = GetFontType.DEFAULT
                font = Font(Typeface.DEFAULT)
            }
        }


        return Pair(font, getFontType)
    }

    /**
     * Рисование текста с возможностью рисования базовой линии.
     */
    internal fun drawText(canvas: Canvas, text: CharSequence, x: Float, y: Float, paint: Paint,
        drawBaseline: Boolean = false
    ): Float {

        return drawText(canvas, text, 0, text.length, x, y, paint, drawBaseline)
    }

    /**
     * Рисование текста с возможностью рисования базовой линии.
     */
    internal fun drawText(canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, y: Float, paint: Paint, drawBaseline: Boolean = false
    ): Float {

        val width = paint.measureText(text, start, end)

        if (!drawBaseline) {
            canvas.drawText(text, start, end, x, y, paint)
        } else {
            val left = when (paint.textAlign) {
                Paint.Align.CENTER -> x - width / 2
                Paint.Align.RIGHT -> x - width
                else -> x
            }

            val right = left + width

            val textColor = paint.color
            paint.color = this.baselineColor
            canvas.drawLine(left, y, right, y, paint)
            paint.color = textColor

            canvas.drawText(text, start, end, x, y, paint)
        }

        return width
    }

    @SuppressLint("SwitchIntDef")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = View.MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = View.MeasureSpec.getSize(heightMeasureSpec)

        var minWidth = this.suggestedMinimumWidth
        var minHeight = this.suggestedMinimumHeight
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            minWidth = max(minWidth, this.minimumWidth)
            minHeight = max(minHeight, this.minimumHeight)
        }

        // DocumentView не рассчитан на wrap_content по ширине
        val width = when (widthMode) {
            View.MeasureSpec.EXACTLY -> widthSize
            View.MeasureSpec.AT_MOST -> widthSize // min(desiredWidth, widthSize)
            else -> minWidth // max(desiredWidth, minWidth)
        }
        if (width <= 0) throw IllegalArgumentException("Width of DocumentView cannot be zero")

        val desiredHeight by lazy { ceil(drawView(null, width)).toInt() }

        val height = when (heightMode) {
            View.MeasureSpec.EXACTLY -> heightSize
            View.MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> max(desiredHeight, minHeight)
        }

        setMeasuredDimension(width, height)
    }

    /**
     * Рисование угла рамки.
     *
     * Рамка вокруг текста может состоят из линий разных цветов. Собственно, эта функция
     * соединяет эти линии, смешивая цвета.
     *
     * @param outX
     * @param outY Координаты внешнего угла рамки.
     * @param inX
     * @param inY Координаты внутреннего угла рамки.
     * @param verticalColor Цвет вертикальной линии рамки.
     * @param horizontalColor Цвет горизонтальной линии рамки.
     */
    private fun drawBorderCorner(canvas: Canvas, paint: Paint,
        outX: Float, outY: Float,
        inX: Float, inY: Float,
        verticalColor: Int, horizontalColor: Int
    ) {

        // Для удобства расчётов будем всегда двигаться вправо и вниз,
        // переводя в нужное направление только при выводе
        val signX = if (inX > outX) 1.0 else -1.0
        val signY = if (inY > outY) 1.0 else -1.0

        // Смещение на 1 пиксель нужно, т.к. после смены знака надо и точки рисовать
        // в другую сторону. Вот чтобы этого избежать смещаемся на 1 пиксель
        val offsetX = if (signX < 0.0) -1.0 else 0.0
        val offsetY = if (signY < 0.0) -1.0 else 0.0

        val xStart = signX * outX
        val yStart = signY * outY
        val xEnd = signX * inX
        val yEnd = signY * inY

        val width = xEnd - xStart
        val height = yEnd - yStart
        var x1 = xStart
        var y1 = yStart

        // Изменения по осям X и Y (по оси X всегда на 1 пиксель)
        val dy = height / width

        val isAntiAlias = paint.isAntiAlias
        paint.isAntiAlias = false

        // Проходим попиксельно по оси x
        while (x1 < xEnd) {
            // В каком пикселе мы сейчас находимся (px1,py1) - (px2,py2)
            val px1 = floor(x1)
            val px2 = px1 + 1.0
            var py1 = floor(yStart)

            // Получаем прямоугольный треугольник (x1,y1) - (x1,y2) - (x2,y2),
            // в котором x2 выровнено по границе, т.е. по оси X мы всегда будем
            // находиться внутри одного пикселя, а по оси Y будем спускаться вниз
            val x2 = min(px2, xEnd)
            val w = x2 - x1
            val y2 = y1 + w * dy // Если расчёты x2 верны, то за yEnd мы не выйдем
            val h = y2 - y1

            // Полная площадь полученного треугольника
            val s = w * h / 2.0
            var siSum = 0.0 // Двигаясь по оси Y, будем понемногу выбирать нужную нам площадь

            // Теперь попиксельно движемся по оси Y
            while (py1 < yEnd) {

                var color: Int
                val py2 = py1 + 1.0

                // Площадь, которую занимают в пикселе оба цвета
                var sCom = (px2 - max(px1, xStart)) *
                        (py2 - max(py1, yStart))
                if (px2 > xEnd && py2 > yEnd) {
                    sCom -= (px2 - xEnd) * (py2 - yEnd)
                }

                if (py1 < y2 && py2 > y1) {
                    val yi = min(py2, y2)
                    val hi = max(yi - y1, 0.0)

                    // Альфа первого цвета зависит от площади, которую занимает треугольник
                    // в данном пикселе. В первом пикселе эта площадь пропорциональна площади
                    // всего треугольника (учитываем только, что при уменьшении высоты площадь
                    // уменьшается в квадрате). Площади в следующих пикселях вычисляем также
                    // через расчёт площади треугольника, только потом отнимаем площадь верхушки,
                    // оставшейся в предыдущих пикселях
                    val k = hi / h
                    val si = s * k * k - siSum
                    siSum += si

                    // Для рассчёта альфы остаётся к площади добавить прямоугольник
                    // (если он есть), расположенный ниже основания нашего треугольника
                    val a1 = si + w * (py2 - yi)

                    // В тех пикселях, где второй цвет занимает всю оставшуюся площадь, проблем
                    // с вычислением альфы второго цвета нет (a2 = 1 - a1). Сложности возникают
                    // на границах, где оба цвета занимают лишь часть пикселя. Для этого мы
                    // и расчитывали вначале площадь, которую занимают в пикселе оба цвета
                    val a2 = sCom - a1

                    color = verticalColor.mix(a1, horizontalColor, a2)
                } else if (py1 >= y2) {
                    color = verticalColor.mix(sCom)
                } else {
                    color = horizontalColor.mix(sCom)
                }

                // TODO: Всё рисуем по точкам, но где-то можно было бы заполнять линиями
                drawPoint(canvas,
                        (offsetX + signX * px1).toFloat(),
                        (offsetY + signY * py1).toFloat(),
                        color)

                py1 = py2
            }

            y1 = y2
            x1 = x2
        }

        paint.isAntiAlias = isAntiAlias
    }

    /**
     * Рисование горизотальных линий рамки.
     *
     * Для прорисовки линий рамки вместе с функцией drawBorderCorner().
     *
     * @param canvas Канвас.
     * @param paint Готовый объект класса Paint().
     * @param left Левая граница линии.
     * @param right Правая граница линии.
     * @param top Верх линии.
     * @param bottom Низ линии.
     * @param color Цвет линии.
     */
    private fun drawBorderHLine(canvas: Canvas, paint: Paint,
        left: Float, right: Float,
        top: Float, bottom: Float,
        color: Int
    ) {

        // Координаты для линии из "чистого" цвета
        val l = ceil(left)
        val r = floor(right)

        var py1 = floor(top)

        // Координаты и размеры отступов слева и справа, для которых понадобится antialias
        var wl = 0f
        var wr = 0f
        var ll = 0f
        var rr = 0f

        if (left < l) {
            wl = l - left
            ll = floor(left)
        }

        if (right > r) {
            wr = right - r
            rr = floor(right)
        }

        val isAntiAlias = paint.isAntiAlias
        paint.isAntiAlias = false

        while (py1 < bottom) {
            val py2 = py1 + 1f
            val h = min(py2, bottom) - max(py1, top)

            drawLine(canvas, l, py1, r, py1, color.mix(h))
            if (wl != 0f) drawPoint(canvas, ll, py1, color.mix(h * wl))
            if (wr != 0f) drawPoint(canvas, rr, py1, color.mix(h * wr))

            py1 = py2
        }

        paint.isAntiAlias = isAntiAlias
    }

    /**
     * Рисование вертикальных линий рамки.
     *
     * Для прорисовки линий рамки вместе с функцией drawBorderCorner().
     *
     * @param canvas Канвас.
     * @param paint Готовый объект класса Paint().
     * @param top Верхняя граница линии.
     * @param bottom Нижняя граница линии.
     * @param left Левая сторона линии.
     * @param right Правая сторона линии.
     * @param color Цвет линии.
     */
    private fun drawBorderVLine(canvas: Canvas, paint: Paint,
        top: Float, bottom: Float,
        left: Float, right: Float,
        color: Int
    ) {

        // Координаты для линии из "чистого" цвета
        val t = ceil(top)
        val b = floor(bottom)

        var px1 = floor(left)

        // Координаты и размеры отступов сверху и снизу, для которых понадобится antialias
        var ht = 0f
        var hb = 0f
        var tt = 0f
        var bb = 0f

        if (top < t) {
            ht = t - top
            tt = floor(top)
        }

        if (bottom > b) {
            hb = bottom - b
            bb = floor(bottom)
        }

        val isAntiAlias = paint.isAntiAlias
        paint.isAntiAlias = false

        while (px1 < right) {
            val px2 = px1 + 1f
            val w = min(px2, right) - max(px1, left)

            drawLine(canvas, px1, t, px1, b, color.mix(w))
            if (ht != 0f) drawPoint(canvas, px1, tt, color.mix(w * ht))
            if (hb != 0f) drawPoint(canvas, px1, bb, color.mix(w * hb))

            px1 = px2
        }

        paint.isAntiAlias = isAntiAlias
    }

    /**
     * Вычисление внутренних границ секций и параграфов.
     */
    private fun getInnerBoundary(clipTop: Float, clipLeft: Float, clipRight: Float,
        borderStyle: BorderStyle, deviceMetrics: Size.DeviceMetrics, localMetrics: Size.LocalMetrics
    ): Triple<Float, Float, Float> {
        val top = clipTop +
                Size.toPixels(borderStyle.marginTop,
                        deviceMetrics, localMetrics) +
                Size.toPixels(borderStyle.borderTop,
                        deviceMetrics, localMetrics, useParentSize = false) +
                Size.toPixels(borderStyle.paddingTop,
                        deviceMetrics, localMetrics)
        val left = clipLeft +
                Size.toPixels(borderStyle.marginLeft,
                        deviceMetrics, localMetrics, horizontal = true) +
                Size.toPixels(borderStyle.borderLeft,
                        deviceMetrics, localMetrics, horizontal = true, useParentSize = false) +
                Size.toPixels(borderStyle.paddingLeft,
                        deviceMetrics, localMetrics, horizontal = true)
        val right = clipRight -
                Size.toPixels(borderStyle.marginRight,
                        deviceMetrics, localMetrics, horizontal = true) -
                Size.toPixels(borderStyle.borderRight,
                        deviceMetrics, localMetrics, horizontal = true, useParentSize = false) -
                Size.toPixels(borderStyle.paddingRight,
                        deviceMetrics, localMetrics, horizontal = true)

        return Triple(top, left, right)
    }
}