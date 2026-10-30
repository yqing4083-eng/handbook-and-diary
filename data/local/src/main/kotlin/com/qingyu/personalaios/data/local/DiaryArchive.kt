package com.qingyu.personalaios.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.qingyu.personalaios.domain.*
import org.json.*
import java.io.File
import java.util.UUID
import java.util.zip.*

/** Portable local backup. Restore imports as new journals; it never overwrites existing pages. */
internal object DiaryArchive {
    suspend fun backup(app:Context,db:DiaryDatabase,destination:String) {
        val (journals,pages)=db.withTransaction { db.diary().allJournals().map { it.journal() } to db.diary().allPages().map { it.page() } }
        val paths=(journals.map { it.coverPath }+pages.flatMap { p -> listOf(p.wallpaper,p.leftFace?.wallpaper ?: "")+(p.stickers+(p.leftFace?.stickers ?: emptyList())).map { it.path } }).filter { it.isNotBlank() }.distinct()
        val media=JSONObject();paths.forEach { media.put(it,"media/${UUID.randomUUID()}.png");check(File(it).isFile) }
        val manifest=JSONObject().put("format",1).put("media",media).put("journals",JSONArray().apply {
            journals.forEach { j -> put(JSONObject().put("id",j.id).put("name",j.name).put("order",j.order).put("cover",j.coverPath)
                .put("color",j.coverColor).put("band",j.bandColor).put("template",j.template.name).put("deleted",j.deletedAt).put("kind",j.kind.name)) }
        }).put("pages",JSONArray().apply {
            pages.forEach { p -> put(JSONObject().put("id",p.id).put("journal",p.journalId).put("order",p.order).put("created",p.createdAt)
                .put("title",p.title).put("body",p.body).put("bodyHtml",p.bodyHtml).put("style",p.style.name).put("ink",DiaryInkCodec.encode(p.strokes))
                .put("stickers",DiaryStickerCodec.encode(p.stickers)).put("aspect",p.canvasAspect).put("deleted",p.deletedAt).put("singleFace",p.singleFace)
                .put("leftFace",DiaryFaceCodec.encode(p.leftFace)).put("wallpaper",p.wallpaper)) }
        })
        ZipOutputStream(checkNotNull(app.contentResolver.openOutputStream(Uri.parse(destination),"wt"))).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString().toByteArray(Charsets.UTF_8));zip.closeEntry()
            paths.forEach { path -> zip.putNextEntry(ZipEntry(media.getString(path)));File(path).inputStream().use { it.copyTo(zip) };zip.closeEntry() }
        }
    }
    suspend fun restore(app:Context,db:DiaryDatabase,source:String) {
        val directory=File(app.filesDir,"diary-media").apply { check(isDirectory || mkdirs()) }
        val imported=mutableMapOf<String,File>();var committed=false;var manifest:JSONObject?=null
        try {
            ZipInputStream(checkNotNull(app.contentResolver.openInputStream(Uri.parse(source)))).use { zip ->
                var total=0L;var count=0
                while(true) {
                    val entry=zip.nextEntry ?: break;check(++count<=2048)
                    check(entry.name=="manifest.json" || entry.name.matches(Regex("media/[a-fA-F0-9-]+\\.png")))
                    check(!entry.isDirectory);check(entry.name !in imported)
                    var bytes=0L;val buffer=ByteArray(8192)
                    if(entry.name=="manifest.json") {
                        check(manifest==null)
                        val data=java.io.ByteArrayOutputStream()
                        while(true) { val n=zip.read(buffer);if(n<0) break;bytes+=n;total+=n;check(bytes<=16*1024*1024 && total<=256*1024*1024);data.write(buffer,0,n) }
                        manifest=JSONObject(data.toString("UTF-8"))
                    } else {
                        val file=File(directory,"${UUID.randomUUID()}.png");imported[entry.name]=file
                        file.outputStream().use { out -> while(true) { val n=zip.read(buffer);if(n<0) break;bytes+=n;total+=n;check(bytes<=24*1024*1024 && total<=256*1024*1024);out.write(buffer,0,n) } }
                        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(file.absolutePath,bounds)
                        check(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096)
                    };zip.closeEntry()
                }
            }
            val data=checkNotNull(manifest);check(data.getInt("format")==1)
            val media=data.getJSONObject("media")
            fun path(value:String):String=if(value.isEmpty()) "" else checkNotNull(imported[media.getString(value)]).absolutePath
            val sourceJournals=data.getJSONArray("journals");check(sourceJournals.length() in 1..2000)
            val ids=mutableMapOf<String,String>()
            val journals=List(sourceJournals.length()) { n -> sourceJournals.getJSONObject(n).let { j ->
                val old=j.getString("id");check(old !in ids);val id=UUID.randomUUID().toString();ids[old]=id
                DiaryJournal(id=id,name=j.getString("name")+" · 恢复",order=j.getInt("order"),coverPath=path(j.getString("cover")),
                    coverColor=j.getLong("color"),bandColor=j.getLong("band"),template=PaperStyle.valueOf(j.getString("template")),deletedAt=j.getLong("deleted"),
                    kind=BookKind.valueOf(j.optString("kind","LEGACY")))
            } }
            val sourcePages=data.getJSONArray("pages");check(sourcePages.length()<=20000)
            val pages=List(sourcePages.length()) { n -> sourcePages.getJSONObject(n).let { p ->
                val aspect=p.getDouble("aspect").toFloat();check(aspect.isFinite() && aspect in .2f..3f)
                DiaryPage(order=p.getInt("order"),createdAt=p.getLong("created"),title=p.getString("title"),body=p.getString("body"),
                    style=PaperStyle.valueOf(p.getString("style")),strokes=DiaryInkCodec.decode(p.getString("ink")),
                    stickers=DiaryStickerCodec.decode(p.getString("stickers")).map { it.copy(path=path(it.path)) },
                    journalId=checkNotNull(ids[p.getString("journal")]),canvasAspect=aspect,deletedAt=p.getLong("deleted"),bodyHtml=p.optString("bodyHtml",""),singleFace=p.optBoolean("singleFace",false),
                    leftFace=DiaryFaceCodec.decode(p.optString("leftFace",""))?.let { face -> face.copy(stickers=face.stickers.map { it.copy(path=path(it.path)) },wallpaper=path(face.wallpaper)) },wallpaper=path(p.optString("wallpaper","")))
            } }
            db.withTransaction {
                val offset=db.diary().allJournals().maxOfOrNull { it.position }?.plus(1) ?: 0
                journals.forEach { db.diary().putJournal(it.copy(order=it.order+offset).entity()) };pages.forEach { db.diary().put(it.entity()) }
            };committed=true
        } finally { if(!committed) imported.values.forEach { it.delete() } }
    }
}
