package com.qingyu.personalaios.feature.diary

import com.qingyu.personalaios.domain.DiarySticker
import com.qingyu.personalaios.domain.StickerKind
import org.junit.Assert.*
import org.junit.Test

class PageElementUiTest {
    @Test
    fun alphaLadderWalksAndWraps() {
        assertEquals(.85f, nextAlpha(1f), 0f)
        assertEquals(.45f, nextAlpha(.65f), 0f)
        assertEquals(1f, nextAlpha(.25f), 0f)
        assertEquals(1f, nextAlpha(.5f), 0f)
    }

    @Test
    fun colourLadderWalksAndWraps() {
        assertEquals(0xFFB4553F, nextInkColor(0xFF243749))
        assertEquals(0xFF243749, nextInkColor(0xFFC08A2E))
        assertEquals(0xFF243749, nextInkColor(0xFF123456))
    }

    @Test
    fun elementNamesCoverEveryKind() {
        val names = listOf(
            elementName(DiarySticker(id = "a", path = "a.png", kind = StickerKind.IMAGE, x = .1f, y = .1f, width = .3f, height = .2f)),
            elementName(DiarySticker(id = "b", path = "b.png", kind = StickerKind.STICKER, x = .1f, y = .1f, width = .3f, height = .2f)),
            elementName(DiarySticker(id = "c", path = "", kind = StickerKind.TAPE, x = .1f, y = .1f, width = .3f, height = .2f)),
            elementName(DiarySticker(id = "d", path = "", kind = StickerKind.TEXT, x = .1f, y = .1f, width = .3f, height = .2f)),
        )
        assertEquals(listOf("贴图", "贴纸", "胶带", "文字"), names)
        assertEquals(names.size, names.distinct().size)
    }
}
