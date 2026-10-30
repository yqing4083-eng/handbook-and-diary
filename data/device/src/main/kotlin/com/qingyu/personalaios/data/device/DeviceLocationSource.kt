package com.qingyu.personalaios.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.qingyu.personalaios.domain.*
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume

class DeviceLocationSource(private val context: Context): LocationSource {
    @Suppress("MissingPermission") override suspend fun current(): GeoPosition {
        val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!coarse && !fine) throw SecurityException()
        val manager = context.getSystemService(LocationManager::class.java)
        if(!manager.isLocationEnabled) error("系统定位已关闭，开启后再试")
        val providers=manager.getProviders(true).filter { it!=LocationManager.PASSIVE_PROVIDER && (fine || it!=LocationManager.GPS_PROVIDER) }
            .sortedBy { if(it==LocationManager.FUSED_PROVIDER) 0 else if(it==LocationManager.NETWORK_PROVIDER) 1 else 2 }
        fun age(location:Location)=(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos).coerceAtLeast(0)/1_000_000L
        val target=if(fine) 80f else 2000f
        val cached=(providers+LocationManager.PASSIVE_PROVIDER).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { age(it)<5*60_000 && it.hasAccuracy() && it.accuracy<=5000f }.minByOrNull { it.accuracy }
        val result=if(cached!=null && age(cached)<30_000 && cached.accuracy<=target) cached else coroutineScope {
            val results=kotlinx.coroutines.channels.Channel<Location?>(kotlinx.coroutines.channels.Channel.UNLIMITED)
            var best=cached
            val jobs=providers.map { provider -> launch {
                val fix=runCatching { suspendCancellableCoroutine<Location?> { cont ->
                    val signal=CancellationSignal();cont.invokeOnCancellation { signal.cancel() }
                    val request=LocationRequest.Builder(0).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                        .setDurationMillis(18_000).setMaxUpdates(1).build()
                    manager.getCurrentLocation(provider,request,signal,context.mainExecutor) { value -> if(cont.isActive) cont.resume(value) }
                } }.getOrElse { error -> if(error is CancellationException) throw error;null }
                results.send(fix)
            } }
            try {
                withTimeoutOrNull(18_000) {
                    repeat(providers.size) {
                        val fix=results.receive()
                        if(fix!=null && age(fix)<5*60_000 && fix.hasAccuracy()) {
                            if(best==null || fix.accuracy<best!!.accuracy) best=fix
                            if(age(fix)<60_000 && fix.accuracy<=target) return@withTimeoutOrNull fix
                        }
                    }
                    best
                } ?: best ?: error("定位超时，请到信号较好的位置重试")
            } finally { jobs.forEach { it.cancel() };results.close() }
        }
        val unknown=Triple("当前位置",null,null)
        val place = if(Geocoder.isPresent()) withTimeoutOrNull(3_000) { suspendCancellableCoroutine<Triple<String,String?,String?>> { cont ->
            try { Geocoder(context,Locale.SIMPLIFIED_CHINESE).getFromLocation(result.latitude,result.longitude,1,object: Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) { if(cont.isActive) cont.resume(addresses.firstOrNull()?.let {
                    Triple(DistrictName.resolve(it.locality,it.subLocality,it.subAdminArea,it.adminArea,it.getAddressLine(0)),
                        (WeatherLocality.city(it.locality) ?: it.adminArea?.takeIf { area -> area in setOf("北京市","上海市","天津市","重庆市") }?.let(WeatherLocality::city)),WeatherCountry.normalize(it.countryCode))
                } ?: unknown) }
                override fun onError(errorMessage: String?) { if(cont.isActive) cont.resume(unknown) }
            }) } catch(_:Exception) { if(cont.isActive) cont.resume(unknown) }
        } } ?: unknown else unknown
        return GeoPosition(result.latitude,result.longitude,place.first,place.second,place.third)
    }
}
