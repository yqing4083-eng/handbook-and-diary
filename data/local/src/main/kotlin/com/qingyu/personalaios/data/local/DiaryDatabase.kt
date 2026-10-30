package com.qingyu.personalaios.data.local

import android.content.Context
import androidx.room.*
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Entity(tableName = "diary_pages")
data class DiaryEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val createdAt: Long,
    val title: String,
    val body: String,
    val paper: String,
    val ink: String,
    val revision: Long,
    val example: Boolean,
    @ColumnInfo(defaultValue = "'[]'") val stickers: String = "[]",
    @ColumnInfo(defaultValue = "'main'") val journalId: String = "main",
    @ColumnInfo(defaultValue = "1.1") val canvasAspect: Float = 1.1f,
    @ColumnInfo(defaultValue = "0") val deletedAt: Long = 0,
    @ColumnInfo(defaultValue = "''") val bodyHtml: String = "",
    @ColumnInfo(defaultValue = "0") val singleFace: Boolean = false,
    @ColumnInfo(defaultValue = "''") val leftFace: String = "",
    @ColumnInfo(defaultValue = "''") val wallpaper: String = "",
)
@Entity(tableName="diary_journals")
data class DiaryJournalEntity(@PrimaryKey val id:String,val name:String,val position:Int,val coverPath:String,
    val coverColor:Long,val bandColor:Long,val template:String,val deletedAt:Long,val lockDigest:String,val lastPageId:String,
    @ColumnInfo(defaultValue = "'LEGACY'") val kind:String = "LEGACY")
@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_pages ORDER BY position, createdAt, id")
    fun observe(): kotlinx.coroutines.flow.Flow<List<DiaryEntity>>
    @Query("SELECT * FROM diary_pages WHERE id = :id") suspend fun find(id: String): DiaryEntity?
    @Query("SELECT COUNT(*) FROM diary_pages") suspend fun count(): Int
    @Query("SELECT COALESCE(MAX(position), -1) FROM diary_pages") suspend fun lastPosition(): Int
    @Upsert suspend fun put(page: DiaryEntity)
    @Query("SELECT * FROM diary_pages WHERE journalId=:id ORDER BY position, createdAt, id") suspend fun journalPages(id:String):List<DiaryEntity>
    @Query("SELECT * FROM diary_pages ORDER BY journalId,position") suspend fun allPages():List<DiaryEntity>
    @Query("SELECT * FROM diary_journals ORDER BY position,id") fun journals(): kotlinx.coroutines.flow.Flow<List<DiaryJournalEntity>>
    @Query("SELECT * FROM diary_journals ORDER BY position,id") suspend fun allJournals():List<DiaryJournalEntity>
    @Query("SELECT * FROM diary_journals WHERE id=:id") suspend fun journal(id:String):DiaryJournalEntity?
    @Upsert suspend fun putJournal(journal:DiaryJournalEntity)
    @Query("UPDATE diary_journals SET lastPageId=:pageId WHERE id=:journalId") suspend fun pageShown(journalId:String,pageId:String)
}
@Database(entities = [DiaryEntity::class,DiaryJournalEntity::class], version = 7, exportSchema = true)
abstract class DiaryDatabase : RoomDatabase() {
    abstract fun diary(): DiaryDao
    companion object {
        val Migration6To7 = object : Migration(6,7) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_pages ADD COLUMN wallpaper TEXT NOT NULL DEFAULT ''")
            }
        }
        val Migration5To6 = object : Migration(5,6) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_pages ADD COLUMN leftFace TEXT NOT NULL DEFAULT ''")
            }
        }
        val Migration4To5 = object : Migration(4,5) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_journals ADD COLUMN kind TEXT NOT NULL DEFAULT 'LEGACY'")
                db.execSQL("ALTER TABLE diary_pages ADD COLUMN singleFace INTEGER NOT NULL DEFAULT 0")
            }
        }
        val Migration3To4 = object : Migration(3,4) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_pages ADD COLUMN bodyHtml TEXT NOT NULL DEFAULT ''")
            }
        }
        val Migration1To2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_pages ADD COLUMN stickers TEXT NOT NULL DEFAULT '[]'")
            }
        }
        val Migration2To3 = object:Migration(2,3) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diary_pages ADD COLUMN journalId TEXT NOT NULL DEFAULT 'main'")
                db.execSQL("ALTER TABLE diary_pages ADD COLUMN canvasAspect REAL NOT NULL DEFAULT 1.1")
                db.execSQL("ALTER TABLE diary_pages ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS diary_journals (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,position INTEGER NOT NULL,coverPath TEXT NOT NULL,coverColor INTEGER NOT NULL,bandColor INTEGER NOT NULL,template TEXT NOT NULL,deletedAt INTEGER NOT NULL,lockDigest TEXT NOT NULL,lastPageId TEXT NOT NULL)")
                db.execSQL("INSERT INTO diary_journals VALUES ('main','我的日记',0,'',4285760906,4281615179,'PLAIN',0,'','')")
            }
        }
    }
}

