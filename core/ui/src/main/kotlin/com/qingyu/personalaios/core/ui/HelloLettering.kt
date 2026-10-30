package com.qingyu.personalaios.core.ui

import android.graphics.*
import androidx.core.graphics.PathParser
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.*
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.*

/** Original centre-line lettering, not an embedded Apple font or an outline wipe. */
object HelloLettering {
    data class Glyph(val advance:Float,val strokes:String)
    private fun glyph(width:Float,path:String)=Glyph(width,path)
    private val glyphs=mapOf(
        'T' to glyph(76f,"M 4 23 C 26 10 61 10 79 18 M 47 15 C 43 42 21 91 31 100 C 37 106 49 101 59 94"),
        'J' to glyph(68f,"M 22 20 C 36 12 63 10 64 22 C 59 47 51 91 30 101 C 10 111 -2 85 14 72 M 23 18 C 40 24 55 25 68 19"),
        'F' to glyph(67f,"M 15 95 C 26 63 35 27 38 14 M 13 25 C 39 10 64 10 71 18 M 19 59 C 37 54 49 54 61 57"),
        'M' to glyph(101f,"M 0 99 C 18 78 29 44 34 15 C 39 8 44 17 40 48 L 32 91 C 53 59 60 31 66 16 C 78 10 75 47 66 92 C 83 56 98 10 99 24 C 96 51 78 98 100 99"),
        'A' to glyph(82f,"M 0 97 C 27 58 53 10 62 12 C 72 19 44 81 58 97 C 64 105 75 98 83 92 M 16 66 C 31 60 54 60 72 62"),
        'S' to glyph(75f,"M 74 22 C 62 0 21 9 19 36 C 18 54 65 52 61 80 C 57 109 14 112 1 93"),
        'O' to glyph(82f,"M 65 19 C 38 -3 3 44 5 79 C 7 112 51 110 69 77 C 88 41 85 12 65 19 C 54 25 56 37 72 37"),
        'N' to glyph(91f,"M 2 99 C 16 71 30 33 34 17 C 44 0 49 37 49 61 L 48 93 C 48 108 70 68 90 13"),
        'D' to glyph(83f,"M 22 93 C 33 60 41 29 39 15 M 6 25 C 45 -5 86 13 81 53 C 77 87 45 105 8 99"),
        'a' to glyph(58f,"M 43 64 C 28 43 9 65 6 86 C 1 110 24 107 43 65 C 39 82 31 111 60 94"),
        'b' to glyph(60f,"M 1 96 C 18 79 47 27 39 16 C 29 1 18 50 13 83 C 32 51 57 59 49 82 C 42 105 20 109 13 94 C 26 109 47 105 62 94"),
        'c' to glyph(51f,"M 43 66 C 37 48 15 61 9 78 C -2 106 25 111 54 94"),
        'd' to glyph(63f,"M 43 67 C 28 42 6 65 6 88 C 4 111 32 97 45 64 C 54 41 67 9 57 13 C 44 26 34 79 42 94 C 46 103 54 101 66 94"),
        'e' to glyph(51f,"M 2 85 C 34 88 49 51 30 60 C 11 68 6 96 18 101 C 31 110 46 99 55 94"),
        'g' to glyph(62f,"M 44 66 C 27 48 5 65 7 87 C 7 110 32 96 44 65 C 37 91 17 142 5 132 C -5 122 36 107 65 94"),
        'h' to glyph(62f,"M 1 97 C 22 68 50 19 38 14 C 29 8 13 66 10 97 C 27 61 55 49 43 82 C 32 110 52 101 65 94"),
        'i' to glyph(30f,"M 1 96 C 13 78 20 63 19 65 C 6 95 9 108 34 94 M 23 47 L 24 46"),
        'k' to glyph(60f,"M 1 96 C 17 71 47 21 36 14 C 26 9 16 54 10 99 C 20 82 51 56 51 66 C 51 78 29 86 18 84 C 32 81 32 105 45 103 C 53 103 59 97 64 94"),
        'l' to glyph(36f,"M 0 96 C 24 68 44 19 31 13 C 18 8 4 69 11 94 C 14 104 24 106 40 94"),
        'm' to glyph(88f,"M 1 96 L 15 65 C 22 55 20 76 10 98 C 36 57 51 52 37 86 L 32 99 C 57 60 77 50 62 86 C 57 103 69 108 91 94"),
        'n' to glyph(62f,"M 1 96 L 16 66 C 20 58 22 66 10 99 C 37 54 56 57 42 86 C 34 106 49 106 66 94"),
        'o' to glyph(58f,"M 40 64 C 16 50 1 78 8 96 C 16 115 49 91 45 69 C 42 55 34 69 44 79 C 51 85 58 78 62 76"),
        'p' to glyph(60f,"M 0 95 L 22 60 C 17 86 3 127 -1 131 M 17 83 C 37 53 58 60 48 83 C 38 107 23 105 19 97 C 30 109 51 102 64 94"),
        'r' to glyph(50f,"M 0 96 C 14 81 20 64 22 60 C 18 78 34 63 38 66 C 43 70 23 97 36 101 C 43 103 49 98 54 94"),
        's' to glyph(52f,"M 0 96 C 12 84 24 67 29 60 C 19 79 44 80 36 96 C 28 115 9 100 16 96 C 24 105 44 102 56 94"),
        't' to glyph(41f,"M 27 39 C 23 63 3 98 17 103 C 25 108 39 100 46 94 M 9 67 L 40 62"),
        'u' to glyph(60f,"M 1 95 L 16 64 C -1 95 10 112 30 92 L 45 64 C 29 96 36 111 64 94"),
        'v' to glyph(54f,"M 0 94 C 12 81 17 69 17 63 C 10 96 18 108 32 98 C 57 78 53 58 42 66 C 36 80 47 84 58 75"),
        'y' to glyph(62f,"M 1 95 L 17 64 C 0 96 10 109 29 94 L 45 65 C 41 85 16 141 4 133 C -4 125 30 110 66 94")
    )
    fun name(month:Int)=Month.of(month).getDisplayName(TextStyle.FULL,Locale.ENGLISH)
    fun advance(word:String)=word.sumOf { glyphs.getValue(it).advance.toDouble() }.toFloat()+12f
    fun paths(word:String):List<Path> {
        var x=6f
        return buildList { word.forEach { char ->
            val glyph=glyphs.getValue(char)
            val path=checkNotNull(PathParser.createPathFromPathData(glyph.strokes))
            path.offset(x,0f)
            val measure=PathMeasure(path,false)
            do { val contour=Path();measure.getSegment(0f,measure.length,contour,true);add(contour) } while(measure.nextContour())
            x+=glyph.advance
        } }
    }
    fun colors(month:Int)=FloatArray(5) { index -> (month-1)*30f+index*42f }.map { hue -> Color.HSVToColor(floatArrayOf(hue%360,.66f,.87f)) }.toIntArray()
}


