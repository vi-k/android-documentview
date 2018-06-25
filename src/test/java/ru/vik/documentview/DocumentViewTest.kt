package ru.vik.documentview

import org.junit.Test

import ru.vik.colorlib.ColorLib
import java.util.logging.Logger

class DocumentViewTest {
    @Test
    fun test() {
        val log = Logger.getLogger("Test")

        val color1 = ColorLib.argb(48, 255, 128, 0)
        val color2 = ColorLib.argb(0, 0, 0, 0)
        val color = ColorLib.layer(color2, color1)

        var a = ColorLib.a(color)
        var r = ColorLib.r(color)
        var g = ColorLib.g(color)
        var b = ColorLib.b(color)

//        assertEquals(DocumentView.argb(208, 176, 167, 39), color)

//        for (i in 0..255 * 255) {
//            assertEquals(i / 255, (i + 1 + (i ushr 8)) ushr 8)
//            assertEquals(i.toString(), (i + 127) / 255, (i + 128 + ((i + 127) ushr 8)) ushr 8)
//        }

        val t = System.currentTimeMillis()
        loop@ for (n in 0 .. 50)
        for (c1 in 1..255 step 2) {
            for (c2 in 1..255 step 2) {
                val rgb1 = (c1 shl 16) or (c1 shl 8) or c1
                val rgb2 = (c2 shl 16) or (c2 shl 8) or c2
                for (a1 in 1..255 step 2) {
                    for (a2 in 1..255 step 2) {
                        val argb1 = (a1 shl 24) or rgb1
                        val argb2 = (a2 shl 24) or rgb2

//                        val a1 = DocumentView.a(argb1)
//                        val a2 = DocumentView.a(argb2)
//                        val a = a1 + a2 - DocumentView.div255(a1 * a2)

                        ColorLib.layer(argb1, argb2)
//                        val mix1 = DocumentView.mix(argb1, argb2)
//                        val mix2 = DocumentView.colorMixD(argb1, argb2)
//                        if (Math.abs(DocumentView.a(mix1) - DocumentView.a(mix2)) > 1 ||
//                            Math.abs(DocumentView.r(mix1) - DocumentView.r(mix2)) > 5 ||
//                            Math.abs(DocumentView.g(mix1) - DocumentView.g(mix2)) > 5 ||
//                            Math.abs(DocumentView.b(mix1) - DocumentView.b(mix2)) > 5) {
//                            log.info(String.format("color1=%s color2=%s mix1=%s min2=%s",
//                                    DocumentView.colorToString(argb1),
//                                    DocumentView.colorToString(argb2),
//                                    DocumentView.colorToString(mix1),
//                                    DocumentView.colorToString(mix2)))
//                            break@loop
//                        }
                    }
                }
            }
        }
        log.info(String.format("%.1f", (System.currentTimeMillis() - t) / 1000f))

//        val v = 510
//        assertEquals(2, (v + 127) / 255)
//        assertEquals(2, (v + 1 + (v ushr 8)) ushr 8)
    }
}