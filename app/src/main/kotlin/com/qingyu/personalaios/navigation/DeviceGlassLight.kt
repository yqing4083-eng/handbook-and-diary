package com.qingyu.personalaios.navigation

import android.hardware.*
import android.view.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qingyu.personalaios.core.glass.GlassLightMath
import kotlin.math.*

/** One listener for the whole application; callbacks stop while the app is backgrounded. */
@Composable internal fun rememberDeviceGlassLight():State<Float> {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val angle=remember { mutableFloatStateOf(45f) }
    DisposableEffect(context,owner) {
        val manager=context.getSystemService(SensorManager::class.java)
        val sensor=manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val matrix=FloatArray(9)
        var last=0L
        var filtered=45f
        var previousRaw:Float?=null
        var amplified=45f
        val listener=object:SensorEventListener {
            override fun onAccuracyChanged(sensor:Sensor?,accuracy:Int) {}
            override fun onSensorChanged(event:SensorEvent) {
                val dt=if(last==0L) .02f else (event.timestamp-last)/1_000_000_000f
                last=event.timestamp
                var x:Float;var y:Float
                if(event.sensor.type==Sensor.TYPE_GAME_ROTATION_VECTOR || event.sensor.type==Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(matrix,event.values)
                    // Project a fixed light vector onto the current device plane.
                    x=matrix[0]*-.6f+matrix[3]*-.8f+matrix[6]*.7f
                    y=matrix[1]*-.6f+matrix[4]*-.8f+matrix[7]*.7f
                } else { x=-.6f+event.values[0]*.12f;y=-.8f-event.values[1]*.12f }
                val target=GlassLightMath.screenAngle(x,y,context.display?.rotation ?: Surface.ROTATION_0) ?: return
                previousRaw?.let { amplified+=GlassLightMath.amplify(it,target) }
                previousRaw=target
                filtered=GlassLightMath.smooth(filtered,amplified,dt)
                if(abs(filtered-angle.floatValue)>.1f) angle.floatValue=filtered
            }
        }
        fun register() { last=0L;previousRaw=null;sensor?.let { manager.registerListener(listener,it,20_000) } }
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_RESUME) register()
            if(event==Lifecycle.Event.ON_PAUSE) manager.unregisterListener(listener)
        }
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
        onDispose { manager.unregisterListener(listener);owner.lifecycle.removeObserver(observer) }
    }
    return angle
}
