package com.qingyu.personalaios.core.glass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test
class LiquidMotionTest {
 @Test fun amplifiedPoseUsesShortestArcAndStrongerGain() {
  assertEquals(4.8f,GlassLightMath.amplify(179f,-179f),.001f)
  assertEquals(-4.8f,GlassLightMath.amplify(-179f,179f),.001f)
  assertEquals(0f,GlassLightMath.amplify(45f,Float.NaN),0f)
 }
 @Test fun horizontalPoseMovesLightAndScreenRotationsKeepItsDirectionConsistent() {
  assertTrue(GlassLightMath.screenAngle(-.8f,-.8f,0)!!<GlassLightMath.screenAngle(-.2f,-.8f,0)!!)
  for(i in 0..3) assertEquals(45f+90f*i,GlassLightMath.screenAngle(-1f,-1f,i)!!,.001f)
  assertNull(GlassLightMath.screenAngle(Float.NaN,1f,0))
  assertNull(GlassLightMath.screenAngle(0f,0f,0))
 }
 @Test fun lightTakesShortestArcAcrossTheWrapBoundary() {
  val forward=GlassLightMath.smooth(179f,-179f,.016f)
  assertTrue(forward>179f && forward<181f)
  val reverse=GlassLightMath.smooth(-179f,179f,.016f)
  assertTrue(reverse < -179f && reverse > -181f)
 }
 @Test fun badSensorReadingDoesNotPoisonAllGlass() { assertEquals(45f,GlassLightMath.smooth(45f,Float.NaN,.016f),0f) }
 @Test fun droppedSensorFramesHaveBoundedResponse() { assertEquals(GlassLightMath.smooth(45f,90f,.1f),GlassLightMath.smooth(45f,90f,10f),.001f) }
 @Test fun menuReturnsToExactlyTheClickedControl() {
  val a=Rect(10f,400f,54f,444f);val b=Rect(12f,170f,188f,378f)
  assertEquals(a,LiquidMenuMotion.bounds(a,b,0f,22f))
  val end=LiquidMenuMotion.bounds(a,b,1f,22f)
  assertEquals(b.left,end.left,.001f);assertEquals(b.bottom,end.bottom,.001f)
 }
 @Test fun compactButtonsNeverCollapseWhenAFormSpringOvershoots() {
  val a=Rect(0f,700f,44f,744f);val b=Rect(16f,80f,376f,680f)
  for(i in -10..115) {
   val q=LiquidMenuMotion.bounds(a,b,i/100f,22f)
   assertTrue("collapsed at $i: $q",q.width>=a.width*.82f-.001f && q.height>=a.height*.94f-.001f)
   assertTrue("oversized at $i: $q",q.width<=b.width*1.04f+.001f && q.height<=b.height*1.08f+.001f)
   assertTrue(q.left.isFinite())
  }
 }
 @Test fun returningMidExpansionKeepsTheLastRenderedBoundsAndCorner() {
  val button=Rect(20f,700f,64f,744f);val menu=Rect(16f,430f,248f,654f)
  val progress=mutableStateOf(.43f);val visibility=mutableStateOf(.36f)
  val opened=GlassMorphFrame(progress,visibility,button,GlassGeometry.Circle,button,null).apply { target=menu }
  val sampled=opened.bounds();val corner=opened.corner(Density(1f))
  val closing=GlassMorphFrame(mutableStateOf(0f),visibility,sampled,GlassGeometry.Corner(corner,opened.roundness()),button,null,true).apply { target=menu }
  assertEquals(sampled,closing.bounds());assertEquals(corner,closing.corner(Density(1f)))
  assertEquals(.36f,closing.visibility.value,0f)
  assertEquals(opened.roundness(),closing.roundness(),0f)
 }
 @Test fun lateMeasurementsCannotRedirectTheReturningBodyAwayFromItsButton() {
  val button=Rect(20f,700f,64f,744f)
  val frame=GlassMorphFrame(mutableStateOf(1f),mutableStateOf(0f),Rect(16f,430f,248f,654f),GlassGeometry.Corner(30.dp),button,null,true)
  frame.target=Rect(60f,40f,350f,390f)
  assertEquals(button,frame.bounds())
 }
 @Test fun nestedPanelRebasesAtTheCurrentContourAndRetainsItsOriginalReturnAnchor() {
  val button=Rect(20f,700f,64f,744f);val current=Rect(16f,430f,248f,654f)
  val next=GlassMorphFrame(mutableStateOf(0f),mutableStateOf(1f),current,GlassGeometry.Corner(30.dp),button,null).apply { target=Rect(16f,150f,376f,650f) }
  assertEquals(current,next.bounds());assertEquals(button,next.returnAnchor)
 }
 @Test fun topControlsOpenBelowAndBottomControlsOpenAbove() {
  val safe=Rect(16f,16f,396f,800f);val menu=Size(232f,224f)
  val top=Rect(300f,20f,344f,64f)
  assertEquals(74f,LiquidMenuMotion.place(top,menu,safe,10f).y,0f)
  val bottom=Rect(20f,740f,64f,784f)
  assertEquals(506f,LiquidMenuMotion.place(bottom,menu,safe,10f).y,0f)
 }
 @Test fun edgeControlsAndLargeMenusStayEntirelyWithinTheAvailableViewport() {
  val safe=Rect(16f,16f,396f,800f)
  for(anchor in listOf(Rect(0f,0f,44f,44f),Rect(390f,750f,434f,794f))) {
   for(menu in listOf(Size(232f,224f),Size(380f,784f))) {
    val point=LiquidMenuMotion.place(anchor,menu,safe,10f)
    assertTrue(point.x>=safe.left && point.y>=safe.top)
    assertTrue(point.x+menu.width<=safe.right && point.y+menu.height<=safe.bottom)
   }
  }
 }
 @Test fun conversationMenusPreferBelowWithoutCoveringHeaderAndFallBackAtBottom() {
  val safe=Rect(16f,16f,396f,800f);val menu=Size(144f,108f)
  assertEquals(274f,LiquidMenuMotion.place(Rect(16f,200f,300f,264f),menu,safe,10f,true).y,0f)
  assertEquals(622f,LiquidMenuMotion.place(Rect(16f,740f,300f,784f),menu,safe,10f,true).y,0f)
 }
 @Test fun bottomOptionAppearsBeforeTopAndAllOptionsAreVisibleAtRest() {
  assertTrue(LiquidMenuMotion.optionReveal(.6f,0f)>LiquidMenuMotion.optionReveal(.6f,3f))
  assertEquals(1f,LiquidMenuMotion.optionReveal(1f,3f),0f)
  assertEquals(1f,LiquidMenuMotion.optionReveal(1f,20f),0f)
 }
}
