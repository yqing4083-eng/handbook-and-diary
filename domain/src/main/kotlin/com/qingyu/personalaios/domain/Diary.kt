package com.qingyu.personalaios.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max

enum class PaperStyle { PLAIN, LINED, DOTTED, GRID, MUSIC }
/** LEGACY keeps existing books and imported older backups fully editable. */
enum class BookKind { LEGACY, SCRAPBOOK, DIARY }
enum class InkTool { FELT, FOUNTAIN, PENCIL, MARKER, WATERCOLOR, DIAGRAM, FILL, BLEND, ERASER, TECHNICAL, CRAYON, INK, GLYPH_ERASER }
data class InkPoint(val x: Float, val y: Float, val pressure: Float = 1f) {
    init { require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f); require(pressure.isFinite() && pressure in 0f..2f) }
}
data class InkStroke(
    val id: String = UUID.randomUUID().toString(),
    val color: Long = 0xFF243749,
    val width: Float = .008f,
    val points: List<InkPoint>,
    val tool: InkTool = InkTool.FELT,
) {
    init { require(width.isFinite() && width in .001f..if(tool==InkTool.ERASER) .10f else .05f); require(points.isNotEmpty()) }
}
data class DiaryPage(
    val id: String = UUID.randomUUID().toString(),
    val order: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val title: String = "",
    val body: String = "",
    val style: PaperStyle = PaperStyle.PLAIN,
    val strokes: List<InkStroke> = emptyList(),
    val revision: Long = 0,
    val example: Boolean = false,
    val stickers: List<DiarySticker> = emptyList(),
    val journalId: String = "main",
    // New pages match the measured two-face concertina spread. Stored legacy
    // aspects remain unchanged, so existing drawings and collage coordinates fit.
    val canvasAspect: Float = 1.235f/.914f,
    val deletedAt: Long = 0,
    val bodyHtml: String = "",
    val singleFace: Boolean = false,
    val leftFace: DiaryFaceContent? = null,
    val wallpaper: String = "",
)

enum class PaperFace { LEFT, RIGHT }
data class DiaryFaceContent(val title:String="",val body:String="",val bodyHtml:String="",
    val strokes:List<InkStroke> = emptyList(),val stickers:List<DiarySticker> = emptyList(),val wallpaper:String="")

/** A physical sheet keeps its identity and revision. Editing either face never
 * rewrites the opposite face or turns one sheet into two separate leaves. */
