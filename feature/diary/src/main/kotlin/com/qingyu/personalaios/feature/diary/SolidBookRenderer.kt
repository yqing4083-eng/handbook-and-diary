package com.qingyu.personalaios.feature.diary

import android.graphics.*
import android.opengl.Matrix
import com.qingyu.personalaios.domain.*
import kotlin.math.*

/** Retained planar Vulkan meshes. Fold motion uses paired rigid transforms. */
internal class SolidBookRenderer(private val gpu:VulkanBookBackend,private val frameReady:()->Unit) {
    private val artwork=BookTextureStore(frameReady)
    private var uploadBudget=1
    private val right=PaperMesh(BookGeometry.HalfWidth,1.8f,.095f,1,paper=true)
    private val rightEdge=PaperMesh(BookGeometry.HalfWidth,1.8f,.095f,1,rimDepth=PaperMaterial.PanelThickness,paper=true)
    private val blockFace=PaperMesh(BookGeometry.HalfWidth,1.8f,.075f)
    private val binding=PaperMesh(1f,1.8f,.0005f)
    private val cover=PaperMesh(BookGeometry.CoverWidth,BookGeometry.CoverHeight,.13f,1)
    private val shadow=PaperMesh(1.5f,2.1f,.08f)
    private val spreadShadow=PaperMesh(2.7f,2.05f,.08f)
    private val coverRim=PaperMesh(BookGeometry.CoverWidth,BookGeometry.CoverHeight,.13f,1,rimDepth=BookBoardMaterial.Thickness)
    private val paperRim=PaperMesh(BookGeometry.HalfWidth,1.8f,.075f,rimDepth=.12f)
    private val projection=FloatArray(16)
    private val model=FloatArray(16)
    private val mvp=FloatArray(16)
    private val sceneModel=FloatArray(16)
    private val casterModel=FloatArray(16)
    private val shadowProjection=FloatArray(16)
    private val projectedModel=FloatArray(16)
    private val matrices=Array(8) { FloatArray(16) }
    private var level=0
    private var initialized=false
    private var pageHeightScale=1f
    private var coverTexture=0
    private var shadowTexture=0
    private var albumShadowTexture=0
    private var overviewShadowTexture=0
    private var contactShadowTexture=0
    private var whiteTexture=0
    private var thumbnail=false
    private var shelfSource:List<DiaryJournal>?=null
    private var shelfId:String?=null
    private var shelfCandidates=emptyList<Pair<Int,DiaryJournal>>()
    private fun shelf(scene:BookScene):List<Pair<Int,DiaryJournal>> {
        if(shelfSource!==scene.journals || shelfId!=scene.journal.id) {
            shelfSource=scene.journals;shelfId=scene.journal.id;shelfCandidates=BookRenderPolicy.shelf(scene)
        }
        return shelfCandidates
    }
    private data class Texture(val id:Int,val bytes:Int)
    private val textures=LinkedHashMap<String,Texture>(24,.75f,true)
    private val frameTextures=HashSet<String>()
    @Volatile private var uploaded:Set<String> = emptySet()
    fun pageReady(page:DiaryPage)=artwork.pageKey(page) in uploaded
    fun coverReady(journal:DiaryJournal)=artwork.coverKey(journal) in uploaded
    private var textureBytes=0L
    private var preparedPages:List<DiaryPage>?=null
    private var preparedJournals:List<DiaryJournal>?=null
    private var preparedIndex=-1
    private var preparedJournal:DiaryJournal?=null
    private var preparedPending:DiaryJournal?=null
    private var preparedPendingPage:DiaryPage?=null
    private var viewportWidth=1f
    private var viewportHeight=1f
    private var preparedDirection=0
    private var preparedOverview=IntRange.EMPTY
    private var preparedOpen=false
    private var openingPages=emptyList<OpeningPageResource>()
    private var openingJournals=emptyList<DiaryJournal>()
    private var openingKeys=emptySet<String>()
    private var openingMeshesReady=false
    fun openingResourcesReady(scene:BookScene)=openingMeshesReady && preparedOpen &&
        preparedPages===scene.pages && preparedJournal==scene.journal && preparedIndex==scene.current &&
        openingKeys.all { it in uploaded }
    fun contextCreated() { initialized=false;coverTexture=0;shadowTexture=0;albumShadowTexture=0;overviewShadowTexture=0;contactShadowTexture=0;whiteTexture=0;textures.clear();uploaded=emptySet();textureBytes=0;preparedPages=null;preparedJournals=null;preparedIndex=-1;preparedJournal=null;preparedPending=null;openingKeys=emptySet();openingPages=emptyList();openingJournals=emptyList();openingMeshesReady=false;listOf(right,rightEdge,blockFace,binding,cover,shadow,spreadShadow,coverRim,paperRim).forEach { it.reset() } }
    fun release() { artwork.release() }
    private fun initialize() {
        val bitmap=Bitmap.createBitmap(160,240,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(24f,26f,136f,214f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x550E1B32;maskFilter=BlurMaskFilter(12f,BlurMaskFilter.Blur.NORMAL) })
        shadowTexture=texture(bitmap)
        val albumShadow=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888)
        // Coverage reaches the hinge. An inset on each half left a bright slit
        // through the combined spread's shadow, falsely separating the two leaves.
        Canvas(albumShadow).drawRoundRect(-2f,-2f,162f,162f,7f,7f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=0x550E1B32;maskFilter=BlurMaskFilter(7f,BlurMaskFilter.Blur.NORMAL)
        })
        albumShadowTexture=texture(albumShadow)
        // Padded, complete-sheet silhouette. Unlike a texture cropped at each
        // half's edge, this can cast a visible soft halo without a centre slit.
        val overviewShadow=Bitmap.createBitmap(256,192,Bitmap.Config.ARGB_8888)
        Canvas(overviewShadow).drawRoundRect(16f,16f,240f,176f,9f,9f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=0x850E1B32.toInt();maskFilter=BlurMaskFilter(12f,BlurMaskFilter.Blur.NORMAL)
        })
        overviewShadowTexture=texture(overviewShadow)
        val contact=Bitmap.createBitmap(160,240,Bitmap.Config.ARGB_8888)
        Canvas(contact).drawRect(12f,12f,148f,228f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x770E1B32;maskFilter=BlurMaskFilter(1.5f,BlurMaskFilter.Blur.NORMAL) })
        contactShadowTexture=texture(contact)
        whiteTexture=texture(Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) })
    }
    private fun texture(bitmap:Bitmap):Int = gpu.texture(bitmap)
    private fun page(page:DiaryPage,requestedSmall:Boolean=false):Int {
        val small=requestedSmall || thumbnail
        val key=artwork.pageKey(page,small);textures[key]?.let { frameTextures.add(key);return it.id }
        fun fallback():Int {
            val other=artwork.pageKey(page,!small)
            return textures[other]?.let { frameTextures.add(other);it.id } ?: whiteTexture
        }
        if(uploadBudget<=0) return fallback()
        val bitmap=artwork.take(key) ?: run { artwork.preparePage(page,small);return fallback() }
        val id=cacheTexture(key,bitmap)
        frameReady();return id
    }
    private fun journalCover(journal:DiaryJournal):Int {
        val key=artwork.coverKey(journal);textures[key]?.let { frameTextures.add(key);return it.id }
        if(uploadBudget<=0) return 0
        val bitmap=artwork.take(key) ?: run { artwork.prepareCover(journal);return 0 }
        val id=cacheTexture(key,bitmap)
        frameReady();return id
    }
    private fun cacheTexture(key:String,bitmap:Bitmap):Int {
        uploadBudget--
        val bytes=bitmap.allocationByteCount;val id=texture(bitmap)
        textures[key]=Texture(id,bytes);textureBytes+=bytes;frameTextures.add(key)
        while(textures.size>32 || textureBytes>64L*1024*1024) {
            // A recorded Vulkan command keeps its texture alive until submission.
            val entry=textures.entries.firstOrNull { it.key !in frameTextures } ?: break
            gpu.deleteTexture(entry.value.id)
            textureBytes-=entry.value.bytes;textures.remove(entry.key)
        }
        uploaded=textures.keys.toSet()
        return id
    }
    private fun prepare(scene:BookScene,warmOpening:Boolean) {
        val open=BookRenderPolicy.preloadPages(scene,warmOpening)
        val overviewPages=if(scene.overview>0f) BookOverviewGeometry.visible(viewportWidth,viewportHeight,scene.pages.size,scene.overviewAnchor,scene.overviewScroll,scene.overviewTop) else IntRange.EMPTY
        if(preparedPages===scene.pages && preparedJournals===scene.journals && preparedIndex==scene.current &&
            preparedJournal==scene.journal && preparedDirection==scene.direction && preparedPending==scene.pendingJournal && preparedPendingPage==scene.pendingPage && preparedOverview==overviewPages && preparedOpen==open) return
        preparedOpen=open
        preparedOverview=overviewPages
        preparedPages=scene.pages;preparedJournals=scene.journals;preparedIndex=scene.current
        preparedJournal=scene.journal;preparedDirection=scene.direction;preparedPending=scene.pendingJournal
        preparedPendingPage=scene.pendingPage
        // Current and adjacent content is ready before a finger reaches its turn.
        val centre=scene.journals.indexOfFirst { it.id==scene.journal.id }
        val journals=(listOfNotNull(scene.pendingJournal,scene.journal)+listOf(-1,1,-2,2).mapNotNull { scene.journals.getOrNull(centre+it) }).distinctBy { it.id }
        // Full-resolution centre/turning content, smaller far layers: six exposed
        // neighbours stay readable without repeatedly evicting visible textures.
        openingPages=BookOpeningResources.pages(scene.current,scene.pages.size,scene.direction).let { pages ->
            if(scene.thumbnail) pages.map { it.copy(small=true) } else pages
        }
        openingJournals=journals
        openingKeys=(journals.map(artwork::coverKey)+openingPages.map { artwork.pageKey(scene.pages[it.index],it.small) }).toSet()
        // Closed shelf motion needs covers only. The existing 350ms idle fence
        // starts page warming after the shelf stops, or an explicit open prepares it.
        val preload=if(open) openingPages else emptyList()
        val pages=(listOfNotNull(scene.pendingPage?.let { it to false })+preload.mapNotNull { item ->
            scene.pages.getOrNull(item.index)?.let { it to item.small }
        }+overviewPages.mapNotNull { scene.pages.getOrNull(it)?.let { p -> p to true } }).distinctBy { it.first.id }
        artwork.wanted((journals.map(artwork::coverKey)+pages.map { artwork.pageKey(it.first,it.second) }).toSet())
        journals.forEach { if(!textures.containsKey(artwork.coverKey(it))) artwork.prepareCover(it) }
        pages.forEach { (p,small) -> if(!textures.containsKey(artwork.pageKey(p,small))) artwork.preparePage(p,small) }
    }
    private fun prepareOpening(scene:BookScene) {
        // Buffer allocation belongs to preparation, never the first turning frame.
        if(!openingMeshesReady) {
            listOf(right,rightEdge,binding,cover,shadow,coverRim,paperRim,blockFace).forEach { it.id(gpu) }
            openingMeshesReady=true
        }
        openingPages.forEach { page(scene.pages[it.index],it.small) }
        openingJournals.forEach { journalCover(it) }
    }
    private inline fun transform(block:()->Unit) {
        val saved=matrices[level++];model.copyInto(saved)
        try { block() } finally { saved.copyInto(model);level-- }
    }
    private fun translate(x:Float,y:Float,z:Float)=Matrix.translateM(model,0,x,y,z)
    private fun rotate(angle:Float,x:Float,y:Float,z:Float)=Matrix.rotateM(model,0,angle,x,y,z)
    private fun draw(mesh:PaperMesh,texture:Int,r:Float=1f,g:Float=1f,b:Float=1f,alpha:Float=1f,
        start:Float=0f,end:Float=1f,bend:Float=0f,side:Int=1) {
        if(!mesh.isRim && !BookRenderPolicy.faceVisible(model,gpu.cull)) return
        Matrix.multiplyMM(mvp,0,projection,0,model,0)
        gpu.draw(mesh.id(gpu),texture,mvp,model,r,g,b,alpha,start,end,bend,side.toFloat(),mesh.material)
    }

    /** Each half has its own thin closed slab. Content stays on its outward face;
     * the inner face is paper, never the neighbour's image pasted on a flat back. */
    private fun albumPanel(texture:Int,pose:FoldPanelPose,back:Boolean,open:Float,fan:Float,alpha:Float,scale:Float=1f,pitch:Float=0f) = transform {
        val hinge=pose.hinge
        translate(hinge.x*fan,hinge.y,hinge.z*fan+BookBoardMaterial.paperBase(open))
        rotate(pitch,1f,0f,0f)
        rotate(pose.angle*open,0f,1f,0f)
        Matrix.scaleM(model,0,scale,scale,scale)
        translate(BookGeometry.HalfWidth/2,0f,0f)
        Matrix.scaleM(model,0,1f,pageHeightScale,1f)
        val sign=if(back) -1f else 1f
        val outer=sign*PaperMaterial.Thickness/2
        val inner=sign*PaperMaterial.FoldGap/2
        gpu.cull=1
        transform {
            translate(0f,0f,if(back) outer else inner)
            draw(right,if(back) texture else whiteTexture,start=.5f,end=0f,alpha=alpha)
        }
        gpu.cull=2
        transform {
            translate(0f,0f,if(back) inner else outer)
            draw(right,if(back) whiteTexture else texture,start=.5f,end=1f,alpha=alpha)
        }
        gpu.cull=0
        transform {
            translate(0f,0f,if(back) inner else outer)
            draw(rightEdge,whiteTexture,r=1f,g=.985f,b=.95f,alpha=alpha)
        }
    }
    private fun panelMatrix(target:FloatArray,base:FloatArray,pose:FoldPanelPose,open:Float,fan:Float,face:Float=0f) {
        base.copyInto(target)
        Matrix.translateM(target,0,pose.hinge.x*fan,pose.hinge.y,pose.hinge.z*fan+BookBoardMaterial.paperBase(open))
        Matrix.rotateM(target,0,pose.angle*open,0f,1f,0f)
        Matrix.translateM(target,0,BookGeometry.HalfWidth/2,0f,0f)
        Matrix.scaleM(target,0,1f,pageHeightScale,1f)
        Matrix.translateM(target,0,0f,0f,face)
    }
    private fun projectedLeafShadow(alpha:Float) {
        Matrix.multiplyMM(projectedModel,0,shadowProjection,0,casterModel,0)
        Matrix.multiplyMM(mvp,0,projection,0,projectedModel,0)
        // Only the ground receiver remains. No shadow is projected onto paper.
        gpu.draw(right.id(gpu),albumShadowTexture,mvp,projectedModel,1f,1f,1f,
            alpha,0f,1f,0f,1f,-1f)
    }
    private fun albumGroundShadows(leaves:IntRange,poses:List<LeafPose>,index:Int,open:Float,fan:Float) {
        model.copyInto(sceneModel)
            // The six-depth fan floats entirely in front of its receiver plane.
            // A shallow light slope keeps that larger physical distance from
            // throwing a screen-length shadow through the foreground leaves.
            PaperShadowGeometry.projection(sceneModel[12],sceneModel[13],sceneModel[14]-3.3f,0f,0f,1f,shadowProjection,
                lightX=-.035f,lightY=.13f)
            gpu.depthTest=false;gpu.cull=0
            for(k in leaves) {
                val pose=poses[k-leaves.first]
                val front=AlbumLeafGeometry.panel(pose,false)
                val back=AlbumLeafGeometry.panel(pose,true)
                val weights=floatArrayOf(max(0f,cos(front.angle*PI.toFloat()/180)),max(0f,-cos(back.angle*PI.toFloat()/180)))
                val total=(weights[0]+weights[1]).coerceAtLeast(.0001f)
                for(side in 0..1) {
                    if(weights[side]<.0001f) continue
                    panelMatrix(casterModel,sceneModel,if(side==0) front else back,open,fan)
                    projectedLeafShadow(fan*(if(k==index || k==index-1) .52f else .14f)*weights[side]/total)
                }
            }
            gpu.depthTest=true
    }
    /** Flexible binding between adjacent shared hinge nodes. Only its thin
     * exposed edge is visible; each paper face retains its original dimensions. */
    private fun hingeBinding(a:PagePose,b:PagePose,fan:Float) = transform {
        val dx=(b.x-a.x)*fan;val dz=(b.z-a.z)*fan
        val length=hypot(dx,dz)
        if(length<.0001f || fan<.001f) return@transform
        translate((a.x+b.x)*fan/2,(a.y+b.y)/2,(a.z+b.z)*fan/2+.03f*(1-fan)-PaperMaterial.Thickness)
        rotate(-atan2(dz,dx)*180f/PI.toFloat(),0f,1f,0f)
        Matrix.scaleM(model,0,length,pageHeightScale*.935f,1f)
        draw(binding,whiteTexture,r=.90f,g=.88f,b=.83f,alpha=fan)
    }
    private fun closedShadow(pose:LibraryPose,opacity:Float=1f) = transform {
        val rise=((pose.z-BookLibraryGeometry.restingDepth(pose.y))/(BookLibraryGeometry.HoverDepth-BookLibraryGeometry.restingDepth(BookLibraryGeometry.HoverHeight))).coerceIn(0f,1f)
        val shadowY=pose.y+(-.33f-pose.y)*rise
        val planeZ=BookLibraryGeometry.tableZ(shadowY)
        translate(pose.x+.09f*rise,shadowY,planeZ+.003f)
        rotate(BookLibraryGeometry.TablePitch,1f,0f,0f)
        val altitude=(pose.z-planeZ).coerceIn(0f,1.6f)
        Matrix.scaleM(model,0,.96f+(.04f+.10f*altitude)*rise,.96f+(.04f+.08f*altitude)*rise,1f)
        gpu.depthTest=false
        if(rise<1f) draw(shadow,contactShadowTexture,alpha=opacity*(1-rise))
        if(rise>0f) draw(shadow,shadowTexture,alpha=opacity*rise*(1-.20f*altitude))
        gpu.depthTest=true
    }
    /** Front cover, paper block and back board touch, with no floating rim gap. */
    private fun closedBody(journal:DiaryJournal,opacity:Float=1f) {
        val texture=journalCover(journal)
        if(texture==0) return
        // Let the nearest opaque board reject the hidden block/rear pixels.
        // Fading books keep their original back-to-front compositing order.
        if(opacity>=1f) transform { translate(0f,0f,BookBoardMaterial.closedFrontFace);draw(coverRim,whiteTexture,r=.82f,g=.79f,b=.72f,alpha=opacity);draw(cover,texture,alpha=opacity) }
        transform { translate(0f,0f,BookBoardMaterial.ClosedRearFace);draw(coverRim,whiteTexture,r=.82f,g=.79f,b=.72f,alpha=opacity);draw(cover,texture,alpha=opacity) }
        transform { translate(.004f,0f,BookBoardMaterial.ClosedBlockFace);draw(paperRim,whiteTexture,r=.96f,g=.94f,b=.90f,alpha=opacity);draw(blockFace,whiteTexture,alpha=opacity) }
        if(opacity<1f) transform { translate(0f,0f,BookBoardMaterial.closedFrontFace);draw(coverRim,whiteTexture,r=.82f,g=.79f,b=.72f,alpha=opacity);draw(cover,texture,alpha=opacity) }
    }
    private fun closedBook(journal:DiaryJournal,pose:LibraryPose,opacity:Float=1f,castShadow:Boolean=true) = transform {
        if(journalCover(journal)==0) return@transform
        if(castShadow) closedShadow(pose,opacity)
        translate(pose.x,pose.y,pose.z)
        rotate(pose.roll,0f,0f,1f);rotate(pose.pitch,1f,0f,0f);rotate(pose.yaw,0f,1f,0f)
        Matrix.scaleM(model,0,pose.scale,pose.scale,pose.scale)
        closedBody(journal,opacity)
    }
    private fun closingRoot(clock:Float) {
        translate(0f,BookClosingGeometry.bottomOffset(clock),0f)
        val scale=BookClosingGeometry.scale(clock)
        Matrix.scaleM(model,0,scale,scale,scale)
    }
    private fun library(scene:BookScene,peersOnly:Boolean=false) {
        val candidates=shelf(scene)
        val closed=scene.copy(open=0f)
        val base=ClosedBookGeometry.base(viewportWidth,viewportHeight,closed)
        val spread=BookOpeningGeometry.spread(scene.open)
        candidates.forEach { (delta,journal) ->
            if(peersOnly && delta==0) return@forEach
            if(scene.open==0f && !BookRenderPolicy.shelfVisible(
                    ClosedBookGeometry.rect(delta,viewportWidth,viewportHeight,closed,base),viewportWidth,viewportHeight)) return@forEach
            var pose=BookShelfGeometry.pose(delta,viewportWidth,viewportHeight,closed,base)
            // Actual neighbouring volumes retreat out of frame. They never turn
            // into 2D images or fade to expose another representation underneath.
            if(delta!=0 && spread>0f) pose=pose.copy(x=pose.x+sign(delta.toFloat())*6f*spread,z=pose.z-2f*spread)
            val incoming=delta==0 && scene.creationFrom!=null && scene.creationFrom.id!=scene.journal.id
            val alpha=if(incoming) BookCreationMotion.creation(0,scene.creationProgress).alpha else 1f
            val falling=incoming && scene.creationProgress<.52f
            if(falling) {
                // Keep the incoming shadow at its landing position before the
                // cover enters from above, just as in the existing creation path.
                val landing=BookShelfGeometry.pose(0,viewportWidth,viewportHeight,scene.copy(open=0f,creationProgress=.52f))
                val strength=((scene.creationProgress-.22f)/.30f).coerceIn(0f,1f)
                closedShadow(landing,strength)
            }
            if(alpha==0f) return@forEach
            if(journal.id==scene.removingId) {
                val p=scene.removal
                closedBook(journal,pose.copy(y=pose.y-.25f*p,z=pose.z-.6f*p,scale=1-.35f*p,
                    pitch=pose.pitch+(BookLibraryGeometry.TablePitch-pose.pitch)*p),alpha*(1-p))
            } else if(delta==0 && scene.closing>=0f) transform {
                if(!falling) closedShadow(pose,alpha)
                translate(pose.x,pose.y,pose.z)
                rotate(pose.roll,0f,0f,1f);rotate(pose.pitch,1f,0f,0f);rotate(pose.yaw,0f,1f,0f)
                closingRoot(scene.closing)
                closedBody(journal,alpha)
            } else closedBook(journal,pose,alpha,castShadow=!falling)
        }
    }
    private fun boardSurface(alpha:Float,front:Boolean) {
        gpu.cull=2;draw(cover,if(front) coverTexture else whiteTexture,alpha=alpha)
        gpu.cull=1;draw(cover,if(front) whiteTexture else coverTexture,alpha=alpha)
        gpu.cull=0
    }
    private fun overview(scene:BookScene) {
        val original=(scene.current-7).coerceAtLeast(0)..(scene.current+7).coerceAtMost(scene.pages.lastIndex)
        val visible=BookOverviewGeometry.visible(viewportWidth,viewportHeight,scene.pages.size,scene.overviewAnchor,scene.overviewScroll,scene.overviewTop)
        val draggedIndex=scene.pages.indexOfFirst { it.id==scene.overviewDragId }
        val indices=((if(scene.overview<.9f) original.toList()+visible.toList() else visible.toList())+listOfNotNull(draggedIndex.takeIf { it>=0 })).distinct()
        val target=BookOverviewGeometry.targetScale(viewportWidth,viewportHeight,scene)
        data class Spread(val index:Int,val left:FoldPanelPose,val right:FoldPanelPose,val scale:Float,val alpha:Float)
        val spreads=indices.map { index ->
            val left=AlbumLeafGeometry.panel(AlbumLeafGeometry.pose(index-1,
                scene.current,0f,scene.direction,scene.selection,1f),true)
            val right=AlbumLeafGeometry.panel(AlbumLeafGeometry.pose(index,
                scene.current,0f,scene.direction,scene.selection,1f),false)
            Spread(index,BookOverviewGeometry.panel(left,true,index,scene,viewportWidth,viewportHeight,right),
                BookOverviewGeometry.panel(right,false,index,scene,viewportWidth,viewportHeight,left),
                BookOverviewGeometry.scale(scene.overview,index,scene.current,target),
                if(index in original) 1f else ((scene.overview-.12f)/.24f).coerceIn(0f,1f))
        }
        // Only a ground receiver, never an inner shadow on neighbouring paper.
        model.copyInto(sceneModel)
        val groundDepth=3.3f-2.88f*BookOverviewGeometry.shadowReceiverTravel(scene.overview)
        PaperShadowGeometry.projection(sceneModel[12],sceneModel[13],sceneModel[14]-groundDepth,0f,0f,1f,shadowProjection,lightX=-.035f,lightY=.13f)
        gpu.depthTest=false;gpu.cull=0
        val diffuse=BookOverviewGeometry.shadowReceiverTravel(scene.overview)
        if(diffuse<1f) for(spread in spreads) for(back in listOf(true,false)) {
            val pose=if(back) spread.left else spread.right
            sceneModel.copyInto(casterModel)
            Matrix.translateM(casterModel,0,pose.hinge.x,pose.hinge.y,pose.hinge.z)
            Matrix.rotateM(casterModel,0,BookOverviewGeometry.pitch(scene.overview),1f,0f,0f)
            Matrix.rotateM(casterModel,0,pose.angle,0f,1f,0f)
            Matrix.scaleM(casterModel,0,spread.scale,spread.scale,spread.scale)
            Matrix.translateM(casterModel,0,BookGeometry.HalfWidth/2,0f,0f)
            Matrix.scaleM(casterModel,0,1f,pageHeightScale,1f)
            projectedLeafShadow(BookOverviewGeometry.shadowAlpha(scene.overview,spread.index,scene.current)*spread.alpha*(1-diffuse))
        }
        if(diffuse>0f) for(spread in spreads) {
            sceneModel.copyInto(casterModel)
            Matrix.translateM(casterModel,0,spread.right.hinge.x,spread.right.hinge.y,spread.right.hinge.z)
            Matrix.rotateM(casterModel,0,BookOverviewGeometry.pitch(scene.overview),1f,0f,0f)
            // Compensate for the more distant receiver and leave transparent
            // diffusion padding outside the sheet. The artwork is never scaled.
            val diffusionScale=spread.scale*1.12f
            Matrix.scaleM(casterModel,0,diffusionScale,diffusionScale,diffusionScale)
            Matrix.multiplyMM(projectedModel,0,shadowProjection,0,casterModel,0)
            Matrix.multiplyMM(mvp,0,projection,0,projectedModel,0)
            gpu.draw(spreadShadow.id(gpu),overviewShadowTexture,mvp,projectedModel,1f,1f,1f,
                .7f*diffuse*spread.alpha,0f,1f,0f,1f,-1f)
        }
        gpu.depthTest=true
        // Real thin slabs with the existing face/rim boundary. Their content IDs
        // remain unchanged throughout entry, scrolling and selection/return.
        for(spread in spreads.sortedBy { min(it.left.hinge.z,it.right.hinge.z) }) {
            val texture=page(scene.pages[spread.index],abs(spread.index-scene.current)>1)
            albumPanel(texture,spread.left,true,1f,1f,spread.alpha,spread.scale,BookOverviewGeometry.pitch(scene.overview))
            albumPanel(texture,spread.right,false,1f,1f,spread.alpha,spread.scale,BookOverviewGeometry.pitch(scene.overview))
        }
    }
    fun draw(width:Int,height:Int,scene:BookScene,warmOpening:Boolean=false) {
        viewportWidth=width.toFloat();viewportHeight=height.toFloat()
        gpu.begin();frameTextures.clear()
        if(shelf(scene).isEmpty() && !(scene.thumbnail && scene.pages.isNotEmpty())) return
        thumbnail=scene.thumbnail
        if(!initialized) { initialize();initialized=true }
        uploadBudget=BookRenderPolicy.uploadCount(scene);prepare(scene,warmOpening)
        // Spend upload bandwidth on the selected content before distant peers.
        scene.pendingJournal?.let { journalCover(it) }
        journalCover(scene.journal)
        for((delta,journal) in shelfCandidates) if(abs(delta)==1) journalCover(journal)
        scene.pendingPage?.let { page(it) }
        if(scene.open>0f || scene.preparingOpen || scene.overview>0f) {
            scene.pages.getOrNull(scene.current)?.let { page(it) }
            scene.pages.getOrNull(scene.current+scene.direction)?.let { page(it) }
        }
        if((scene.preparingOpen || warmOpening) && scene.open==0f) prepareOpening(scene)
        // Tab re-entry creates a fresh GPU context. A missing asynchronous cover
        // is transparent, never a white substitute. Only present the real shelf.
        if(!coverReady(scene.journal)) return
        gpu.depthTest=true;gpu.cull=0
        val focal=BookGeometry.focal(width.toFloat(),height.toFloat(),scene.portrait,scene.open)
        val principalY=BookCameraGeometry.principalY(height.toFloat(),scene)
        Matrix.frustumM(projection,0,-width/(2*focal),width/(2*focal),(principalY-height)/focal,principalY/focal,1f,40f)
        Matrix.setIdentityM(model,0);translate(BookLibraryGeometry.cameraBaseX(width.toFloat(),height.toFloat(),scene),BookLibraryGeometry.cameraBaseY(width.toFloat(),height.toFloat(),scene),-BookGeometry.cameraDistance(scene.open))
        if(scene.open==0f) { library(scene);gpu.depthTest=false;return }
        library(scene,peersOnly=true)
        coverTexture=journalCover(scene.journal)
        val open=BookOpeningGeometry.spread(scene.open)
        val fan=((open-.62f)/.38f).coerceIn(0f,1f)
        closedShadow(BookShelfGeometry.pose(0,viewportWidth,viewportHeight,scene.copy(open=0f)),1-fan)
        pageHeightScale=1f+(BookGeometry.HalfHeight/.9f-1f)*open
        translate(scene.libraryOffset*1.7f,0f,0f)
        rotate(scene.roll,0f,0f,1f)
        translate(0f,BookLibraryGeometry.HoverHeight*(1-open)+(if(scene.closing>=0f) 0f else BookOpeningGeometry.lift(scene.open)),BookLibraryGeometry.HoverDepth*(1-open))
        val breathing=if(scene.closing>=0f) 1f else BookOpeningGeometry.scale(scene.open);Matrix.scaleM(model,0,breathing,breathing,breathing)
        if(scene.closing>=0f) closingRoot(scene.closing)
        rotate(BookGeometry.ClosedPitch*(1-open),1f,0f,0f);rotate(BookOpeningGeometry.yaw(scene.open),0f,1f,0f)
        translate(-.625f*(1-open),0f,0f)
        val index=scene.current.coerceIn(0,scene.pages.lastIndex.coerceAtLeast(0))
        val t=if(open==1f) scene.turn else 0f
        fun leafPose(leaf:Int)=AlbumLeafGeometry.pose(leaf,index,t,scene.direction,scene.selection,scene.open,scene.additionReveal,scene.closing)
        // The two boards remain bound to the first/last content half. No extra
        // blank endpaper or separate cover representation appears at opening.
        val rear=AlbumLeafGeometry.panel(leafPose(scene.pages.lastIndex.coerceAtLeast(0)),false)
        val boardOpacity=1f-BookOverviewGeometry.flatten(scene.overview)
        // Same boards and block as the closed shelf, continuously hinged. The
        // block compresses into the bound edge; boards remain solid under leaves.
        if(boardOpacity>0f) transform {
            translate(rear.hinge.x*fan,rear.hinge.y,rear.hinge.z*fan+BookBoardMaterial.paperBase(open))
            // A board and its attached half share one hinge and angle at every frame.
            rotate(rear.angle*open,0f,1f,0f);translate(BookGeometry.CoverWidth/2,0f,BookBoardMaterial.rearOffset(open))
            draw(coverRim,whiteTexture,r=.82f,g=.79f,b=.72f,alpha=boardOpacity)
            boardSurface(boardOpacity,false)
            transform {
                translate(.004f,0f,BookBoardMaterial.rearLinerZ(open))
                Matrix.scaleM(model,0,1f,1f,BookBoardMaterial.blockDepthScale(open))
                draw(paperRim,whiteTexture,r=.96f,g=.94f,b=.90f,alpha=boardOpacity);draw(blockFace,whiteTexture,alpha=boardOpacity)
            }
        }
        if(scene.pages.isNotEmpty() && scene.overview>0f) overview(scene)
        else if(scene.pages.isNotEmpty()) {
            val leaves=AlbumLeafGeometry.visibleLeaves(index,scene.pages.size)
            val poses=leaves.map(::leafPose)
            if(fan>.001f) albumGroundShadows(leaves,poses,index,open,fan)
            for(i in 0 until poses.lastIndex) hingeBinding(AlbumLeafGeometry.panel(poses[i],true).hinge,AlbumLeafGeometry.panel(poses[i+1],false).hinge,fan)
            // Stable adjacent half ownership through reverse, commit and rebound.
            // Fully opaque slabs may render near-to-far for early depth rejection.
            // Preserve the existing order while the fan or new paper is fading.
            val drawOrder=if(fan==1f && scene.additionReveal>=1f) leaves.sortedByDescending { k ->
                val pose=poses[k-leaves.first]
                pose.hinge.z+BookGeometry.HalfWidth*abs(sin(pose.angle*PI.toFloat()/180f))
            } else leaves.toList()
            for(k in drawOrder) {
                val central=k==index-1 || k==index
                if(!central && fan<.005f) continue
                val front=AlbumLeafGeometry.frontPage(k,scene.pages.size)?.let { page(scene.pages[it],abs(it-index)>1) } ?: whiteTexture
                val back=AlbumLeafGeometry.backPage(k,scene.pages.size)?.let { page(scene.pages[it],abs(it-index)>1) } ?: whiteTexture
                val pose=poses[k-leaves.first]
                val alpha=(if(central) 1f else fan)*if(k==index+1) scene.additionReveal else 1f
                albumPanel(front,AlbumLeafGeometry.panel(pose,false),false,open,fan,alpha)
                albumPanel(back,AlbumLeafGeometry.panel(pose,true),true,open,fan,alpha)
            }
        }
        val front=AlbumLeafGeometry.panel(leafPose(-1),true)
        if(boardOpacity>0f) transform {
            translate(front.hinge.x*fan,front.hinge.y,front.hinge.z*fan+BookBoardMaterial.paperBase(open))
            rotate(front.angle*open,0f,1f,0f)
            // The 0.045-thick board ends behind the content slab, never through it.
            translate(BookGeometry.CoverWidth/2,0f,BookBoardMaterial.frontOffset)
            draw(coverRim,whiteTexture,r=.82f,g=.79f,b=.72f,alpha=boardOpacity)
            boardSurface(boardOpacity,true)
        }
        gpu.depthTest=false
    }
}

private class PaperMesh(width:Float,height:Float,radius:Float,side:Int=0,rimDepth:Float=0f,paper:Boolean=false) {
    val isRim=rimDepth>0f
    val material=(if(isRim) 1f else 0f)+(if(paper) 2f else 0f)
    private var meshId=0
    private val geometry=BookMeshGeometry(width,height,radius,side,rimDepth)
    fun reset() { meshId=0 }
    fun id(gpu:VulkanBookBackend):Int {
        if(meshId==0) meshId=gpu.mesh(geometry.vertices,geometry.indices)
        return meshId
    }
}
