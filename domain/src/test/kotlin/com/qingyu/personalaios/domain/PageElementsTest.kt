package com.qingyu.personalaios.domain

import org.junit.Assert.*
import org.junit.Test

class PageElementsTest {
    @Test
    fun solidTapePaintsNothing() {
        assertTrue(tapeOps(TapePattern.SOLID).isEmpty())
    }

    @Test
    fun everyPatternPaintsInsideItsOwnBox() {
        TapePattern.entries.filter { it != TapePattern.SOLID }.forEach { pattern ->
            val ops = tapeOps(pattern)
            assertTrue(pattern.name, ops.isNotEmpty())
            ops.forEach { op ->
                assertTrue("$pattern $op", op.x >= 0f && op.y >= 0f)
                assertTrue("$pattern $op", op.x + op.width <= 1.0001f && op.y + op.height <= 1.0001f)
                assertTrue("$pattern $op", op.alpha in 0f..1f)
            }
        }
    }

    @Test
    fun stripesAndChecksCrossTheWholeWidth() {
        assertTrue(tapeOps(TapePattern.STRIPE).all { it.mark == TapeMark.BAND && it.height == 1f })
        assertEquals(4, tapeOps(TapePattern.CHECK).count { it.width < 1f })
        assertEquals(3, tapeOps(TapePattern.CHECK).count { it.width == 1f })
    }

    @Test
    fun collageSlotsFillTheGridEvenly() {
        val slots = collageSlots(4, 2, 2)
        assertEquals(4, slots.size)
        assertEquals(CollageSlot(.05f, .05f, .435f, .435f), slots.first())
        assertEquals(.515f, slots.last().x, 1e-6f)
        assertEquals(.515f, slots.last().y, 1e-6f)
        slots.forEach { assertTrue(it.x + it.width <= .9501f && it.y + it.height <= .9501f) }
    }

    @Test
    fun collageSlotsStayLegalForEveryRequest() {
        (0..12).forEach { count ->
            (1..6).forEach { columns ->
                (1..6).forEach { rows ->
                    val slots = collageSlots(count, columns, rows)
                    assertTrue(slots.size <= count)
                    slots.forEach { assertTrue("$count $columns $rows $it", it.width >= .08f && it.height >= .08f) }
                }
            }
        }
    }

    @Test
    fun collageSlotsClipToTheAvailableCells() {
        assertEquals(9, collageSlots(20, 3, 3).size)
        assertEquals(1, collageSlots(1, 1, 1).size)
        assertTrue(collageSlots(0, 2, 2).isEmpty())
    }

    @Test
    fun stickerFilesAreStableAndDistinct() {
        assertEquals(StickerArt.entries.size, StickerArt.entries.map { stickerFile(it) }.distinct().size)
        assertEquals("sticker-s00_00.png", stickerFile(StickerArt.S00_00))
        assertTrue(StickerArt.entries.all { it.label.isNotBlank() })
    }

    @Test
    fun tapeAndTextElementsDoNotNeedAFile() {
        val tape = DiarySticker(id = "t", path = "", kind = StickerKind.TAPE, x = .1f, y = .1f, width = .5f, height = .2f)
        val text = DiarySticker(id = "x", path = "", kind = StickerKind.TEXT, x = .1f, y = .1f, width = .5f, height = .2f)
        assertEquals(TapePattern.STRIPE, tape.tape)
        assertEquals(1f, text.alpha, 0f)
        assertThrows(IllegalArgumentException::class.java) {
            DiarySticker(id = "i", path = "", kind = StickerKind.IMAGE, x = .1f, y = .1f, width = .5f, height = .2f)
        }
    }

    @Test
    fun elementFieldsAreRangeChecked() {
        assertThrows(IllegalArgumentException::class.java) {
            DiarySticker(id = "t", path = "", kind = StickerKind.TAPE, x = .9f, y = .1f, width = .5f, height = .2f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiarySticker(id = "x", path = "", kind = StickerKind.TEXT, x = .1f, y = .1f, width = .5f, height = .2f, alpha = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DiarySticker(id = "x", path = "", kind = StickerKind.TEXT, x = .1f, y = .1f, width = .5f, height = .2f, fontSize = .5f)
        }
    }
}
