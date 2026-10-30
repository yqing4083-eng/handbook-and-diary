package com.qingyu.personalaios.data.device

/** Administrative district, not a neighbourhood / street returned in subLocality. */
internal object DistrictName {
    private val district=Regex("([^省市区县街道路镇乡]{2,8}(?:自治县|自治旗|区|县|旗))")
    fun resolve(locality:String?,subLocality:String?,subAdmin:String?,admin:String?,line:String?):String {
        val county=listOfNotNull(subLocality,subAdmin).firstOrNull { it.endsWith("区") || it.endsWith("县") || it.endsWith("旗") }
            ?: line?.let { district.findAll(it).lastOrNull()?.value }
        val city=locality?.takeIf { it.isNotBlank() }
        return when {
            county!=null && city!=null && city!=county -> "${city.removeSuffix("市")} · $county"
            county!=null -> county
            else -> city ?: subAdmin ?: admin ?: "当前位置"
        }
    }
}