object DiaryStickerCodec {
    fun encode(stickers: List<DiarySticker>): String = JSONArray().apply {
        stickers.forEach { s -> put(JSONObject().apply {
            put("id",s.id);put("path",s.path);put("x",s.x);put("y",s.y)
            put("width",s.width);put("height",s.height);put("rotation",s.rotation)
            put("crop",JSONArray().apply { s.crop.forEach { put(JSONArray().put(it.x).put(it.y)) } })
            put("kind",s.kind.name);put("alpha",s.alpha);put("tape",s.tape.name)
            put("fontSize",s.fontSize);put("color",s.color)
            if(s.fillPage) put("fillPage",true)
            if(s.locked) put("locked",true)
            if(s.text.isNotEmpty()) put("text",s.text)
        }) }
    }.toString()
    fun decode(value: String): List<DiarySticker> = JSONArray(value).let { a -> List(a.length()) { i ->
        a.getJSONObject(i).let { s -> DiarySticker(s.getString("id"),s.getString("path"),
            s.getDouble("x").toFloat(),s.getDouble("y").toFloat(),s.getDouble("width").toFloat(),
            s.getDouble("height").toFloat(),s.getDouble("rotation").toFloat(),
            s.optJSONArray("crop")?.let { points -> List(points.length()) { n -> points.getJSONArray(n).let { InkPoint(it.getDouble(0).toFloat(),it.getDouble(1).toFloat()) } } } ?: emptyList(),
            StickerKind.valueOf(s.optString("kind",StickerKind.IMAGE.name)),
            s.optDouble("alpha",1.0).toFloat(),
            TapePattern.valueOf(s.optString("tape",TapePattern.STRIPE.name)),
            s.optString("text",""),
            s.optDouble("fontSize",.05).toFloat(),
            s.optLong("color",0xFF243749),s.optBoolean("fillPage",false),s.optBoolean("locked",false)) }
    } }
}

object DiaryInkCodec {
    fun encode(strokes: List<InkStroke>): String = JSONArray().apply {
        strokes.forEach { stroke -> put(JSONObject().apply {
            put("id", stroke.id); put("color", stroke.color); put("width", stroke.width)
            put("tool",stroke.tool.name)
            put("points", JSONArray().apply {
                stroke.points.forEach { put(JSONArray().put(it.x).put(it.y).put(it.pressure)) }
            })
        }) }
    }.toString()
    fun decode(value: String): List<InkStroke> {
        val array = JSONArray(value)
        return List(array.length()) { index ->
            val stroke = array.getJSONObject(index); val points = stroke.getJSONArray("points")
            InkStroke(stroke.getString("id"), stroke.getLong("color"), stroke.getDouble("width").toFloat(),
                List(points.length()) { p -> points.getJSONArray(p).let {
                    InkPoint(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(),it.optDouble(2,1.0).toFloat())
                } },InkTool.valueOf(stroke.optString("tool","FELT")))
        }
    }
}
object DiaryFaceCodec {
    fun encode(face:DiaryFaceContent?):String = face?.let { JSONObject().put("title",it.title)
        .put("body",it.body).put("bodyHtml",it.bodyHtml).put("ink",DiaryInkCodec.encode(it.strokes))
        .put("stickers",DiaryStickerCodec.encode(it.stickers)).put("wallpaper",it.wallpaper).toString() } ?: ""
    fun decode(value:String):DiaryFaceContent? = if(value.isBlank()) null else JSONObject(value).let {
        DiaryFaceContent(it.optString("title"),it.optString("body"),it.optString("bodyHtml"),
            DiaryInkCodec.decode(it.optString("ink","[]")),DiaryStickerCodec.decode(it.optString("stickers","[]")),it.optString("wallpaper",""))
    }
}
internal fun DiaryPage.entity() = DiaryEntity(id, order, createdAt, title, body, style.name,
    DiaryInkCodec.encode(strokes), revision, example, DiaryStickerCodec.encode(stickers),journalId,canvasAspect,deletedAt,bodyHtml,singleFace,DiaryFaceCodec.encode(leftFace),wallpaper)
