package com.qingyu.personalaios.domain

/** Photos form the base; stickers, tape and text retain their own top layer.
 * Stable grouping preserves order within each group without rewriting saved data. */
object PaperLayers {
    fun base(elements:List<DiarySticker>)=elements.filter { it.kind==StickerKind.IMAGE }
    fun top(elements:List<DiarySticker>)=elements.filter { it.kind!=StickerKind.IMAGE }
    fun ordered(elements:List<DiarySticker>)=base(elements)+top(elements)
}
