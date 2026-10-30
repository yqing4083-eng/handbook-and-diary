package com.qingyu.personalaios.domain

/** Decoration marks a tape pattern paints inside its own box; coordinates are a fraction of that box. */
enum class TapeMark { BAND, DOT, LINE }

data class TapeOp(
    val mark: TapeMark,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val alpha: Float,
)

/** One pattern serves both renderers, so screen and paper export can never drift apart. */
fun tapeOps(pattern: TapePattern): List<TapeOp> = when (pattern) {
    TapePattern.SOLID -> emptyList()
    TapePattern.STRIPE -> List(7) { i -> TapeOp(TapeMark.BAND, .06f + .13f * i, 0f, .05f, 1f, .28f) }
    TapePattern.DOT -> List(8) { i -> TapeOp(TapeMark.DOT, .14f + .24f * (i % 4), .28f + .36f * (i / 4), .09f, .09f, .3f) }
    TapePattern.CHECK ->
        List(4) { i -> TapeOp(TapeMark.BAND, .05f + .24f * i, 0f, .11f, 1f, .16f) } +
            List(3) { i -> TapeOp(TapeMark.BAND, 0f, .2f + .3f * i, 1f, .11f, .16f) }
    TapePattern.LACE ->
        List(5) { i -> TapeOp(TapeMark.DOT, .05f + .19f * i, 0f, .16f, .16f, .26f) } +
            List(5) { i -> TapeOp(TapeMark.LINE, .05f + .19f * i, .05f, .19f, 0f, .3f) }
}

data class CollageSlot(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Even grid for a collage: columns and rows shrink until every cell keeps the smallest legal
 * element size, so a 6x6 request on a narrow page degrades instead of producing unusable cells.
 */
fun collageSlots(count: Int, columns: Int, rows: Int, padding: Float = .05f, gap: Float = .03f): List<CollageSlot> {
    var cols = columns.coerceIn(1, 6)
    var lines = rows.coerceIn(1, 6)
    fun cell(axis: Int) = (1f - 2 * padding - (axis - 1) * gap) / axis
    while (cols > 1 && cell(cols) < .08f) cols--
    while (lines > 1 && cell(lines) < .08f) lines--
    val width = cell(cols)
    val height = cell(lines)
    return List(count.coerceIn(0, cols * lines)) { i ->
        val column = i % cols
        val line = i / cols
        CollageSlot(padding + column * (width + gap), padding + line * (height + gap), width, height)
    }
}

/** User supplied, transparent sticker catalogue. Stable names never depend on sorting. */
enum class StickerArt(val label:String,val group:String,val sheet:Int,val left:Int,val top:Int,val right:Int,val bottom:Int) {
    S00_00("抱鱼猫","伙伴",0,13,187,316,463),
    S00_01("草莓狗","伙伴",0,362,230,640,463),
    S00_02("企鹅","伙伴",0,680,229,940,452),
    S00_03("困困猫","伙伴",0,948,210,1225,458),
    S00_04("饼干猫","伙伴",0,1288,241,1518,464),
    S00_05("荷叶蛙","伙伴",0,39,552,287,834),
    S00_06("打滚猫","伙伴",0,339,576,655,838),
    S00_07("萝卜兔","伙伴",0,655,575,925,835),
    S00_08("毛毯猫","伙伴",0,947,574,1249,835),
    S00_09("香蕉猫","伙伴",0,1280,558,1518,854),
    S01_00("咖啡狗","慢生活",1,31,193,313,436),
    S01_01("海豹","慢生活",1,344,250,631,399),
    S01_02("发芽土豆","慢生活",1,675,187,909,417),
    S01_03("桃子","慢生活",1,954,221,1203,410),
    S01_04("蜗牛","慢生活",1,1249,183,1505,430),
    S01_05("鸽子","慢生活",1,37,614,297,814),
    S01_06("煎蛋","慢生活",1,362,588,616,810),
    S01_07("纸飞机","慢生活",1,675,621,904,800),
    S01_08("袜子","慢生活",1,969,587,1194,832),
    S01_09("乌龟","慢生活",1,1250,638,1509,814),
    S02_00("暖咖狗","手绘",2,43,123,431,358),
    S02_01("灰海豹","手绘",2,494,162,891,361),
    S02_02("小嫩芽","手绘",2,972,86,1273,381),
    S02_03("害羞桃","手绘",2,63,472,406,734),
    S02_04("慢慢蜗牛","手绘",2,500,485,862,734),
    S02_05("散步鸽","手绘",2,971,495,1316,727),
    S02_06("荷包蛋","手绘",2,50,829,339,1091),
    S02_07("蓝飞机","手绘",2,359,846,652,1076),
    S02_08("暖袜子","手绘",2,693,834,911,1091),
    S02_09("小乌龟","手绘",2,985,857,1324,1074),
    S03_00("小胡子","像素",3,169,485,1085,798),
    S04_00("垂耳兔","像素",4,172,323,1082,932),
    S05_00("粉蝴蝶结","像素",5,72,294,1183,1007),
    S06_00("恶魔猫","像素",6,232,330,1152,929),
    S07_00("幸运草","像素",7,237,299,1033,964),
    S08_00("天使猫","像素",8,117,302,1138,953),
    S09_00("草莓猫","像素",9,226,280,1053,973),
    S10_00("瞌睡云","像素",10,271,246,1036,1047),
    S11_00("猫耳眼镜","像素",11,71,389,1183,869),
    S12_00("月亮猫","像素",12,187,270,1092,1053),
}
fun stickerFile(art:StickerArt)="sticker-${art.name.lowercase()}.png"

/** Keep the transparent cutout's aspect, including very wide accessories. */
fun stickerSize(art:StickerArt,canvasAspect:Float):Pair<Float,Float> {
    val ratio=(art.bottom-art.top).toFloat()/(art.right-art.left)*canvasAspect
    val width=.32f.coerceIn(maxOf(.081f,.081f/ratio),minOf(1f,.36f/ratio))
    return width to width*ratio
}
