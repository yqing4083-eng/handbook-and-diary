package com.qingyu.personalaios.data.device

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.qingyu.personalaios.domain.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class DeviceCalendarRepository(private val context: Context): CalendarRepository {
    override fun observe(date: LocalDate,zone: ZoneId): Flow<CalendarState> = callbackFlow {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED) {
            trySend(CalendarState());awaitClose { };return@callbackFlow
        }
        val resolver=context.contentResolver
        var query: Job?=null
        fun refresh() { query?.cancel();query=launch(Dispatchers.IO) {
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALENDAR)!=PackageManager.PERMISSION_GRANTED) {
                trySend(CalendarState());return@launch
            }
            try {
                // Instances expands recurring events and exceptions using the system provider.
                val start=date.atStartOfDay(zone).toInstant().toEpochMilli()
                val end=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val uri=CalendarContract.Instances.CONTENT_URI.buildUpon().also { ContentUris.appendId(it,start-86_400_000);ContentUris.appendId(it,end+86_400_000) }.build()
                val events=mutableListOf<CalendarEvent>()
                resolver.query(uri,arrayOf(CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.ALL_DAY),
                    "${CalendarContract.Instances.VISIBLE} = 1 AND (${CalendarContract.Instances.STATUS} IS NULL OR ${CalendarContract.Instances.STATUS} != ?)",
                    arrayOf(CalendarContract.Events.STATUS_CANCELED.toString()),"${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                    while(cursor.moveToNext()) {
                        val begin=Instant.ofEpochMilli(cursor.getLong(2));val finish=Instant.ofEpochMilli(cursor.getLong(3));val all=cursor.getInt(4)!=0
                        val event=CalendarEvent("${cursor.getLong(0)}:${begin.toEpochMilli()}",cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "未命名日程",begin,finish,all)
                        if(CalendarRules.overlaps(event,date,zone)) events+=event
                    }
                }
                trySend(CalendarState(events.distinctBy { it.id },true))
            } catch(_: SecurityException) { trySend(CalendarState()) }
            catch(_: Exception) { trySend(CalendarState(granted=true,message="日历暂时无法读取，点击重试")) }
        } }
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { refresh() } }
        try { resolver.registerContentObserver(CalendarContract.CONTENT_URI,true,observer) }
        catch(_: SecurityException) { trySend(CalendarState());awaitClose { };return@callbackFlow }
        refresh()
        awaitClose { query?.cancel();runCatching { resolver.unregisterContentObserver(observer) } }
    }.catch { emit(CalendarState(message="日历暂时无法读取，点击重试")) }.distinctUntilChanged()
}
