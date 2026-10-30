package com.qingyu.personalaios.data.device
import org.junit.Assert.*
import org.junit.Test
class DistrictNameTest {
    @Test fun prefersCountyOverCityAndDoesNotMistakeStreetForDistrict() {
        assertEquals("杭州 · 西湖区",DistrictName.resolve("杭州市","西湖区",null,"浙江省",null))
        assertEquals("杭州 · 西湖区",DistrictName.resolve("杭州市","翠苑街道",null,"浙江省","中国浙江省杭州市西湖区文三路"))
        assertEquals("丽水 · 青田县",DistrictName.resolve("丽水市",null,"青田县","浙江省",null))
        assertEquals("义乌市",DistrictName.resolve("义乌市",null,null,"浙江省",null))
        assertEquals("当前位置",DistrictName.resolve(null,null,null,null,null))
    }
}
