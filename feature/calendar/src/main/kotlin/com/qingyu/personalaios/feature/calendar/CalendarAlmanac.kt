package com.qingyu.personalaios.feature.calendar

import com.nlf.calendar.Solar
import java.time.LocalDate
import com.qingyu.personalaios.core.theme.FestivalCalendar

internal object CalendarAlmanac {
    fun festival(date:LocalDate):String {
        return FestivalCalendar.events(date).firstOrNull()?.name.orEmpty()
    }
    fun lunarDate(date:LocalDate):String {
        val lunar=Solar.fromYmd(date.year,date.monthValue,date.dayOfMonth).lunar
        return "${lunar.monthInChinese}月${lunar.dayInChinese}"
    }
}

internal data class DailyFortune(val word:String,val title:String,val message:String,val favor:String,val avoid:String,val colorName:String,val color:Long)
/** A local daily reflection, stable for the selected date and independent of private data. */
internal object DailyFortunes {
    private val entries=listOf(
        DailyFortune("顺","把节奏交还给自己","先做一件小事，好心情会慢慢跟上。","专注","心急","海盐蓝",0xFF8FBCCC),
        DailyFortune("晴","好事正在轻轻发生","给新的想法留一点空间，也给自己一点肯定。","尝试","设限","杏子金",0xFFE4BA7E),
        DailyFortune("安","平常的一天也珍贵","不必事事都做得完美，认真过好此刻就很好。","整理","苛责","鼠尾草",0xFFA3B9A5),
        DailyFortune("悦","遇见一点小欢喜","留意那些细小的快乐，值得把它们写下来。","记录","比较","珊瑚粉",0xFFD7A2A0),
        DailyFortune("生","新的可能正在发芽","让好奇心带你走一小步，不需要马上有答案。","学习","犹疑","嫩叶绿",0xFFADC298),
        DailyFortune("静","在留白里找到答案","把纷杂的念头放一放，听听内心真正的声音。","沉淀","勉强","雾紫",0xFFB6ABD2),
        DailyFortune("光","今天也有自己的光","你已经走了很远，别忘了欣赏眼前的风景。","表达","内耗","奶油黄",0xFFE4CD96),
        DailyFortune("和","与生活温柔相处","给重要的人一句问候，也给自己一个微笑。","分享","争执","浅湖蓝",0xFF9EC9C4),
    )
    fun forDate(date:LocalDate)=entries[Math.floorMod(date.toEpochDay(),entries.size.toLong()).toInt()]
}