@Composable fun HelloWord(word:String,modifier:Modifier=Modifier,colors:IntArray=intArrayOf(Color.WHITE,Color.WHITE),
    retainColors:Boolean=false,durationMillis:Int=1450,onWritten:()->Unit={}) {
    val colors=if(!retainColors && LocalSpaceColors.current.foreground==androidx.compose.ui.graphics.Color.Black) intArrayOf(Color.BLACK,Color.BLACK) else colors
    val pen=remember(word) { Animatable(0f) }
    val measures=remember(word) { HelloLettering.paths(word).map { PathMeasure(it,false) } }
    val length=remember(measures) { measures.sumOf { it.length.toDouble() }.toFloat() }
    val paint=remember(word,colors.toList()) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style=Paint.Style.STROKE;strokeWidth=6f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND
        shader=LinearGradient(0f,0f,HelloLettering.advance(word),110f,colors,null,Shader.TileMode.CLAMP)
    } }
    val segment=remember { Path() }
    val written by rememberUpdatedState(onWritten)
    LaunchedEffect(word) { pen.animateTo(1f,tween(durationMillis,easing=LinearEasing));written() }
    Canvas(modifier.semantics { contentDescription=word }) {
        val canvas=drawContext.canvas.nativeCanvas
        canvas.save();canvas.scale(size.width/HelloLettering.advance(word),size.height/140f)
        var remaining=length*pen.value
        measures.forEach { measure ->
            if(remaining>0f) { segment.reset();measure.getSegment(0f,minOf(remaining,measure.length),segment,true);canvas.drawPath(segment,paint) }
            remaining-=measure.length
        }
        canvas.restore()
    }
}