internal fun DiaryEntity.page() = DiaryPage(id, position, createdAt, title, body,
    PaperStyle.valueOf(paper), DiaryInkCodec.decode(ink), revision, example, DiaryStickerCodec.decode(stickers),journalId,canvasAspect,deletedAt,bodyHtml,singleFace,DiaryFaceCodec.decode(leftFace),wallpaper)
internal fun DiaryJournal.entity()=DiaryJournalEntity(id,name,order,coverPath,coverColor,bandColor,template.name,deletedAt,lockDigest,lastPageId,kind.name)
internal fun DiaryJournalEntity.journal()=DiaryJournal(id,name,position,coverPath,coverColor,bandColor,PaperStyle.valueOf(template),deletedAt,lockDigest,lastPageId,BookKind.valueOf(kind))

class RoomDiaryRepository(context: Context) : DiaryRepository {
    private val app = context.applicationContext
    private val database = Room.databaseBuilder(context.applicationContext, DiaryDatabase::class.java,
        "diary.db").addMigrations(DiaryDatabase.Migration1To2,DiaryDatabase.Migration2To3,DiaryDatabase.Migration3To4,DiaryDatabase.Migration4To5,DiaryDatabase.Migration5To6,DiaryDatabase.Migration6To7).build()
    private val dao = database.diary()
    override val pages = dao.observe().map { rows -> rows.map { it.page() } }
    override val journals = dao.journals().map { rows -> rows.map { it.journal() } }
    private val clipState=MutableStateFlow<List<InkClip>>(emptyList())
    override val clips=clipState
    private val clipWrites=Mutex()
    private val clipFile=android.util.AtomicFile(File(app.filesDir,"diary-clips.json"))
    override suspend fun putClip(clip:InkClip)=writeClips { it.filterNot { c -> c.id==clip.id }+clip }
    override suspend fun deleteClip(id:String)=writeClips { it.filterNot { c -> c.id==id } }
    private suspend fun writeClips(change:(List<InkClip>)->List<InkClip>)=withContext(Dispatchers.IO) {
        clipWrites.withLock {
            val next=change(clipState.value)
            val encoded=JSONArray().apply { next.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("ink",DiaryInkCodec.encode(it.strokes))) } }.toString().toByteArray()
            var output:java.io.FileOutputStream?=null
            try { output=clipFile.startWrite();output.write(encoded);clipFile.finishWrite(output);clipState.value=next }
            catch(error:Throwable) { clipFile.failWrite(output);throw error }
        }
    }
    override suspend fun backup(destination:String)=withContext(Dispatchers.IO) { DiaryArchive.backup(app,database,destination) }
    override suspend fun restore(source:String)=withContext(Dispatchers.IO) { DiaryArchive.restore(app,database,source) }
    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            if(clipFile.baseFile.exists()) {
                val encoded=JSONArray(clipFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                clipState.value=List(encoded.length()) { i -> encoded.getJSONObject(i).let { InkClip(it.getString("id"),it.getString("name"),DiaryInkCodec.decode(it.getString("ink"))) } }
            }
        }
        // A fresh or emptied library stays empty. Legacy entries are preserved
        // by Room migrations; startup must never recreate demo books or pages.
    }
    override suspend fun addPage(): String = database.withTransaction {
        val page = DiaryPage(order = dao.lastPosition() + 1)
        dao.put(page.entity()); page.id
    }
    override suspend fun addPage(journalId:String,afterId:String?):String=database.withTransaction {
        val journal=checkNotNull(dao.journal(journalId));check(journal.deletedAt==0L)
        val rows=dao.journalPages(journalId).filter { it.deletedAt==0L }
        val at=rows.indexOfFirst { it.id==afterId }.let { if(it<0) rows.size else it+1 }
        rows.drop(at).forEach { dao.put(it.copy(position=it.position+1,revision=it.revision+1)) }
        val page=NewBook.page(journal.journal(),at)
        dao.put(page.entity());page.id
    }
    override suspend fun putJournal(journal:DiaryJournal)=database.withTransaction { dao.putJournal(journal.entity()) }
    override suspend fun createJournal(journal:DiaryJournal,afterId:String?):String {
        // Copy bundled artwork off the main thread. Private, immutable files are
        // compatible with cover editing, backups and the existing texture cache.
        val prepared=if(journal.kind!=BookKind.LEGACY && journal.coverPath.isBlank())
            withContext(Dispatchers.IO) { InitialBookCovers.apply(app,journal) } else journal
        return database.withTransaction {
        val ordered=dao.allJournals().toMutableList()
        val at=ordered.indexOfFirst { it.id==afterId }.let { if(it<0) ordered.size else it+1 }
        ordered.add(at,prepared.entity())
        ordered.forEachIndexed { index,row -> dao.putJournal(row.copy(position=index)) }
        val page=NewBook.page(prepared,0)
        dao.put(page.entity());page.id
        }
    }
    override suspend fun pageShown(journalId:String,pageId:String) { dao.pageShown(journalId,pageId) }
    override suspend fun organize(ids:List<String>,journalId:String,beforeId:String?)=database.withTransaction {
        val targetJournal=checkNotNull(dao.journal(journalId)).also { check(it.deletedAt==0L) }
        val moving=ids.mapNotNull { dao.find(it) }.filter { it.deletedAt==0L }
        moving.forEach { require(dao.journal(it.journalId)?.kind==targetJournal.kind) { "Different book formats cannot share pages" } }
        val target=dao.journalPages(journalId).filter { it.deletedAt==0L && it.id !in ids }.toMutableList()
        val at=target.indexOfFirst { it.id==beforeId }.let { if(it<0) target.size else it }
        target.addAll(at,moving)
        target.forEachIndexed { index,row -> dao.put(row.copy(journalId=journalId,position=index,revision=row.revision+1)) }
    }
    override suspend fun duplicate(ids:List<String>,journalId:String):String=database.withTransaction {
        val targetJournal=checkNotNull(dao.journal(journalId))
        val target=dao.journalPages(journalId).filter { it.deletedAt==0L }.toMutableList()
        var last=""
        ids.mapNotNull { dao.find(it) }.forEach { source ->
            require(dao.journal(source.journalId)?.kind==targetJournal.kind)
            val at=target.indexOfFirst { it.id==source.id }.let { if(it<0) target.size else it+1 }
            last=UUID.randomUUID().toString()
            target.add(at,source.copy(id=last,journalId=journalId,deletedAt=0,example=false,revision=0))
        }
        target.forEachIndexed { index,row -> dao.put(row.copy(position=index,revision=row.revision+1)) };last
    }
    override suspend fun trash(ids:List<String>,restore:Boolean)=database.withTransaction {
        ids.forEach { id -> dao.find(id)?.let { dao.put(it.copy(deletedAt=if(restore) 0 else System.currentTimeMillis(),revision=it.revision+1)) } }
    }
    override suspend fun save(pages: List<DiaryPage>) = database.withTransaction {
        pages.forEach { page ->
            if(dao.journal(page.journalId)?.kind==BookKind.SCRAPBOOK.name) {
                // The paper editor supports drawing over photos in a scrapbook.
                require(page.title.isBlank() && page.body.isBlank() && page.bodyHtml.isBlank())
                require(page.style==PaperStyle.PLAIN && !page.singleFace && page.leftFace==null && page.stickers.all { it.kind==StickerKind.IMAGE })
            }
            val stored = dao.find(page.id)
            if (stored != null && stored.revision <= page.revision) dao.put(page.entity())
        }
    }
    override suspend fun importImage(source: String): String = withContext(Dispatchers.IO) {
        OriginalDiaryMedia.copy(app,source,File(app.filesDir,"diary-media"))
    }
    override suspend fun importBytes(data: ByteArray, name: String): String = withContext(Dispatchers.IO) {
        val directory=File(app.filesDir,"diary-media").apply { check(isDirectory || mkdirs()) }
        val file=File(directory,name)
        if(file.isFile) return@withContext file.absolutePath
        val temporary=File(directory,"$name.tmp")
        try { temporary.outputStream().use { it.write(data) };check(temporary.renameTo(file));file.absolutePath }
        catch(error:Throwable) { temporary.delete();throw error }
    }
    override suspend fun importPhotoPages(journalId:String,startId:String?,sources:List<String>):String {
        require(sources.size in 1..PhotoPageFill.MaxImages)
        val owned=mutableListOf<String>()
        var committed=false
        try {
            // Decode/copy outside the transaction. Failed imports leave every original page intact.
            sources.forEach { owned+=importImage(it) }
            val id=database.withTransaction {
                val journal=checkNotNull(dao.journal(journalId)).also { check(it.deletedAt==0L) }.journal()
                val plan=PhotoPageFill.plan(dao.journalPages(journalId).map { it.page() },journalId,startId,owned,journal.kind)
                plan.pages.forEach { dao.put(it.entity()) }
                plan.firstId
            }
            committed=true;return id
        } finally {
            if(!committed) withContext(NonCancellable+Dispatchers.IO) { owned.forEach { File(it).delete() } }
        }
    }
}