object DiaryFaces {
    fun page(sheet:DiaryPage,face:PaperFace):DiaryPage {
        if(face==PaperFace.RIGHT) return sheet
        val left=sheet.leftFace ?: DiaryFaceContent()
        return sheet.copy(title=left.title,body=left.body,bodyHtml=left.bodyHtml,
            strokes=left.strokes,stickers=left.stickers,wallpaper=left.wallpaper,leftFace=null)
    }
    fun replace(sheet:DiaryPage,face:PaperFace,edited:DiaryPage):DiaryPage =
        if(face==PaperFace.RIGHT) sheet.copy(title=edited.title,body=edited.body,bodyHtml=edited.bodyHtml,
            strokes=edited.strokes,stickers=edited.stickers,wallpaper=edited.wallpaper)
        else sheet.copy(leftFace=DiaryFaceContent(edited.title,edited.body,edited.bodyHtml,edited.strokes,edited.stickers,edited.wallpaper))
}
data class DiaryJournal(
    val id: String = UUID.randomUUID().toString(), val name: String = "未命名书册", val order: Int = 0,
    val coverPath: String = "", val coverColor: Long = 0xFFB5A17E, val bandColor: Long = 0xFFFFBA00,
    val template: PaperStyle = PaperStyle.PLAIN, val deletedAt: Long = 0,
    val lockDigest: String = "", val lastPageId: String = "",
    val kind: BookKind = BookKind.LEGACY,
)
object NewBook {
    const val SpreadAspect = 1.235f / .914f
    const val FaceAspect = SpreadAspect / 2f
    fun journal(kind:BookKind,order:Int=0)=DiaryJournal(name="NeoBook",order=order,kind=kind,
        template=if(kind==BookKind.DIARY) PaperStyle.LINED else PaperStyle.PLAIN)
    fun page(journal:DiaryJournal,order:Int)=DiaryPage(order=order,journalId=journal.id,
        style=when(journal.kind) { BookKind.DIARY->PaperStyle.LINED;BookKind.SCRAPBOOK->PaperStyle.PLAIN;else->journal.template },
        canvasAspect=if(journal.kind==BookKind.DIARY) FaceAspect else SpreadAspect,singleFace=journal.kind==BookKind.DIARY)
}
data class InkClip(val id:String=UUID.randomUUID().toString(),val name:String="画布片段",val strokes:List<InkStroke>)
/** Every page element shares one list, so layer order is simply the list order. */
enum class StickerKind { IMAGE, STICKER, TAPE, TEXT }
enum class TapePattern { SOLID, STRIPE, DOT, CHECK, LACE }
/** Images use the same normalized 1.1:1 canvas as ink, so their position survives resizing. */
data class DiarySticker(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val x: Float = .25f,
    val y: Float = .2f,
    val width: Float = .5f,
    val height: Float = .5f,
    val rotation: Float = 0f,
    val crop: List<InkPoint> = emptyList(),
    val kind: StickerKind = StickerKind.IMAGE,
    val alpha: Float = 1f,
    val tape: TapePattern = TapePattern.STRIPE,
    val text: String = "",
    val fontSize: Float = .05f,
    val color: Long = 0xFF243749,
    val fillPage: Boolean = false,
    val locked: Boolean = false,
) {
    init {
        // Tape and text are drawn from their own parameters, so they carry no asset path.
        require(kind==StickerKind.TEXT || kind==StickerKind.TAPE || path.isNotBlank())
        require(listOf(x,y,width,height,rotation,alpha,fontSize).all { it.isFinite() })
        require(alpha in .05f..1f)
        require(fontSize in .015f.. .4f)
        val limit=if(kind==StickerKind.IMAGE && fillPage) 64f else if(kind==StickerKind.IMAGE || kind==StickerKind.STICKER) 6f else 1f
        require(width in .08f..limit && height in .08f..limit)
        require(x in minOf(0f,1f-width)..maxOf(0f,1f-width) && y in minOf(0f,1f-height)..maxOf(0f,1f-height))
        require(text.length<=4000)
    }
}
fun DiarySticker.transformed(dx: Float = 0f, dy: Float = 0f, zoom: Float = 1f, rotationDelta: Float = 0f,
    pivotX:Float=.5f,pivotY:Float=.5f): DiarySticker {
    require(listOf(dx,dy,zoom,rotationDelta).all { it.isFinite() } && zoom > 0)
    if(locked) return this
    val limit=if(kind==StickerKind.IMAGE && fillPage) 64f else if(kind==StickerKind.IMAGE || kind==StickerKind.STICKER) 6f else 1f
    val factor=zoom.coerceIn(maxOf(.08f/width,.08f/height),minOf(limit/width,limit/height))
    val w=(width*factor).coerceIn(.08f,limit);val h=(height*factor).coerceIn(.08f,limit)
    return copy(x=(x+dx+(width-w)*pivotX.coerceIn(0f,1f)).coerceIn(minOf(0f,1f-w),maxOf(0f,1f-w)),
        y=(y+dy+(height-h)*pivotY.coerceIn(0f,1f)).coerceIn(minOf(0f,1f-h),maxOf(0f,1f-h)),
        width=w,height=h,rotation=(rotation+rotationDelta)%360f)
}
interface DiaryRepository {
    val pages: Flow<List<DiaryPage>>
    val journals: Flow<List<DiaryJournal>> get() = flowOf(listOf(DiaryJournal(id="main",name="我的日记")))
    val clips:Flow<List<InkClip>> get()=flowOf(emptyList())
    suspend fun initialize()
    suspend fun addPage(): String
    suspend fun save(pages: List<DiaryPage>)
    suspend fun importImage(source: String): String = error("Image import is unavailable")
    suspend fun importPhotoPages(journalId:String,startId:String?,sources:List<String>):String = error("Photo pages are unavailable")
    /** Keeps a generated asset under a stable name, so repeated inserts reuse one file. */
    suspend fun importBytes(data: ByteArray, name: String): String = error("Asset import is unavailable")
    suspend fun addPage(journalId: String, afterId: String?): String = addPage()
    suspend fun putJournal(journal: DiaryJournal) { error("Journal management is unavailable") }
    suspend fun createJournal(journal:DiaryJournal,afterId:String?=null):String {
        putJournal(journal);return addPage(journal.id,null)
    }
    suspend fun pageShown(journalId:String,pageId:String) = Unit
    suspend fun organize(ids: List<String>, journalId: String, beforeId: String? = null) { error("Page management is unavailable") }
    suspend fun duplicate(ids: List<String>, journalId: String): String = error("Page management is unavailable")
    suspend fun trash(ids: List<String>, restore: Boolean = false) { error("Page management is unavailable") }
    suspend fun backup(destination: String) { error("Backup is unavailable") }
    suspend fun restore(source: String) { error("Restore is unavailable") }
    suspend fun putClip(clip:InkClip) { error("Canvas clips are unavailable") }
    suspend fun deleteClip(id:String) { error("Canvas clips are unavailable") }
}

