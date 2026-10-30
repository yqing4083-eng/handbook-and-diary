package com.qingyu.personalaios.domain

/** Positioned paragraphs use the existing rich-text column, with readable HTML
 * fallback for export. Coordinates refer to physical paper rows and width. */
data class RuledParagraph(val id:String,val row:Int,val x:Float,val text:String) {
    init { require(row in 2..10000 && x.isFinite() && x in .065f.. .87f && text.length<=100000) }
}
object RuledText {
    private const val Prefix="<div data-doki-ruled=\"1\""
    private val paragraph=Regex("<p data-id=\"([a-zA-Z0-9_-]+)\" data-row=\"(\\d+)\" data-x=\"([0-9.]+)\">([\\s\\S]*?)</p>")
    private fun escape(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br/>")
    private fun unescape(s:String)=s.replace("<br/>","\n").replace("&gt;",">").replace("&lt;","<").replace("&amp;","&")
    fun titleX(html:String)=Regex("data-title-x=\"([0-9.]+)\"").find(html)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it in .065f.. .87f } ?: .065f
    fun encode(blocks:List<RuledParagraph>,titleX:Float=.065f,document:RuledDocument?=null,titleInk:List<RuledGlyph> = emptyList())=Prefix+" data-title-x=\"${titleX.coerceIn(.065f,.87f)}\""+(document?.let {
        " data-flow-text=\"${java.util.Base64.getEncoder().encodeToString(it.text.toByteArray(Charsets.UTF_8))}\""
    } ?: "")+" data-flow-ink=\"${RuledInkCodec.encode(document?.glyphs ?: emptyList())}\" data-title-ink=\"${RuledInkCodec.encode(titleInk)}\""+">"+(document?.anchors?.joinToString("") {
        require(it.id.matches(Regex("[a-zA-Z0-9_-]+")) && it.start in 0..document.text.length && it.x in .065f.. .87f)
        "<a data-start=\"${it.start}\" data-x=\"${it.x}\" data-id=\"${it.id}\"></a>"
    } ?: "")+blocks.joinToString("") {
        require(it.id.matches(Regex("[a-zA-Z0-9_-]+")))
        "<p data-id=\"${it.id}\" data-row=\"${it.row}\" data-x=\"${it.x}\">${escape(it.text)}</p>"
    }+"</div>"
    /** Semantic text survives reflow without saving layout gaps as spaces/newlines. */
    fun document(html:String):RuledDocument?=runCatching {
        val encoded=Regex("data-flow-text=\"([^\"]*)\"").find(html)?.groupValues?.get(1) ?: return null
        val text=String(java.util.Base64.getDecoder().decode(encoded),Charsets.UTF_8)
        require(text.length<=100000)
        val anchors=Regex("<a data-start=\"(\\d+)\" data-x=\"([0-9.]+)\" data-id=\"([a-zA-Z0-9_-]+)\"></a>").findAll(html).map {
            RuledAnchor(it.groupValues[1].toInt(),it.groupValues[2].toFloat(),it.groupValues[3])
        }.toList()
        require(anchors.isNotEmpty() && anchors.first().start==0 && anchors.zipWithNext().all { (a,b)->a.start<b.start })
        require(anchors.all { it.start in 0..text.length && it.x in .065f.. .87f && (it.start==0 || text[it.start-1]=='\n') })
        val glyphs=ink(html,"flow").filter { it.offset in text.indices && text[it.offset]=='\uFFFC' }
        RuledDocument(text,anchors,glyphs)
    }.getOrNull()
    fun ink(html:String,part:String):List<RuledGlyph> =
        Regex("data-$part-ink=\\\"([^\\\"]*)\\\"").find(html)?.groupValues?.get(1)?.let(RuledInkCodec::decode) ?: emptyList()
    fun decode(html:String):List<RuledParagraph>? {
        if(!html.startsWith(Prefix)) return null
        return runCatching { paragraph.findAll(html).map { m ->
            RuledParagraph(m.groupValues[1],m.groupValues[2].toInt(),m.groupValues[3].toFloat(),unescape(m.groupValues[4]))
        }.toList() }.getOrNull()
    }
    fun plain(blocks:List<RuledParagraph>)=blocks.filter { it.text.isNotEmpty() }.sortedWith(compareBy({it.row},{it.x})).joinToString("\n") { it.text }
    fun initial(page:DiaryPage)=decode(page.bodyHtml) ?: if(page.body.isEmpty()) emptyList() else listOf(RuledParagraph("legacy",2,.065f,page.body))
}
