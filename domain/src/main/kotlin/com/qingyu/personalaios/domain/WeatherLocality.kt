package com.qingyu.personalaios.domain

/** General weather geography normalization, independent of any messaging service. */
object WeatherLocality {
    fun city(value:String?):String? = value?.trim()?.takeIf { it.isNotBlank() && it.length<=80 && it!="null" }
}
object WeatherCountry {
    fun normalize(value:String?):String? = value?.trim()?.uppercase(java.util.Locale.ROOT)?.takeIf { it.matches(Regex("[A-Z]{2}")) }
}
