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

    enum class Baseline { NONE, CHARACTERS, INDENT, PARAGRAPH, SECTION, VIEW }

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
    var fontList = FontList()
    val deviceMetrics: Size.DeviceMetrics
    var baselineMode = Baseline.NONE
    internal val textPaint = TextPaint()
    internal val borderPaint = Paint()
    internal val baselinePaint = Paint()
    internal val drawingData = SectionDrawingData(
            paragraphStyle = ParagraphStyle.default(),
            characterStyle = CharacterStyle.default(),
            localMetrics = Size.LocalMetrics()
    )

    var baselineColor: Int
        get() = this.baselinePaint.color
        set(value) {
            this.baselinePaint.color = value
        }

    var baselineWidth: Size
        get() = Size.px(this.baselinePaint.strokeWidth)
        set(value) {
            this.baselinePaint.strokeWidth = value.toPixels(this.deviceMetrics,
                    0f, 0f, 0f)
        }

    init {
        val displayMetrics = this.context.resources.displayMetrics
        this.deviceMetrics = Size.DeviceMetrics(displayMetrics.density,
                displayMetrics.scaledDensity, displayMetrics.xdpi, displayMetrics.ydpi)

        this.borderPaint.isAntiAlias = false
        this.borderPaint.style = Paint.Style.FILL

        this.baselinePaint.isAntiAlias = false
        baselineColor = Color.rgb(255, 0, 0)
        baselineWidth = Size.dp(1f)
    }

    // Для DSL
    operator fun invoke(init: DocumentView.() -> Unit): DocumentView {
        this.init()
        return this
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

        val desiredHeight by lazy {
            measureView(width)
            ceil(this.drawingData.outerBottom).toInt()
        }

        val height = when (heightMode) {
            View.MeasureSpec.EXACTLY -> heightSize
            View.MeasureSpec.AT_MOST -> min(desiredHeight, heightSize)
            else -> max(desiredHeight, minHeight)
        }

        setMeasuredDimension(width, height)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawView(canvas)
    }

    open fun measureView(width: Int = this.width) {
        applyCharacterStyle(this.drawingData.characterStyle, this.textPaint,
                this.drawingData.localMetrics)

        this.drawingData.apply {
            outerTop = 0f
            outerLeft = 0f
            outerRight = width.toFloat()
            innerTop = this@DocumentView.paddingTop.toFloat()
            innerLeft = this@DocumentView.paddingLeft.toFloat()
            innerRight = (width - this@DocumentView.paddingRight).toFloat()
            innerBottom = innerTop
            outerBottom = innerTop
        }

        measureSection(this.document, null, this.drawingData, 0f)
        this.drawingData.outerBottom = this.drawingData.innerBottom + this.paddingBottom
    }

    open fun drawView(canvas: Canvas) {
        drawSection(canvas, this.document, this.drawingData)
    }

    @Suppress("NAME_SHADOWING")
    private fun measureSection(section: Section, baseline: Float?, parent: SectionDrawingData,
        space: Float
    ): Pair<Float, Float> {

        var baseline = baseline
        var space = space
        val data = section.data as? SectionDrawingData
                ?: SectionDrawingData().let { section.data = it; it }

        data.paragraphStyle.copyFrom(parent.paragraphStyle)
                .attach(section.paragraphStyle)
        data.characterStyle.copyFrom(parent.characterStyle)
                .attach(section.characterStyle, this.deviceMetrics, parent.localMetrics)

        // Метрики, необходимые для рассчёта размеров (если они указаны в em, ratio и fh).
        // Размеры уже рассчитаны с учётом density и scaledDensity
        applyCharacterStyle(data.characterStyle, this.textPaint, data.localMetrics)
        data.localMetrics.parentSize = parent.innerRight - parent.innerLeft

        // Границы секции
        space = measureBoundary(section, parent, section.borderStyle, null,
                this.deviceMetrics, data, space)

        // Если ignoreFirstMargin = true, схлопываем первый попавшийся margin
        if (section.ignoreFirstMargin) space = -1f

        if (baseline == null && section.firstBaselineToTop) baseline = data.innerTop

        // diff - Разница между текущим space и space, который был у потомка. Т.е. то,
        // что мы можем убрать при ignoreLastMargin
        var diff = 0f
        var lastItem: ParagraphItem? = null

        for (item in section.items) {
            lastItem = item
            lateinit var res: Pair<Float, Float>
            when (item) {
                is Section -> res = measureSection(item, baseline, data, space)
                is Paragraph -> res = measureParagraph(section, item, baseline, data, space)
            }
            space = res.first
            diff = res.second
        }

        if (section.items.isEmpty()) space = 0f

        (lastItem?.data as? SectionDrawingData)?.also {
            if (section.ignoreLastMargin) {
                it.outerBottom -= diff
                data.innerBottom -= diff
                space = 0f
            }
        }

        val paddingBottom = Size.toPixels(section.borderStyle.paddingBottom,
                this.deviceMetrics, data.localMetrics)
        val borderBottom = Size.toPixels(section.borderStyle.borderBottom,
                this.deviceMetrics, data.localMetrics, useParentSize = false)
        val marginBottom = Size.toPixels(section.borderStyle.marginBottom,
                this.deviceMetrics, data.localMetrics)

        if (borderBottom != 0f || paddingBottom != 0f) space = 0f
        data.innerBottom -= space
        data.outerBottom = data.innerBottom + paddingBottom + borderBottom + marginBottom

        var newSpace = max(space, marginBottom)
        diff = newSpace - space
        if (!section.marginCollapsing) newSpace = 0f

        parent.innerBottom = max(data.outerBottom, data.innerBottom + newSpace)

        return Pair(newSpace, diff)
    }

    /**
     * Рисование секции или вычисление необходимой высоты секции (при canvas == null).
     */
    open fun drawSection(canvas: Canvas, section: Section, parent: SectionDrawingData
    ) {
        val data = section.data as SectionDrawingData

        drawBorder(canvas, section.borderStyle, data)

        for (item in section.items) {
            when (item) {
                is Section -> drawSection(canvas, item, data)
                is Paragraph -> drawParagraph(canvas, section, item, data)
            }
        }
    }

    @Suppress("NAME_SHADOWING")
    private fun measureParagraph(section: Section, paragraph: Paragraph, baseline: Float?,
        parent: SectionDrawingData, space: Float
    ): Pair<Float, Float> {
        var baseline = baseline
        val data = paragraph.data as? ParagraphDrawingData
                ?: ParagraphDrawingData().let { paragraph.data = it; it }

        data.paragraphStyle.copyFrom(parent.paragraphStyle).attach(paragraph.paragraphStyle)
        data.characterStyle.copyFrom(parent.characterStyle)
                .attach(paragraph.characterStyle, this.deviceMetrics, parent.localMetrics)

        // Метрики, необходимые для рассчёта размеров (если они указаны в em, ratio и fh).
        // Размеры уже рассчитаны с учётом density и scaledDensity
        applyCharacterStyle(data.characterStyle, this.textPaint, data.localMetrics)
        data.localMetrics.parentSize = parent.innerRight - parent.innerLeft

        // Границы абзаца
        measureBoundary(section, parent, paragraph.borderStyle, data.paragraphStyle.spaceBefore,
                this.deviceMetrics, data, space)

        if (paragraph.text.isNotEmpty() || section.drawEmptyParagraph) {
            // Вычисляем размеры абзаца, разбиваем абзац на строки

            val parser = StringParser(paragraph.text)

            data.leftIndent = Size.toPixels(data.paragraphStyle.leftIndent,
                    this.deviceMetrics, data.localMetrics, horizontal = true)
            data.rightIndent = Size.toPixels(data.paragraphStyle.rightIndent,
                    this.deviceMetrics, data.localMetrics, horizontal = true)
            data.firstLeftIndent = Size.toPixels(data.paragraphStyle.firstLeftIndent,
                    this.deviceMetrics, data.localMetrics, horizontal = true)
            data.firstRightIndent = Size.toPixels(data.paragraphStyle.firstRightIndent,
                    this.deviceMetrics, data.localMetrics, horizontal = true)

            val paragraphWidth = data.innerRight - data.innerLeft
            val lineWidth = paragraphWidth - data.leftIndent - data.rightIndent

//            this.log.warning("parentWidth=${data.localMetrics.parentSize} " +
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

            data.segments.clear()

            // Парсим строку абзаца
            while (!parser.eof()) {
                // Находим в тексте очередной сегмент с одним стилем
                val segmentCharacterStyle = data.characterStyle.clone()
                data.segmentLocalMetrics.copyFrom(data.localMetrics)

                val segmentFont = parseNextSegment(parser, paragraph,
                        segmentCharacterStyle, data.segmentLocalMetrics)

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
                                    this.deviceMetrics, data.segmentLocalMetrics,
                                    useParentSize = false)

                    val ascent = data.segmentLocalMetrics.fontAscent -
                            segmentCharacterStyle.baselineShift.size
                    val descent = data.segmentLocalMetrics.fontDescent +
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
                                if (isFirstLine) data.firstLeftIndent + data.firstRightIndent
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
                            data.segments.add(segment)
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
//                                "lm.fontsize=${data.segmentLocalMetrics.fontSize}")

                        if (out || segment.end == parser.end || segment.eol) {

                            // При выходе за пределы строки, окончании абзаца
                            // и встрече символа переноса строки завершаем последнюю строку

                            var last = data.segments.lastIndex

                            if (out) {
                                if (segment.spaces == 0) {
                                    // Мы не можем переносить на другую строку по границе сегментов,
                                    // а только по границе слов. Учитываем это - ищем начало слова,
                                    // чтобы отделить его от текущей строки
                                    while (last >= first) {
                                        val hyphenWidth = data.segments[last].hyphenWidth
                                        if (hyphenWidth != 0f) {
                                            var w = hyphenWidth
                                            for (i in first..last) {
                                                w += data.segments[i].spacesWidth +
                                                        data.segments[i].textWidth
                                            }

                                            if (curLineWidth >= w) break
                                        }

                                        if (data.segments[last--].spaces != 0) break
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
                                    data.segments.add(divSegment)
                                    last = data.segments.size - 1
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
                                val s = data.segments[i]

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
                            // 1) Первая строка в документе (baseline == null). Интерлиньяж
                            //    не учитывается, отступ отсчитывается по ascent.
                            // 2) Все символы в строке имеют заданный интерлиньяж
                            //    (isAutoLeading == false), только его и учитываем.
                            // 3) В строке есть символы и с автоматическим интерлиньяжем и
                            //    с установленным. Рассчитываем по максимальному интерлиньяжу.
                            baseline = round(when {
                                baseline == null -> data.innerBottom + fullAscent
                                !isAutoLeading -> baseline + maxLeading
                                else -> {
                                    max(data.innerBottom + maxAscent, baseline + maxLeading)
                                }
                            })

//                            this.log.warning("baseline=$baseline " +
//                                    "maxLeading=$maxLeading " +
//                                    "maxAscent=$maxAscent " +
//                                    "maxDescent=$maxDescent " +
//                                    "fullDescent=$fullDescent"
//                            )

                            data.innerBottom = round(baseline + fullDescent)

                            for (i in first..last) {
                                data.segments[i].baseline = baseline
                            }

                            width = 0f
                            isFirstLine = false
                            first = last + 1

                            if (!out) {
                                isFirst = true
                            } else {
                                var nextFirstSegment: Segment = segment

                                if (first < data.segments.size) {
                                    nextFirstSegment = data.segments[first]

                                    // Вычисляем ширину и базовую линию новой строки
                                    // (без последнего сегмента, т.к. он будет рассчитан отдельно)
                                    for (i in first until data.segments.size) {
                                        width += with(data.segments[i]) { spacesWidth + textWidth }
                                    }
                                }

                                nextFirstSegment.isFirst = true
                                nextFirstSegment.spacesWidth = 0f
                            }
                        }
                    }
                }
            }
        }

        val marginBottom = Size.toPixels(paragraph.borderStyle.marginBottom,
                this.deviceMetrics, data.localMetrics) +
                Size.toPixels(data.paragraphStyle.spaceAfter, this.deviceMetrics,
                        data.localMetrics, useParentSize = false)

        data.outerBottom = data.innerBottom +
                Size.toPixels(paragraph.borderStyle.paddingBottom, this.deviceMetrics,
                        data.localMetrics) +
                Size.toPixels(paragraph.borderStyle.borderBottom, this.deviceMetrics,
                        data.localMetrics, useParentSize = false) +
                marginBottom

        parent.innerBottom = data.outerBottom
        return Pair(if (section.marginCollapsing) marginBottom else 0f, marginBottom)
    }

    open fun drawParagraph(canvas: Canvas, section: Section, paragraph: Paragraph,
        parent: SectionDrawingData
    ) {
        val data = paragraph.data as ParagraphDrawingData

        if (paragraph.text.isNotEmpty() || section.drawEmptyParagraph) {
            drawBorder(canvas, paragraph.borderStyle, data)

            var isFirstLine = true
            var leftOfLine: Float
            var rightOfLine: Float
            var x = 0f
            var spaceK = 1f
            lateinit var lastFirstSegment: Segment

            // Ищем последнюю строку
            for (i in data.segments.size - 1 downTo 0) {
                if (data.segments[i].isFirst) {
                    lastFirstSegment = data.segments[i]
                    break
                }
            }

            var lastSegmentIndex = 0
            var lastCharacterStyle: CharacterStyle? = null

            for (i in 0 until data.segments.size) {
                val segment = data.segments[i]

                // Т.к. текст разбит на слова, то мы не часто будем встречаться со сменой
                // параметров шрифта. Незачем тогда и устанавливать их каждый раз заново
                if (segment.characterStyle !== lastCharacterStyle) {
                    applyCharacterStyle(segment.characterStyle, this.textPaint)
                }
                lastCharacterStyle = segment.characterStyle

                // Начало новой строки
                if (segment.isFirst) {
                    var align = data.paragraphStyle.align
                    spaceK = 1f
                    leftOfLine = data.innerLeft + data.leftIndent
                    rightOfLine = data.innerRight - data.rightIndent

                    if (isFirstLine) {
                        leftOfLine += data.firstLeftIndent
                        rightOfLine -= data.firstRightIndent
                        data.paragraphStyle.firstAlign?.also { align = it }
                        isFirstLine = false
                    } else if (segment === lastFirstSegment) {
                        if (data.paragraphStyle.lastAlign != null)
                            align = data.paragraphStyle.lastAlign
                        else if (align == ParagraphStyle.Align.JUSTIFY) {
                            align = ParagraphStyle.Align.LEFT
                        }
                    }

                    val curLineWidth = rightOfLine - leftOfLine
                    x = leftOfLine

                    for (j in i until data.segments.size) {
                        if (data.segments[j].isFirst && data.segments[j] !== segment) break
                        lastSegmentIndex = j
                    }

                    if (align != ParagraphStyle.Align.LEFT) {
                        var width = data.segments[lastSegmentIndex].hyphenWidth
                        var spacesWidth = 0f
                        for (j in i..lastSegmentIndex) {
                            val p = data.segments[j]
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
                    when {
                        this.baselineMode == Baseline.VIEW -> {
                            canvas.drawLine(0f, segment.baseline,
                                    this.width.toFloat(), segment.baseline, this.baselinePaint)
                        }
                        this.baselineMode == Baseline.SECTION -> {
                            canvas.drawLine(parent.blockLeft, segment.baseline,
                                    parent.blockRight, segment.baseline, this.baselinePaint)
                        }
                        this.baselineMode == Baseline.PARAGRAPH -> {
                            canvas.drawLine(data.blockLeft, segment.baseline,
                                    data.blockRight, segment.baseline, this.baselinePaint)
                        }
                        this.baselineMode == Baseline.INDENT -> {
                            canvas.drawLine(leftOfLine, segment.baseline,
                                    rightOfLine, segment.baseline, this.baselinePaint)
                        }
                    }
                }

                x += segment.spacesWidth * if (spaceK.isInfinite()) 0f else spaceK

                val withHyphen = i == lastSegmentIndex &&
                        data.segments[lastSegmentIndex].hyphenWidth != 0f

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

    private fun parseNextSegment(parser: StringParser, paragraph: Paragraph,
        characterStyle: CharacterStyle, localMetrics: Size.LocalMetrics
    ): Font {
        parser.start()

        val start = parser.start
        var end = parser.end
        var font: Font = applyCharacterStyle(characterStyle, this.textPaint, localMetrics)

        for (span in paragraph.spans) {
            if (span.start > start) {
                end = min(end, span.start)
            } else if (span.end > start) {
                characterStyle.attach(span.characterStyle, this.deviceMetrics, localMetrics)
                font = applyCharacterStyle(characterStyle, this.textPaint, localMetrics)
                end = min(end, span.end)
            }
        }

        parser.pos = end

        return font
    }

    /**
     * Установка параметров textPaint из стиля characterStyle.
     *
     * Ф-я использует свойство класса: deviceMetrics.
     */
    internal fun applyCharacterStyle(characterStyle: CharacterStyle, textPaint: TextPaint,
        localMetrics: Size.LocalMetrics? = null
    ): Font {

        textPaint.reset()
        textPaint.isAntiAlias = true

        val (font, getFontType) = getFont(characterStyle)

        if (getFontType != GetFontType.BY_FULL_NAME) {
            characterStyle.bold?.also { textPaint.isFakeBoldText = it }
            characterStyle.italic?.also { textPaint.textSkewX = if (it) -0.18f else 0f }
        }

        textPaint.typeface = font.typeface

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

        textPaint.textSize = fontSize * font.scale

        textPaint.textScaleX = characterStyle.scaleX
        characterStyle.color?.also { textPaint.color = it }
        characterStyle.strike?.also { textPaint.isStrikeThruText = it }
        characterStyle.underline?.also { textPaint.isUnderlineText = it }

        localMetrics?.also {
            val fontMetrics = font.correctFontMetrics(textPaint.fontMetrics)

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

    internal fun drawBorder(canvas: Canvas, borderStyle: BorderStyle, data: SectionDrawingData
    ) = drawBorder(canvas, borderStyle,
            data.innerLeft, data.innerTop, data.innerRight, data.innerBottom,
            data.outerLeft, data.outerTop, data.outerRight, data.outerBottom,
            data.localMetrics)

    internal fun drawBorder(canvas: Canvas, borderStyle: BorderStyle,
        innerLeft: Float, innerTop: Float, innerRight: Float, innerBottom: Float,
        outerLeft: Float, outerTop: Float, outerRight: Float, outerBottom: Float,
        localMetrics: Size.LocalMetrics
    ) {
        val inTop = innerTop -
                Size.toPixels(borderStyle.paddingTop, this.deviceMetrics, localMetrics)
        val outTop = inTop -
                Size.toPixels(borderStyle.borderTop, this.deviceMetrics, localMetrics)

        val inBottom = innerBottom +
                Size.toPixels(borderStyle.paddingBottom, this.deviceMetrics, localMetrics)
        val outBottom = inBottom +
                Size.toPixels(borderStyle.borderBottom, this.deviceMetrics, localMetrics)

        val inLeft = innerLeft -
                Size.toPixels(borderStyle.paddingLeft, this.deviceMetrics, localMetrics)
        val outLeft = inLeft -
                Size.toPixels(borderStyle.borderLeft, this.deviceMetrics, localMetrics)

        val inRight = innerRight +
                Size.toPixels(borderStyle.paddingRight, this.deviceMetrics, localMetrics)
        val outRight = inRight +
                Size.toPixels(borderStyle.borderRight, this.deviceMetrics, localMetrics)

        // Отступ
        if (borderStyle.marginColor != Color.TRANSPARENT) {
            this.borderPaint.color = borderStyle.marginColor
            canvas.drawRect(outerLeft, outerTop, outerRight, outerBottom, this.borderPaint)
        }

        // Фон
        if (borderStyle.backgroundColor != Color.TRANSPARENT) {
            this.borderPaint.color = borderStyle.backgroundColor
            canvas.drawRect(outLeft, outTop, outRight, outBottom, this.borderPaint)
        }

        var t = outTop
        var b = outBottom
        val l = if (Size.isZero(borderStyle.borderLeft)) outLeft else ceil(inLeft)
        val r = if (Size.isZero(borderStyle.borderRight)) outRight else floor(inRight)

        // Рамка
        if (Size.isNotZero(borderStyle.borderTop)) {
            t = ceil(inTop)

            if (Size.isNotZero(borderStyle.borderLeft)) {
                drawBorderCorner(canvas, this.borderPaint, outLeft, outTop, inLeft, inTop,
                        borderStyle.borderLeft!!.color, borderStyle.borderTop!!.color)
            }

            if (Size.isNotZero(borderStyle.borderRight)) {
                drawBorderCorner(canvas, this.borderPaint, outRight, outTop, inRight, inTop,
                        borderStyle.borderRight!!.color, borderStyle.borderTop!!.color)
            }

            drawBorderHLine(canvas, this.borderPaint, l, r, outTop, inTop,
                    borderStyle.borderTop!!.color)
        }

        if (Size.isNotZero(borderStyle.borderBottom)) {
            b = floor(inBottom)

            if (Size.isNotZero(borderStyle.borderLeft)) {
                drawBorderCorner(canvas, this.borderPaint, outLeft, outBottom, inLeft, inBottom,
                        borderStyle.borderLeft!!.color, borderStyle.borderBottom!!.color)
            }

            if (Size.isNotZero(borderStyle.borderRight)) {
                drawBorderCorner(canvas, this.borderPaint, outRight, outBottom, inRight, inBottom,
                        borderStyle.borderRight!!.color, borderStyle.borderBottom!!.color)
            }

            drawBorderHLine(canvas, this.borderPaint, l, r, inBottom, outBottom,
                    borderStyle.borderBottom!!.color)
        }

        if (Size.isNotZero(borderStyle.borderLeft)) {
            drawBorderVLine(canvas, this.borderPaint, t, b, outLeft, inLeft,
                    borderStyle.borderLeft!!.color)
        }

        if (Size.isNotZero(borderStyle.borderRight)) {
            drawBorderVLine(canvas, this.borderPaint, t, b, inRight, outRight,
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
    internal fun drawText(canvas: Canvas, text: CharSequence, x: Float, y: Float, paint: Paint
    ): Float {

        return drawText(canvas, text, 0, text.length, x, y, paint)
    }

    /**
     * Рисование текста с возможностью рисования базовой линии.
     */
    internal fun drawText(canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, y: Float, paint: Paint
    ): Float {

        val width = paint.measureText(text, start, end)
        val baseline = round(y)

        if (this.baselineMode == Baseline.CHARACTERS) {
            val left = when (paint.textAlign) {
                Paint.Align.CENTER -> x - width / 2
                Paint.Align.RIGHT -> x - width
                else -> x
            }

            val right = left + width

            canvas.drawLine(round(left), baseline, round(right), baseline, this.baselinePaint)
        }

        canvas.drawText(text, start, end, round(x), baseline, paint)

        return width
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

                this.borderPaint.color = color
                canvas.drawPoint(
                        (offsetX + signX * px1).toFloat(),
                        (offsetY + signY * py1).toFloat(), this.borderPaint)

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

            this.borderPaint.color = color.mix(h)
            canvas.drawLine(l, py1, r, py1, this.borderPaint)

            if (wl != 0f) {
                this.borderPaint.color = color.mix(h * wl)
                canvas.drawPoint(ll, py1, this.borderPaint)
            }

            if (wr != 0f) {
                this.borderPaint.color = color.mix(h * wr)
                canvas.drawPoint(rr, py1, this.borderPaint)
            }

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

            this.borderPaint.color = color.mix(w)
            canvas.drawLine(px1, t, px1, b, this.borderPaint)

            if (ht != 0f) {
                this.borderPaint.color = color.mix(w * ht)
                canvas.drawPoint(px1, tt, this.borderPaint)
            }

            if (hb != 0f) {
                this.borderPaint.color = color.mix(w * hb)
                canvas.drawPoint(px1, bb, this.borderPaint)
            }

            px1 = px2
        }

        paint.isAntiAlias = isAntiAlias
    }

    /**
     * Вычисление внутренних границ секций и параграфов.
     */
    private fun measureBoundary(section: Section, parent: SectionDrawingData,
        borderStyle: BorderStyle, spaceBefore: Size?, deviceMetrics: Size.DeviceMetrics,
        data: SectionDrawingData, space: Float
    ): Float {
        // Если передан space < 0, удаляем marginTop
        val marginTop =
                if (space < 0f) 0f
                else Size.toPixels(spaceBefore, deviceMetrics,
                        data.localMetrics, useParentSize = false) + Size.toPixels(
                        borderStyle.marginTop, deviceMetrics, data.localMetrics)
        val maxMargin = max(space, marginTop)

//        data.outerTop = parent.innerBottom - marginTop + if (space < 0f) 0f else maxMargin - space
        data.outerTop = parent.innerBottom + if (space < 0f) 0f else maxMargin - space - marginTop
        data.outerLeft = parent.innerLeft
        data.outerRight = parent.innerRight

        val borderTop = Size.toPixels(borderStyle.borderTop, deviceMetrics,
                data.localMetrics, useParentSize = false)
        val paddingTop = Size.toPixels(borderStyle.paddingTop, deviceMetrics,
                data.localMetrics)

        data.innerTop = data.outerTop + marginTop + borderTop + paddingTop

        data.blockLeft = data.outerLeft +
                Size.toPixels(borderStyle.marginLeft, deviceMetrics, data.localMetrics,
                        horizontal = true) +
                Size.toPixels(borderStyle.borderLeft, deviceMetrics, data.localMetrics,
                        horizontal = true, useParentSize = false)
        data.innerLeft = data.blockLeft +
                Size.toPixels(borderStyle.paddingLeft, deviceMetrics, data.localMetrics,
                        horizontal = true)

        data.blockRight = data.outerRight -
                Size.toPixels(borderStyle.marginRight, deviceMetrics, data.localMetrics,
                        horizontal = true) -
                Size.toPixels(borderStyle.borderRight, deviceMetrics, data.localMetrics,
                        horizontal = true, useParentSize = false)
        data.innerRight = data.blockRight -
                Size.toPixels(borderStyle.paddingRight, deviceMetrics, data.localMetrics,
                        horizontal = true)

        data.innerBottom = data.innerTop
        data.outerBottom = data.innerTop

        return if (section.marginCollapsing && borderTop == 0f && paddingTop == 0f) maxMargin else 0f
    }
}