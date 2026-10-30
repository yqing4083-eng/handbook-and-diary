package com.qingyu.personalaios.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.qingyu.personalaios.domain.*
import java.io.IOException
import kotlinx.coroutines.flow.*

private val Context.spacePreferences by preferencesDataStore("space_preferences")

data class SpaceSettings(
    val weather: WeatherTheme = WeatherTheme.SNOW,
    val period: DayPeriod? = null,
    val quality: GraphicsQuality = GraphicsQuality.HIGH,
    val reducedMotion: Boolean = false,
    val glass: GlassParameters = GlassParameters(),
    val background: String? = null,
    val nightMode: Boolean = true,
    val glassGroups: GlassGroups = GlassGroups(),
)

class SpacePreferences(context: Context) {
    private val app = context.applicationContext
    private val store = app.spacePreferences
    private val background = stringPreferencesKey("custom_background")
    private val nightMode = booleanPreferencesKey("night_mode")
    private val profileName=stringPreferencesKey("profile_name")
    val userName:Flow<String> = store.data.catch { error -> if(error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[profileName]?.takeIf(String::isNotBlank) ?: "Doki" }
    suspend fun setUserName(value:String) { require(value.isNotBlank() && value.length<=32);store.edit { it[profileName]=value.trim() } }
    private val weather = stringPreferencesKey("preview_weather")
    private val period = stringPreferencesKey("preview_period")
    private val quality = stringPreferencesKey("graphics_quality")
    private val reduced = booleanPreferencesKey("reduced_motion")
    private val refraction = floatPreferencesKey("glass_refraction")
    private val blur = floatPreferencesKey("glass_blur")
    private val highlight = floatPreferencesKey("glass_highlight")
    private val magnification = floatPreferencesKey("glass_magnification")
    private val tint = floatPreferencesKey("glass_tint")
    private val chromatic = booleanPreferencesKey("glass_chromatic")
    private val interaction = floatPreferencesKey("glass_interaction")
    private val chromaticStrength=floatPreferencesKey("glass_chromatic_strength")
    private val materialRevision=intPreferencesKey("material_preset_revision")

    val settings: Flow<SpaceSettings> = store.data.onStart {
        store.edit { values ->
            if(values[materialRevision]!=1) {
                val existing=GlassTarget.entries.fold(GlassGroups()) { groups,target -> groups.updated(target,readGlass(values,target)) }
                GlassGroups.presetUpdate(existing).forEach { (target,parameters) -> writeGlass(values,target,parameters) }
                values[materialRevision]=1
            }
        }
    }.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        SpaceSettings(
            WeatherTheme.entries.firstOrNull { it.name == values[weather] } ?: WeatherTheme.SNOW,
            null,
            GraphicsQuality.HIGH,
            false,
            GlassParameters(
                values[refraction] ?: 1.8f, values[blur] ?: 4f,
                values[highlight] ?: .85f, values[magnification] ?: 1.12f,
                values[tint] ?: .07f, values[chromatic] ?: true,
                values[interaction] ?: 1f,
                values[chromaticStrength] ?: 1f,
            ).normalized(),
            values[background],
            values[nightMode] ?: true,
            GlassGroups().let { defaults ->
                GlassTarget.entries.fold(defaults) { groups,target -> groups.updated(target,readGlass(values,target)) }
            },
        )
    }

    suspend fun setWeather(value: WeatherTheme) { store.edit { it[weather] = value.name } }
    suspend fun setNightMode(value:Boolean) { store.edit { it[nightMode]=value } }
    suspend fun setPeriod(value: DayPeriod?) { store.edit { if (value == null) it.remove(period) else it[period] = value.name } }
    suspend fun setQuality(value: GraphicsQuality) { store.edit { it[quality] = value.name } }
    suspend fun setReducedMotion(value: Boolean) { store.edit { it[reduced] = value } }
    suspend fun setGlass(value: GlassParameters) {
        val safe = value.normalized()
        store.edit {
            it[refraction] = safe.refraction
            it[blur] = safe.blur
            it[highlight] = safe.highlight
            it[magnification] = safe.magnification
            it[tint] = safe.tint
            it[chromatic] = safe.chromaticAberration
            it[interaction] = safe.interaction
            it[chromaticStrength]=safe.chromaticStrength
        }
    }

    private fun glassKey(target:GlassTarget,field:String)="glass_${target.name.lowercase(java.util.Locale.ROOT)}_$field"
    private fun readGlass(values:Preferences,target:GlassTarget):GlassParameters {
        val defaults=GlassGroups.defaultFor(target)
        fun number(field:String,fallback:Float)=values[floatPreferencesKey(glassKey(target,field))] ?: fallback
        return GlassParameters(number("refraction",defaults.refraction),number("blur",defaults.blur),
            number("highlight",defaults.highlight),number("magnification",defaults.magnification),
            number("tint",defaults.tint),values[booleanPreferencesKey(glassKey(target,"chromatic"))] ?: defaults.chromaticAberration,
            number("interaction",defaults.interaction),number("chromatic_strength",defaults.chromaticStrength)).normalized()
    }

    /** Merge only edited groups in one transaction; older hidden global values stay inactive. */
    suspend fun setGlassGroups(changes:Map<GlassTarget,GlassParameters>) {
        store.edit { values -> changes.forEach { (target,parameters) -> writeGlass(values,target,parameters) } }
    }
    private fun writeGlass(values:MutablePreferences,target:GlassTarget,parameters:GlassParameters) {
            val safe=parameters.normalized()
            mapOf("refraction" to safe.refraction,"blur" to safe.blur,"highlight" to safe.highlight,
                "magnification" to safe.magnification,"tint" to safe.tint,"interaction" to safe.interaction,
                "chromatic_strength" to safe.chromaticStrength).forEach { (field,value) ->
                values[floatPreferencesKey(glassKey(target,field))]=value
            }
            values[booleanPreferencesKey(glassKey(target,"chromatic"))]=safe.chromaticAberration
    }

    suspend fun setBackground(source: String?) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val old=store.data.first()[background]
        var file: java.io.File?=null
        var committed=false
        try {
            if(source!=null) {
                val bitmap=android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(app.contentResolver,android.net.Uri.parse(source))) { decoder,info,_ ->
                    val ratio=minOf(1f,2048f/maxOf(info.size.width,info.size.height))
                    decoder.setTargetSize(maxOf(1,(info.size.width*ratio).toInt()),maxOf(1,(info.size.height*ratio).toInt()))
                    decoder.allocator=android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val target=java.io.File(app.filesDir,"background-${java.util.UUID.randomUUID()}.webp")
                file=target
                try { target.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY,90,it)) } } finally { bitmap.recycle() }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                store.edit { if(file==null) it.remove(background) else it[background]=file!!.absolutePath }
                committed=true
                old?.let { java.io.File(it).takeIf { previous -> previous.parentFile==app.filesDir && previous.name.startsWith("background-") }?.delete() }
            }
        } catch(error:Throwable) { if(!committed) file?.delete();throw error }
    }
}
