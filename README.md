# DocumentView

`DocumentView` это Kotlin-виджет для Android для вывода отформатированного текста. Аналог [TextView](https://developer.android.com/reference/android/widget/TextView) из Android SDK с его [Spannable](https://developer.android.com/reference/android/text/Spannable), но с более широкими (в планах!) возможностями в части форматирования текста.

Работа только начата. Пока доступно только самое простое форматирование текста, но уже есть выравнивание по ширине (justification) и обработка мягких переносов.

[document]:https://github.com/vi-k/kotlin-utils/wiki/document
[html]:https://github.com/vi-k/kotlin-utils/wiki/html
[htmldocument]:https://github.com/vi-k/kotlin-utils/wiki/htmldocument
[color]:https://github.com/vi-k/kotlin-utils/wiki/color

## Содержание
- [Простой пример](#Простой-пример)
- [Абзацы](#Абзацы)
- [Шрифты](#Шрифты)
- [Рамки](#Рамки)
- [Оформление абзацев](#Оформление-абзацев)
- [Коррекция шрифтов](#Коррекция-шрифтов)
- [Переносы слов](#Переносы-слов)
- [Базовые линии](#Базовые-линии)
- [Секции](#Секции)

## Простой пример

Layout:

```xml
<ru.vik.documentview.DocumentView
    android:id="@+id/docView"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

MainActivity.kt:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val docView: DocumentView = findViewById(R.id.docView)

    docView.document.setText("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod ...")
    docView.document
            .addSpan(0, 5, CharacterStyle(color = Color.RED))
            .addSpan(6, 11, CharacterStyle(bold = true))
            .addSpan(12, 17, CharacterStyle(italic = true))
            .addSpan(18, 21, CharacterStyle(bold = true, italic = true))
            .addSpan(22, 26, CharacterStyle(underline = true))
            .addSpan(28, 39, CharacterStyle(strike = true))
            .addSpan(50, 55, CharacterStyle(baselineShift = Size.em(-0.4f),
                    size = Size.em(0.85f)))
            .addSpan(60, 63, CharacterStyle(baselineShift = Size.em(0.25f),
                    size = Size.em(0.85f)))
            .addSpan(64, 71, CharacterStyle(scaleX = 0.6f))
}
```

![screenshot_1.png](docs/screenshot_1.png)

Виджет содержит внутри себя объект платформонезависимого класса `Document` ([wiki][document]), который хранит в себе форматируемый документ. С ним мы и работаем, добавляя участки форматирования. Класс Span хранит в себе стиль знаков, начало и конец форматирования.

Не всегда удобно отсчитывать количество символов для создания участков, для быстрого форматирования проще прибегнуть к нумерации слов. Функция `addWordSpan()` принимает на вход первым параметром номер слова (нумерация начинается с 1). Вторым параметром можно указать количество слов, на которые надо распространить форматирование.

```kotlin
docView.document
        .addWordSpan(1, CharacterStyle(color = Color.RED))
        .addWordSpan(2, CharacterStyle(bold = true))
        .addWordSpan(3, CharacterStyle(italic = true))
        .addWordSpan(4, CharacterStyle(bold = true, italic = true))
        .addWordSpan(5, CharacterStyle(underline = true))
        .addWordSpan(6, CharacterStyle(strike = true))
        .addWordSpan(8, CharacterStyle(baselineShift = Size.em(-0.4f), size = Size.em(0.85f)))
        .addWordSpan(10, CharacterStyle(baselineShift = Size.em(0.25f), size = Size.em(0.85f)))
        .addWordSpan(11, CharacterStyle(scaleX = 0.6f))
```

Участки могут пересекаться. В этом случае они либо дополняют друг друга, либо последний перекрывает первый:

```kotlin
docView.document.setText("Lorem ipsum dolor sit amet ...")
docView.document
        .addWordSpan(1, 3, CharacterStyle(bold = true))
        .addWordSpan(3, 3, CharacterStyle(italic = true))
```

![screenshot_2.png](docs/screenshot_2.png)

```kotlin
docView.document
        .addWordSpan(1, 3, CharacterStyle(color = Color.RED))
        .addWordSpan(3, -1, CharacterStyle(color = Color.GREEN)) // count = -1 - до конца абзаца
```

![screenshot_2_2.png](docs/screenshot_2_2.png)

Если в `addWordSpan()` вместо количества слов указано отрицательное значение, то правой границей участка станет конец абзаца.

Класс `Document` является основой для создания классов-наследников, которые будут конвертировать исходный текст в каком-либо формате (HTML, Markdown и т.п.) во внутреннюю структуру `Document`. Для конвертации HTML есть модуль [htmldocument].

## Абзацы

В предыдущих примерах был только один абзац. Если же абзацев несколько, то доступ к каждому осуществляется по индексу:

```kotlin
docView.document.setText("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed " +
        "do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n" +
        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut " +
        "aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit " +
        "in voluptate velit esse cillum dolore eu fugiat nulla pariatur.\n" +
        "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia " +
        "deserunt mollit anim id est laborum.")
docView.document[0]
        .addSpan(0, 1, CharacterStyle(color = Color.RED))
docView.document[1]
        .addSpan(0, 1, CharacterStyle(color = Color.RED))
docView.document[2]
        .addSpan(0, 1, CharacterStyle(color = Color.RED))
```

![screenshot_3.png](docs/screenshot_3.png)

У абзаца есть собственные настройки стиля знаков, изменение которого оказывает воздействие на весь абзац:

```kotlin
docView.document[0].characterStyle.italic = true
docView.document[1].characterStyle.size = Size.em(0.8f)
docView.document[2].characterStyle.color = Color.GRAY
```

![screenshot_3_2.png](docs/screenshot_3_2.png)

Форматирование, добавленное через `addSpan()`, накладывается поверх общих настроек абзаца.

## Шрифты

Чтобы использовать другие шрифты, кроме стандартного, их надо создать и добавить в список `fontList`:

```kotlin
docView.fontList.createFamily("sans_serif", Font(Typeface.SANS_SERIF))
docView.fontList.createFamily("serif", Font(Typeface.SERIF))
docView.fontList.createFamily("mono", Font(Typeface.MONOSPACE))

docView.document[0].characterStyle.font = "sans_serif"
docView.document[1].characterStyle.font = "serif"
docView.document[2].characterStyle.font = "mono"
```

![screenshot_4.png](docs/screenshot_4.png)

Функция `createFamily()` создаёт сразу 4 шрифта для разных начертаний: нормального, **полужирного**, *курсива* и ***полужирного вместе с курсивом***. Это возможно только для встроенных шрифтов. Для пользовательских шрифтов все файлы с начертаниями необходимо загрузить отдельно. Если этого не сделать, соответствующий шрифт будет при необходимости генерироваться автоматически. Но специально приготовленные шрифты могут существенно отличаться от генерируемых:

```kotlin
docView.document.setText("Lorem ipsum dolor sit amet ...\n" +
        "Lorem ipsum dolor sit amet ...")
docView.document[0]
        .addWordSpan(1, 3, CharacterStyle(bold = true))
        .addWordSpan(3, 3, CharacterStyle(italic = true))
docView.document[1]
        .addWordSpan(1, 3, CharacterStyle(bold = true))
        .addWordSpan(3, 3, CharacterStyle(italic = true))

docView.fontList.createFamily("serif1", Font(Typeface.SERIF))
docView.fontList["serif2"] = Font(Typeface.SERIF)

docView.document[0].characterStyle.font = "serif1"
docView.document[1].characterStyle.font = "serif2"
```

![screenshot_5.png](docs/screenshot_5.png)

Чтобы `DocumentView` в нужные моменты мог задействовать нужные шрифты, при создании шрифта к основному названию надо добавить соответствующий постфикс: `:bold`, `:italic`, `:bold_italic`

```kotlin
docView.fontList["serif2:bold"] = Font(Typeface.create(Typeface.SERIF, Typeface.BOLD))
docView.fontList["serif2:italic"] = Font(Typeface.create(Typeface.SERIF, Typeface.ITALIC))
docView.fontList["serif2:bold_italic"] = Font(Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC))
```

![screenshot_5_2.png](docs/screenshot_5_2.png)

Если проект использует несколько `DocumentView`, то удобнее создать один список шрифтов и использовать его для всех создаваемых виджетов:

```kotlin
val fontList = FontList()
fontList["georgia"] = Font(Typeface.createFromAsset(this.assets, "fonts/georgia.ttf")!!)
fontList["georgia:bold"] = Font(Typeface.createFromAsset(this.assets, "fonts/georgiab.ttf")!!)
fontList["georgia:italic"] = Font(Typeface.createFromAsset(this.assets, "fonts/georgiai.ttf")!!)
fontList["georgia:bold_italic"] = Font(Typeface.createFromAsset(this.assets, "fonts/georgiaz.ttf")!!)

docView.fontList = fontList
docView.document.characterStyle.font = "georgia"

docView.document.setText("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed " +
        "do eiusmod tempor incididunt ut labore et dolore magna aliqua.")
docView.document
        .addWordSpan(6, 8, CharacterStyle(bold = true))
        .addWordSpan(9, -1, CharacterStyle(italic = true))
```

![screenshot_6.png](docs/screenshot_6.png)

## Рамки

Абзац можно оформить рамкой:

```kotlin
docView.document.setText("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed " +
        "do eiusmod tempor incididunt ut labore et dolore magna aliqua.\n" +
        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut " +
        "aliquip ex ea commodo consequat.\n" +
        "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore " +
        "eu fugiat nulla pariatur.")
docView.document[0].borderStyle
        .setPadding(Size.dp(8f))
        .setBorder(Border.dp(1f, Color.rgb(0xDC3023)))
        .setMargin(Size.dp(8f))
        .setBackgroundColor(Color.argb(0.1f, 0xDC3023))
docView.document[1].borderStyle
        .setPadding(Size.dp(8f))
        .setBorder(Border.dp(1f, Color.rgb(0x22A7F0)))
        .setMargin(Size.dp(8f))
        .setBackgroundColor(Color.argb(0.1f, 0x22A7F0))
docView.document[2].borderStyle
        .setPadding(Size.dp(8f))
        .setBorder(Border.dp(1f, Color.rgb(0x26C281)))
        .setMargin(Size.dp(8f))
        .setBackgroundColor(Color.argb(0.1f, 0x26C281))
```

![screenshot_7.png](docs/screenshot_7.png)

В примере используется класс `Color` из модуля для работы с цветом `color` ([wiki][color]). Это не [android.graphics.Color](https://developer.android.com/reference/android/graphics/Color), хотя можно использовать и его. И в том, и в другом случае цвет хранится в обычном `Int`.

Рамки могут быть разными. Сам документ также может иметь отдельную рамку:

```kotlin
docView.document[0].borderStyle
        .setPadding(Size.dp(8f))
        .setBorderTop(Border.dp(8f, Color.rgb(0xDC3023)))
        .setBorderRight(Border.dp(8f, Color.rgb(0x22A7F0)))
        .setBorderBottom(Border.dp(8f, Color.rgb(0x26C281)))
        .setBorderLeft(Border.dp(8f, Color.rgb(0x9B59B6)))
        .setMargin(Size.dp(4f))
        .setBackgroundColor(Color.argb(0.2f, 0xDC3023))
docView.document[1].borderStyle
        .setPadding(Size.dp(8f))
        .setBorderLeft(Border.dp(8f, Color.rgb(0x22A7F0)))
        .setMargin(Size.dp(4f))
        .setBackgroundColor(Color.argb(0.2f, 0x22A7F0))
docView.document[2].borderStyle
        .setPadding(Size.dp(8f))
        .setBorder(
                Border.dp(8f, Color.TRANSPARENT),
                Border.dp(8f, Color.rgb(0x26C281)))
        .setMargin(Size.dp(4f))
        .setBackgroundColor(Color.argb(0.2f, 0x26C281))

docView.document.borderStyle
        .setPadding(Size.dp(4f))
        .setBorder(Border.dp(4.0f, Color.rgb(0xF9690E)))
        .setMargin(Size.dp(4f))
        .setBackgroundColor(Color.argb(0.1f, 0xF9690E))
```

![screenshot_7_2.png](docs/screenshot_7_2.png)

Поддержка разноцветных рамок сделала для согласования с HTML. Но на данном этапе рамки рисуются попиксельно **вручную**, т.к. пока не нашлось адекватного уже существующего метода с аппаратной поддержкой, чтобы сделать плавные переходы между цветами с учётом сглаживания (antialias) и альфа-канала у цветов (`drawPath()` работает только с одним цветом, drawVertices() работает только в SOFTWARE и не умеет сглаживать линии). Собственный же метод умеет и сглаживать рамки (antialias), и более менее адекватно смешивать цвета (см. в ([`Color`][color] ф-ю `mix()`). Но он значительно увеличивает время прорисовки виджета при толстых рамках. В будущем, видимо, придётся пойти на компромиссы и от чего-то отказаться ради увеличения скорости, если, конечно, не удастся найти что-то адекватное. (Не очень хочется переходить ради одних только рамок на OpenGL, хотя...)

## Оформление абзацев

Для оформления абзацев есть: отступы сверху и снизу (`spaceBefore`, `spaceAfter`), выравнивание (`align`) по левому краю, по правому, по ширине и относительно центра, отступы слева и справа (`leftIndent`, `rightIndent`), отдельное выравнивание и отступы для первой строки (`firstAlign`, `firstLeftIndent`, `firstRightIndent`), отдельное выравнивание для последней строки (`lastAlign`).

```kotlin
docView.document.setText("Lorem ipsum\n" +
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod " +
        "tempor incididunt ut labore et dolore magna aliqua.\n" +
        "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi " +
        "ut aliquip ex ea commodo consequat.\n" +
        "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore " +
        "eu fugiat nulla pariatur.\n" +
        "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia " +
        "deserunt mollit anim id est laborum.")

docView.document.characterStyle
        .setSize(Size.em(1.2f))
docView.document.paragraphStyle
        .setSpaceBefore(Size.dp(8f)) // Отступ сверху, общий для всех абзацев

docView.document[0].characterStyle
        .setSize(Size.em(2f))
docView.document[0].paragraphStyle
        .setAlign(ParagraphStyle.Align.CENTER)
        .setSpaceBefore(Size.dp(0f))
docView.document[0].borderStyle
        .setBorderBottom(Border.dp(1f, Color.LTGRAY))
        .setMarginBottom(Size.dp(4f))
docView.document[1].paragraphStyle
        .setAlign(ParagraphStyle.Align.LEFT)
        .setFirstLeftIndent(Size.em(2f))
docView.document[2].paragraphStyle
        .setAlign(ParagraphStyle.Align.RIGHT)
docView.document[3].paragraphStyle
        .setAlign(ParagraphStyle.Align.JUSTIFY)
docView.document[4].paragraphStyle
        .setAlign(ParagraphStyle.Align.JUSTIFY)
        .setLastAlign(ParagraphStyle.Align.CENTER)
```

![screenshot_8.png](docs/screenshot_8.png)

Пример оформления стихотворения:

```kotlin
docView.document.setText("Куда ещё тянется провод\u2028из гроба того?\n" +
        "Нет, Сталин не умер. Считает он смерть поправимостью.\n" +
        "Мы вынесли из мавзолея его.\n" +
        "Но как из наследников Сталина Сталина вынести?")

docView.document.characterStyle
        .setFont("georgia")
        .setItalic(true)

docView.document.paragraphStyle
        .setLeftIndent(Size.em(2f))
        .setFirstLeftIndent(Size.em(-2f))
        .setFirstAlign(ParagraphStyle.Align.LEFT)
        .setAlign(ParagraphStyle.Align.RIGHT)
```

![screenshot_8_2.png](docs/screenshot_8_2.png)

Заодно в этом примере используется символ разрыва строки `'\u2028'` без разделения абзацев.

## Коррекция шрифтов

При совместном использовании нескольких шрифтов может возникнуть проблема соотношения реальных размеров знаков для одного и того же кегля:

```kotlin
docView.fontList.createFamily("serif", Font(Typeface.SERIF))
docView.fontList["ponomar"] = Font(
        typeface = Typeface.createFromAsset(this.assets, "fonts/PonomarUnicode.ttf")!!)

docView.document.setText("В начале сотворил Бог - Въ нача́лѣ сотворѝ бг҃ъ")
docView.document.addWordSpan(1, 4, CharacterStyle(font = "serif"))
docView.document.addWordSpan(5, -1, CharacterStyle(font = "ponomar"))
```

![screenshot_9.png](docs/screenshot_9.png)

Можно, конечно, в каждом случае вручую приводить нужный текст к требуемому размеру, а можно скорректировать весть шрифт ещё на этапе его загрузки, задав ему масштаб:

```kotlin
docView.fontList["ponomar"] = Font(
        typeface = Typeface.createFromAsset(this.assets, "fonts/PonomarUnicode.ttf")!!,
        scale = 1.2f)
```

![screenshot_9_2.png](docs/screenshot_9_2.png)

Следующей проблемой может оказаться, как в данном случае, слишком большое или слишком маленькое расстояние между строками *(старославянский шрифт требует больше места из-за обилия в языке диакритических знаков)*. Это тоже можно исправить, указав нужные множители для коррекции верхнего (`ascent`) и нижнего (`descent`) отступов шрифта:

```kotlin
docView.fontList["ponomar"] = Font(
        typeface = Typeface.createFromAsset(this.assets, "fonts/PonomarUnicode.ttf")!!,
        ascentRatio = 0.8f,
        descentRatio = 0.8f,
        scale = 1.2f)
```

![screenshot_9_3.png](docs/screenshot_9_3.png)

## Переносы слов

Автоматические разбивка слов для переносов по слогам пока не реализована. Но можно задействовать мягкие переносы (`'\u00AD'`), вручную указав их в тексте. Пример из [Оформления абзацев](#Оформление-абзацев), но с мягкими переносами:

```kotlin
// Мягкие переносы ля наглядности обозначаем знаком '~', затем их переводим в '\u00AD'
val string = "Lorem ipsum\n" +
        "Lo~rem ip~sum do~lor sit amet, con~sec~te~tur adi~pis~cing elit, sed do " +
        "eius~mod tem~por in~ci~di~dunt ut la~bo~re et do~lo~re mag~na ali~qua.\n" +
        "Ut enim ad mi~nim ve~niam, qu~is nos~t~rud exer~ci~ta~tion ul~lam~co la~bo~ris " +
        "ni~si ut ali~qu~ip ex ea com~mo~do con~se~quat.\n" +
        "Duis aute iru~re do~lor in rep~re~hen~de~rit in vo~lup~ta~te ve~lit es~se " +
        "cil~lum do~lo~re eu fu~gi~at nul~la pa~ria~tur.\n" +
        "Ex~cep~te~ur sint oc~cae~cat cu~pi~da~tat non pro~i~dent, sunt in cul~pa qui " +
        "of~fi~cia de~se~runt mol~lit anim id est la~bo~rum."
docView.document.setText(string.replace('~', '\u00AD'))
```

![screenshot_10.png](docs/screenshot_10.png)

В некоторых языках (например, в старославянском) используется символ переноса, отличный от стандартного. Изменить это можно при загрузке шрифта:

```kotlin
docView.fontList["ponomar"] = Font(
        typeface = Typeface.createFromAsset(this.assets, "fonts/PonomarUnicode.ttf")!!,
        hyphen = '_', // Символ переноса для старославянского языка
        ascentRatio = 0.9f,
        descentRatio = 0.9f,
        scale = 1.2f)

val text = "Прї~и~ди́~те ко мнѣ̀ всѝ трꙋж~да́ю~щї~и~сѧ и҆ ѡ҆б~ре~ме~не́н~нїи, и҆ а҆́зъ " +
        "оу҆по~ко́ю вы̀. Воз̾~ми́~те и҆́го моѐ на се~бѐ, и҆ на~ꙋ~чи́~те~сѧ ѿ ме~нѐ, ꙗ҆́кѡ " +
        "кро́~токъ є҆́смь и҆ сми~ре́нъ се́рд~цемъ, и҆ ѡ҆б~рѧ́~ще~те по~ко́й дꙋ~ша́мъ " +
        "ва́~шымъ. И҆́го бо моѐ бла́~го, и҆ бре́~мѧ моѐ лег~ко̀ є҆́сть."
docView.document.setText(text.replace('~', '\u00AD'))
docView.document.characterStyle.font = "ponomar"
docView.document.paragraphStyle
        .setFirstLeftIndent(Size.em(1f))
        .setAlign(ParagraphStyle.Align.JUSTIFY)
docView.document.addSpan(0, 1, CharacterStyle(color = Color.RED))
```

![screenshot_10_2.png](docs/screenshot_10_2.png)

## Базовые линии

В `DocumentView` можно включить режим, показывающий базовые линии:

```kotlin
val string = "Lo~rem ip~sum do~lor sit amet, con~sec~te~tur adi~pis~cing elit, " +
        "sed do eius~mod tem~por in~ci~di~dunt ut la~bo~re et do~lo~re mag~na ali~qua."
docView.document.setText(string.replace('~', '\u00AD'))

docView.baselineMode = DocumentView.Baseline.PARAGRAPH
```

![screenshot_11.png](docs/screenshot_11.png)

Базовые линии рассчитываются автоматически по максимальным размерам задействованных в строке символов с учётом их смещений относительно базовой линии (`baselineShift`):

```kotlin
val string = "Lo~rem ip~sum do~lor sit amet, con~sec~te~tur adi~pis~cing elit, " +
        "sed do eius~mod tem~por in~ci~di~dunt ut la~bo~re et do~lo~re mag~na ali~qua.\n" +
        "Ut enim ad mi~nim ve~niam, qu~is nos~t~rud exer~ci~ta~tion ul~lam~co la~bo~ris " +
        "ni~si ut ali~qu~ip ex ea com~mo~do con~se~quat.\n" +
        "Duis1 aute2 iru~re3 do~lor4 in5 rep~re~hen~de~rit6 in7 vo~lup~ta~te8 " +
        "ve~lit9 es~se10 cil~lum11 do~lo~re12 eu13 fu~gi~at14 nul~la15 " +
        "pa~ria~tur16.\n" +
        "Ex~cep~te~ur sint oc~cae~cat cu~pi~da~tat non pro~i~dent1, sunt in cul~pa* qui " +
        "of~fi~cia de~se~runt mol~lit anim2 id est la~bo~rum."
docView.document.setText(string.replace('~', '\u00AD'))

docView.baselineMode = DocumentView.Baseline.VIEW
docView.baselineColor = Color.rgb(0x4B77BE)

docView.document.borderStyle
        .setPadding(Size.dp(0f), Size.dp(8f))
        .setBorder(Border.px(1.0f, Color.BLACK))
        .setMargin(Size.dp(4f))

docView.document.characterStyle
        .setSize(Size.sp(18f))
docView.document.paragraphStyle
        .setAlign(ParagraphStyle.Align.JUSTIFY)
        .setFirstLeftIndent(Size.dp(24f))

docView.document[1]
        .addWordSpan(10, CharacterStyle(
                size = Size.em(1.4f)))
docView.document[2]
        .addSpan(Regex("""\d+"""), CharacterStyle(
                baselineShift = Size.em(0.33f),
                size = Size.em(0.58f)))
docView.document[3]
        .addSpan(Regex("""\*|\d"""), CharacterStyle(
                baselineShift = Size.em(-0.5f),
                size = Size.em(0.58f)))
```

![screenshot_11_2.png](docs/screenshot_11_2.png)

Это удобно, но, как видно на этом примере, не всегда выглядит красиво - расстояние между некоторыми строками увеличилось (отмечены красным). Чтобы исправить верхние и нижние индексы, можно подобрать экспериментальным путём размер символов и смещение базовой линии. А можно с помощью свойства `verticalAlign` выравнять их по верхней или нижней границе базового шрифта, тогда они точно не выйдут за границы и не приведут к увеличению строки:

```kotlin
docView.document[2]
        .addSpan(Regex("""\d+"""), CharacterStyle(
                verticalAlign = CharacterStyle.VAlign.BOTTOM,
                size = Size.em(0.58f)))
docView.document[3]
        .addSpan(Regex("""\*|\d"""), CharacterStyle(
                verticalAlign = CharacterStyle.VAlign.TOP,
                size = Size.em(0.58f)))
```

![screenshot_11_3.png](docs/screenshot_11_3.png)

Для верхнего индекса получилось очень хорошо, зато нижний индекс поднялся слишком высоко. И такой компромисс может нас не устроить. Можно опустить ниже. Значение `VAlign.BOTTOM` служит для выравнивания нижней границы символа по нижней границе базового шрифта. Значение `VAlign.BASELINE_TO_BOTTOM` выравнивает базовую линию символа по границе базового шрифта. Но разумеется, расстояние между строками снова увеличится:

```kotlin
docView.document[2]
        .addSpan(Regex("""\d+"""), CharacterStyle(
                verticalAlign = CharacterStyle.VAlign.BASELINE_TO_BOTTOM,
                size = Size.em(0.58f)))
```

![screenshot_11_4.png](docs/screenshot_11_4.png)

И что-то всё ещё нужно сделать с увеличенным шрифтом во втором абзаце. Можно убрать эти участки вообще из рассчёта размера строки, установив интерлиньяж равным `0`:

```kotlin
docView.document[1]
        .addWordSpan(10, CharacterStyle(
                size = Size.em(1.4f),
                leading = Size.dp(0f)))
docView.document[2]
        .addSpan(Regex("""\d+"""), CharacterStyle(
                verticalAlign = CharacterStyle.VAlign.BASELINE_TO_BOTTOM,
                size = Size.em(0.58f),
                leading = Size.dp(0f)))
```

![screenshot_11_5.png](docs/screenshot_11_5.png)

Только бы не оказалось так, что в строке не окажется ни одного символа с ненулевым размером! Строки слипнутся! Избежать этого можно, установив интерлиньяж равным высоте базового шрифта через `Size.ratio()`. Если в абзацных отступах `ratio` это доля от ширины родительской секции, в шрифтах - доля от кегля базового шрифта (тоже самое, что `em`), то при вычислении интерлиньяжа `ratio` это доля от высоты (не кегля!) базового (а не текущего!) шрифта (`em` и `fh` будут вычисляться от размера текущего шрифта):

```kotlin
docView.document[1]
        .addWordSpan(10, CharacterStyle(
                size = Size.em(1.4f),
                leading = Size.ratio(1f)))
docView.document[2]
        .addSpan(Regex("""\d+"""), CharacterStyle(
                verticalAlign = CharacterStyle.VAlign.BASELINE_TO_BOTTOM,
                size = Size.em(0.58f),
                leading = Size.ratio(1f)))
```

Расстояние между строками получилось одинаковым. Если в нижнем индексе не будут использоваться символы, спускающиеся ниже базовой линии, то получим качественный результат. Зато увеличенный шрифт не пересёкся с соседними строками по чистой случайности. Но в этом случае остаётся только немного увеличить расстояние между строками во всём тексте, что, впрочем, всегда выглядит хорошо:

```kotlin
docView.document.characterStyle.leading = Size.fh(1.15f)
```

![screenshot_11_6.png](docs/screenshot_11_6.png)

Обратите внимание! Интерлиньяж не влияет на первую строку в тексте, что кажется вполне логичным для строки, перед которой нет другой строки. Первая базовая линия всегда рассчитывается по размерам знаков, находящихся в первой строке. А это может испортить вид, если вы хотите поставить рядом два текста, рассчитывая, что их базовые линии будет совпадать:

```kotlin
docView.document[0]
        .addSpan(0, 1, CharacterStyle(
                size = Size.em(1.4f),
                leading = Size.ratio(1f)))
```

![screenshot_11_7.png](docs/screenshot_11_7.png)

Интерлиньяж `leading = Size.ratio(1f)` для первой строки не работает! Хотя, если принудительно установить первую базовую строку на начало секции, можно получить синхронное отображение:

```kotlin
docView.document.setFirstBaselineToTop = true
```

![screenshot_11_8.png](docs/screenshot_11_8.png)

## Секции

Чуть позже.