fun inkInside(points:List<InkPoint>,point:InkPoint):Boolean {
    if(points.size<3) return false
    var inside=false;var previous=points.last()
    points.forEach { p ->
        if((p.y>point.y)!=(previous.y>point.y) && point.x<(previous.x-p.x)*(point.y-p.y)/(previous.y-p.y)+p.x) inside=!inside
        previous=p
    };return inside
}
fun List<InkStroke>.shifted(dx:Float,dy:Float):List<InkStroke> {
    if(isEmpty()) return this
    val points=flatMap { it.points };val x=dx.coerceIn(-points.minOf { it.x },1-points.maxOf { it.x })
    val y=dy.coerceIn(-points.minOf { it.y },1-points.maxOf { it.y })
    return map { it.copy(points=it.points.map { p -> p.copy(x=(p.x+x).coerceIn(0f,1f),y=(p.y+y).coerceIn(0f,1f)) }) }
}

/** All coordinates are normalized to the drawing's fixed aspect ratio (width / height = 1.1). */
fun InkStroke.hits(point: InkPoint, radius: Float = .035f,aspect:Float=1.1f): Boolean {
    fun distance(a: InkPoint, b: InkPoint): Float {
        val dx = b.x - a.x; val dy = (b.y - a.y) / aspect
        val px = point.x - a.x; val py = (point.y - a.y) / aspect
        val length = dx * dx + dy * dy
        val t = if (length == 0f) 0f else ((px * dx + py * dy) / length).coerceIn(0f, 1f)
        return hypot(px - t * dx, py - t * dy)
    }
    return if (points.size == 1) distance(points[0], points[0]) <= radius + width / 2
    else points.zipWithNext().any { (a, b) -> distance(a, b) <= radius + width / 2 }
}

/** Raw (unnormalized) extent of one stroke, in the same coordinates as [InkPoint]. */
data class InkBounds(val minX:Float,val minY:Float,val maxX:Float,val maxY:Float) {
    /** True when [point] is close enough that the exact segment test can still accept it. */
    fun reaches(point:InkPoint,reach:Float,aspect:Float):Boolean {
        // The exact test measures distance after dividing y by the aspect ratio, so the box is
        // grown by reach on x and by reach * aspect on y. `max(1f, aspect)` only widens x, which
        // keeps this a rejection filter: it can never drop a stroke [InkStroke.hits] would keep.
        val wide=reach*max(1f,aspect);val tall=reach*aspect
        return point.x>=minX-wide && point.x<=maxX+wide && point.y>=minY-tall && point.y<=maxY+tall
    }
}

fun InkStroke.bounds():InkBounds {
    if (points.isEmpty()) return InkBounds(0f,0f,0f,0f)
    var minX=points[0].x;var maxX=minX;var minY=points[0].y;var maxY=minY
    for (index in 1 until points.size) {
        val p=points[index]
        if (p.x<minX) minX=p.x else if (p.x>maxX) maxX=p.x
        if (p.y<minY) minY=p.y else if (p.y>maxY) maxY=p.y
    }
    return InkBounds(minX,minY,maxX,maxY)
}

/**
 * Eraser sweep over one page.
 *
 * Bounds are measured once per stroke when the gesture starts, so an eraser moving across a busy
 * page rejects the strokes it is nowhere near without walking their points. Every stroke whose box
 * is reached still goes through the exact [InkStroke.hits] test, so the set this returns is exactly
 * the set the plain filter would have removed.
 */
class InkEraser(strokes:List<InkStroke>,private val aspect:Float=1.1f) {
    private class Swept(val stroke:InkStroke,val box:InkBounds)

    private val boxes: List<Swept> = strokes.map { Swept(it,it.bounds()) }

    fun removedAt(point:InkPoint,radius:Float=.035f):Set<String> {
        var result:MutableSet<String>?=null
        boxes.forEach { swept ->
            if(!swept.box.reaches(point,radius+swept.stroke.width/2,aspect)) return@forEach
            if(!swept.stroke.hits(point,radius,aspect)) return@forEach
            val set=result ?: HashSet<String>().also { result=it }
            set.add(swept.stroke.id)
        }
        return result ?: emptySet()
    }
}
