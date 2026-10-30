package com.qingyu.personalaios.domain

/** One editable document, with paragraph indents rather than padding spaces or
 * competing text fields. Offsets are UTF-16, matching Android IME selection. */
data class RuledAnchor(val start:Int,val x:Float,val id:String)
/** One original vector drawing occupies one editable character. No OCR or font substitution. */
data class RuledGlyph(val offset:Int,val strokes:List<InkStroke>,val paperWidthInk:Boolean=false,val sourceWidthScale:Float=1f/.56f)
data class RuledDocument(val text:String,val anchors:List<RuledAnchor>,val glyphs:List<RuledGlyph> = emptyList()) {
    fun insertInk(at:Int,strokes:List<InkStroke>,paperWidthInk:Boolean=false,sourceWidthScale:Float=1f/.56f):RuledDocument {
        require(strokes.isNotEmpty())
        val position=at.coerceIn(0,text.length)
        val next=edit(text.substring(0,position)+"\uFFFC"+text.substring(position))
        return next.copy(glyphs=(glyphs.map { if(it.offset>=position) it.copy(offset=it.offset+1) else it }+RuledGlyph(position,strokes,paperWidthInk,sourceWidthScale)).sortedBy { it.offset })
    }
    fun anchorAt(offset:Int):RuledAnchor=anchors.lastOrNull { it.start<=offset } ?: RuledAnchor(0,.065f,"body")
    fun indent(offset:Int,x:Float):RuledDocument {
        val start=text.lastIndexOf('\n',(offset-1).coerceAtLeast(-1))+1
        val old=anchors.find { it.start==start }
        return copy(anchors=(anchors.filterNot { it.start==start }+RuledAnchor(start,x.coerceIn(.065f,.87f),old?.id ?: "p-$start")).sortedBy { it.start })
    }
    fun edit(next:String,changeStart:Int?=null,removed:Int?=null):RuledDocument {
        if(text==next) return this
        var prefix=0
        while(prefix<minOf(text.length,next.length) && text[prefix]==next[prefix]) prefix++
        var suffix=0
        while(suffix<text.length-prefix && suffix<next.length-prefix && text[text.lastIndex-suffix]==next[next.lastIndex-suffix]) suffix++
        // Native Editable supplies the exact range, disambiguating adjacent ink objects.
        if(changeStart!=null && removed!=null) {
            require(changeStart in 0..text.length && removed in 0..text.length-changeStart)
            prefix=changeStart;suffix=text.length-changeStart-removed
        }
        val oldEnd=text.length-suffix;val delta=next.length-text.length
        val inherited=anchorAt(prefix)
        val shifted=anchors.mapNotNull { a -> when {
            a.start<=prefix -> a
            a.start>=oldEnd -> a.copy(start=a.start+delta)
            else -> null
        } }.filter { it.start<=next.length && (it.start==0 || next[it.start-1]=='\n') }.associateBy { it.start }.toMutableMap()
        val starts=listOf(0)+next.indices.filter { next[it]=='\n' }.map { it+1 }
        for(start in starts) if(start !in shifted) shifted[start]=RuledAnchor(start,inherited.x,"p-$start-${next.length}")
        val ink=glyphs.mapNotNull { g -> when {
            g.offset<prefix -> g
            g.offset>=oldEnd -> g.copy(offset=g.offset+delta)
            else -> null
        } }.filter { it.offset in next.indices && next[it.offset]=='\uFFFC' }
        return RuledDocument(next,starts.map { checkNotNull(shifted[it]) },ink)
    }
    fun blocks(rowAtOffset:(Int)->Int):List<RuledParagraph> = anchors.mapNotNull { a ->
        val end=text.indexOf('\n',a.start).takeIf { it>=0 } ?: text.length
        val value=text.substring(a.start,end)
        if(value.isEmpty()) null else RuledParagraph(a.id,rowAtOffset(a.start).coerceIn(2,10000),a.x,value)
    }
    companion object {
        fun from(blocks:List<RuledParagraph>,visualLines:(String,Float)->Int):RuledDocument {
            val result=StringBuilder();val anchors=mutableListOf<RuledAnchor>();var row=2
            for(block in blocks.sortedWith(compareBy({it.row},{it.x}))) {
                while(row<block.row) { anchors+=RuledAnchor(result.length,.065f,"blank-${result.length}");result.append('\n');row++ }
                val paragraphs=block.text.split('\n')
                for((index,value) in paragraphs.withIndex()) {
                    anchors+=RuledAnchor(result.length,block.x,if(index==0) block.id else "${block.id}-$index")
                    result.append(value);row+=visualLines(value,block.x).coerceAtLeast(1)
                    result.append('\n')
                }
            }
            if(result.isNotEmpty()) result.setLength(result.length-1)
            if(anchors.isEmpty()) anchors+=RuledAnchor(0,.065f,"body")
            return RuledDocument(result.toString(),anchors)
        }
    }
}
