# DocumentView

DocumentView это Kotlin-виджет для Android для вывода отформатированного текста. Аналог [TextView](https://developer.android.com/reference/android/widget/TextView) из Android SDK с его [Spannable](https://developer.android.com/reference/android/text/Spannable), но с более широкими (в планах!) возможностями в части форматирования текста.

Работа только начата. Пока доступно только самое простое форматирование текста, но уже есть выравнивание по ширине (justification) и обработка мягких переносов.

## Содержание
- Простой пример
- Шрифты

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

    docView.document.setText("Нормальный, полужирный, курсив, полужирный курсив, " +
            "подчёркнутый, зачёркнутый, верхнийиндекс, нижнийиндекс, красный.")
    docView.document
            .add(Span(CharacterStyle(bold = true), 12, 22))
            .add(Span(CharacterStyle(italic = true), 24, 30))
            .add(Span(CharacterStyle(bold = true, italic = true), 32, 49))
            .add(Span(CharacterStyle(underline = true), 51, 63))
            .add(Span(CharacterStyle(strike = true), 65, 76))
            .add(Span(CharacterStyle(
                    baselineShift = Size.em(-0.4f),
                    size = Size.em(0.85f)), 85, 91))
            .add(Span(CharacterStyle(
                    baselineShift = Size.em(0.25f),
                    size = Size.em(0.85f)), 99, 105))
            .add(Span(CharacterStyle(color = Color.RED), 107, 114))
}
```

![screenshot_1.png](docs/screenshot_1.png)

Виджет содержит внутри себя объект платформонезависимого класса [Document], который хранит в себе форматируемый документ. С ним мы и работаем, добавляя участки форматирования. Класс Span хранит в себе стиль знаков, начало и конец форматирования.

Участки могут пересекаться:

```kotlin
    docView.document.setText("Полужирный, полужирный с курсивом, курсив.")
    docView.document
            .add(Span(CharacterStyle(bold = true), 0, 33))
            .add(Span(CharacterStyle(italic = true), 12, 41))
```

![screenshot_2.png](docs/screenshot_2.png)

## Шрифты

Создаём шрифты, которые будем использовать:
```kotlin
val fontList = FontList()
fontList.createFamily("sans_serif", Font(Typeface.SANS_SERIF))
fontList.createFamily("serif", Font(Typeface.SERIF))
fontList.createFamily("mono", Font(Typeface.MONOSPACE))
docView.fontList = fontList
```

Функция `createFamily()` создаёт сразу 4 шрифта для разных начертаний: нормального, **полужирного**, *курсива* и ***полужирного вместе с курсивом***. Это имеет смысл только для встроенных шрифтов. Для пользовательских шрифтов все файлы с начертаниями необходимо загрузить отдельно. Как это сделать, смотрите в [документации](https://github.com/vi-k/android-documentview/wiki/Установка шрифтов). Если шрифт не имеет отдельных файлов для отдельных начертаний, то ничего загружать не надо, полужирный и курсив будут создаваться автоматически при выводе.

Настраиваем параметры по-умолчанию:
```kotlin
docView.characterStyle.font = "sans_serif"
docView.characterStyle.size = Size.dp(16f)
docView.paragraphStyle.firstLeftIndent = Size.dp(32f)
```

Устанавливаем шрифт по-умолчанию, базовый размер шрифта и отступ для первой строки. Стили characterStyle и paragraphStyle описаны в документации к [Document].

Создаём объект класса [Document], отвечающий за форматирование документа. В данном случае используем уже готовый класс преобразователь из HTML в [Document].
```kotlin
val document = SimpleHtmlDocument()
docView.document = document
```

[DocumentView] не связан напрямую с HTML. Поэтому в этом месте может оказаться и какой-нибудь другой класс-преобразователь. Например, (в каком-нибудь будущем) PlainTextDocument и MarkdownDocument.

Устанавливаем отступы от краёв:
```kotlin
document.blockStyle.setPadding(Size.dp(4f))
```

Отступы можно сделать и обычным способом через свойства View `paddingLeft`, `paddingTop` и т.п., но с той разницей, что в этом случае значение будет указываться в пикселях устройства, в то время как blockStyle принимает значение в пикселях, не зависящих от устройства (device-independent pixels). Также можно указать значение, пропорциональное или размеру шрифта (`Size.em()`) или ширине виджета (`Size.percent()` или `Size.ratio()`).

Подробное описание возможностей классов смотрите в документации:
1) [DocumentView].
2) [Document].
3) [Html].
4) [HtmlDocument].

[DocumentView]:https://github.com/vi-k/android-documentview/wiki
[Document]:https://github.com/vi-k/kotlin-utils/wiki/document
[Html]:https://github.com/vi-k/kotlin-utils/wiki/html
[HtmlDocument]:https://github.com/vi-k/kotlin-utils/wiki/htmldocument
