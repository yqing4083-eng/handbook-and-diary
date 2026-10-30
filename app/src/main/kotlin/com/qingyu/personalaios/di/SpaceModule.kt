package com.qingyu.personalaios.di

import android.content.Context
import com.qingyu.personalaios.data.local.SpacePreferences
import com.qingyu.personalaios.data.weather.*
import com.qingyu.personalaios.data.device.*
import com.qingyu.personalaios.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpaceModule {
    @Provides @Singleton fun personalSpace(@ApplicationContext context: Context): PersonalSpaceRepository =
        com.qingyu.personalaios.data.local.LocalPersonalSpaceRepository(context)
    @Provides @Singleton fun preferences(@ApplicationContext context: Context) = SpacePreferences(context)
    @Provides @Singleton fun diary(@ApplicationContext context: Context): DiaryRepository =
        com.qingyu.personalaios.data.local.RoomDiaryRepository(context)
    @Provides @Singleton fun location(@ApplicationContext context: Context): LocationSource = DeviceLocationSource(context)
    @Provides @Singleton fun calendar(@ApplicationContext context: Context): CalendarRepository = DeviceCalendarRepository(context)
    @Provides @Singleton fun weatherRepository(@ApplicationContext context: Context, location: LocationSource): WeatherRepository =
        LiveWeatherRepository(location,OpenMeteoDataSource(),DeviceWeatherCache(context))
}
