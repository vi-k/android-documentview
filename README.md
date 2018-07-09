# DocumentView

Виджет на Kotlin для Android для вывода отформатированного текста. Замена [TextView](https://developer.android.com/reference/android/widget/TextView). Наследует от [View](https://developer.android.com/reference/android/view/View).

Изначально создавался для вывода HTML. Для моих задач обычный TextView с его [fromHtml()](https://developer.android.com/reference/android/text/Html.html#fromHtml(java.lang.String,%20int)) оказался недостаточным. Во-первых, не доставало растягивания по ширине (justification появился в API 26, а я ориентировался на API 15). Во-вторых, перенос строк на мягких переносах работал как-то уж совсем произвольно (то переносит, то не переносит, логики не увидел). В-третьих, HTML-код в моём головном проекте поставляется пользователем, и мне, с одной стороны, хотелось полностью контролировать, что будет в этом HTML, ограничивая пользователя от лишнего, а с другой стороны, наоборот, хотелось добавить возможности (дополнительные теги и аттрибуты), которых нет в обычном HTML.

Первый [проект](https://github.com/vi-k/android-html2spannable) свёлся к тому, что я сам парсил HTML и затем формировал из него [Spannable](https://developer.android.com/reference/android/text/SpannableStringBuilder). Столкнувшись с некоторыми ограничениями, решил сделать отдельный свой собственный ~велосипед~ класс [Document](https://github.com/vi-k/kotlin-utils/tree/master/src/main/java/ru/vik/utils/document), хранящий в себе форматированный документ. Причём сделать его не зависящим ни от Android SDK (прогнозируемая многоплатформенность Kotlin заставляет думать наперёд), ни от HTML. Парсинг HTML отдан отдельному классу [BaseHtml](https://github.com/vi-k/kotlin-utils/tree/master/src/main/java/ru/vik/utils/html) и его наследникам. Преобразование результата парсинга в Document отдано классу [BaseHtmlDocument](https://github.com/vi-k/kotlin-utils/tree/master/src/main/java/ru/vik/utils/htmldocument) и его наследникам.

Описание указанных классов смотрите здесь: <https://github.com/vi-k/kotlin-utils>.

Пример работы с виджетом здесь: <https://github.com/vi-k/android-DocumentViewSample>.

Работа только начата. Ещё многое предстоит сделать. Но простое форматирование текста уже доступно. Ссылки, изображения, таблицы пока не доступны.

## Пример использования

Layout:
```xml
<ScrollView
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <ru.vik.documentview.DocumentView
        android:id="@+id/docView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

</ScrollView>
```

MainActivity.kt:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

        val docView: DocumentView = findViewById(R.id.docView)

        val fontList = FontList()
        fontList.createFamily("sans_serif", Font(Typeface.SANS_SERIF))
        fontList.createFamily("serif", Font(Typeface.SERIF))
        fontList.createFamily("mono", Font(Typeface.MONOSPACE))

        docView.fontList = fontList

        docView.characterStyle.font = "sans_serif"
        docView.characterStyle.size = Size.dp(16f)
        docView.paragraphStyle.firstLeftIndent = Size.dp(32f)

        val document = SimpleHtmlDocument()
        docView.document = document

        document.blockStyle.setPadding(Size.dp(4f))

        val testString = "Нормальный, <b>полужирный</b>, <i>курсив</i>, <u>подчёркнутый</u>, <s>зачёркнутый</s>, верхний<sup>индекс</sup>, нижний<sub>индекс</sub>."
        val testString2 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."

        document.setText("<h1>DocumentView Sample</h1>\n" +
                "<p>$testString</p>\n" +
                "<p font='serif'>$testString</p>\n" +
                "<p font='mono'>$testString</p>\n" +
                "<p align='justify'>$testString2</p>")
}
```
