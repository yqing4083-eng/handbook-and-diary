package com.qingyu.personalaios.core.theme

import com.nlf.calendar.Solar
import java.time.LocalDate

enum class FestivalMotif { CHRISTMAS, LANTERN, MOON, BOAT, HEART, STAR, BALLOON, FLOWER, BOOK, PUMPKIN, MAPLE, WILLOW, SNOW, RIBBON }
data class FestivalEvent(val name:String,val introduction:String,val motif:FestivalMotif,
    val accent:Long,val companion:Long,val solarTerm:Boolean=false)
data class FestivalTheme(val event:FestivalEvent)

/** Civil-date/lunar rules shared by the calendar and every page; no system clock changes. */
object FestivalCalendar {
    private val cache=object:LinkedHashMap<LocalDate,List<FestivalEvent>>(96,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<LocalDate,List<FestivalEvent>>?)=size>96
    }
    @Synchronized fun events(date:LocalDate):List<FestivalEvent> = cache.getOrPut(date) {
        val solar=Solar.fromYmd(date.year,date.monthValue,date.dayOfMonth)
        val lunar=solar.lunar
        val additions=when(date.monthValue to date.dayOfMonth) {
            12 to 24 -> listOf("平安夜")
            12 to 25 -> listOf("圣诞节")
            10 to 31 -> listOf("万圣节")
            else -> emptyList()
        }
        val names=(lunar.festivals+solar.festivals+additions).distinct()
        names.map { describe(it) }+lunar.jieQi.orEmpty().takeIf { term -> term.isNotEmpty() && names.none { it.removeSuffix("节")==term } }?.let { listOf(describeTerm(it)) }.orEmpty()
    }
    fun theme(date:LocalDate):FestivalTheme?=events(date).firstOrNull()?.let(::FestivalTheme)

    private fun describe(name:String):FestivalEvent {
        fun event(text:String,motif:FestivalMotif,accent:Long,companion:Long)=FestivalEvent(name,text,motif,accent,companion)
        return when {
            "圣诞" in name || "平安夜" in name -> event("圣诞时节，人们用常青枝、灯饰和礼物传递祝福。愿今天的温暖，留在你写下的每一页里。",FestivalMotif.CHRISTMAS,0xFFE54850,0xFF58AF8A)
            "除夕" in name -> event("除夕是农历岁末的团圆之夜。年夜饭、守岁和辞旧迎新，让旧年的故事有了温暖的收尾。",FestivalMotif.LANTERN,0xFFEF4D4F,0xFFFFC568)
            "春节" in name -> event("春节开启农历新年。贴春联、拜年与团聚，寄托着对新一年的平安和美好期盼。",FestivalMotif.LANTERN,0xFFEF4D4F,0xFFFFC568)
            "元宵" in name -> event("农历正月十五是元宵节。赏灯、猜灯谜、吃汤圆，把新春的团圆与热闹延续下去。",FestivalMotif.LANTERN,0xFFFF9865,0xFFFFD67C)
            "中秋" in name -> event("中秋以明月寄托团圆。赏月、分享月饼，也适合给思念的人留下一句问候。",FestivalMotif.MOON,0xFFE8BE71,0xFF91B8DF)
            "端午" in name -> event("端午在农历五月初五。粽叶、龙舟与艾草承载着传统记忆，也寄托着安康的祝愿。",FestivalMotif.BOAT,0xFF69B593,0xFFE8CD8B)
            "清明" in name -> event("清明兼具节气与传统节日的意义。慎终追远，也在春光里踏青，珍惜此刻相伴的人。",FestivalMotif.WILLOW,0xFF8CB9A0,0xFFC6D99D)
            "重阳" in name -> event("农历九月初九是重阳。登高、赏菊与敬老的习俗，提醒我们把陪伴留给长辈。",FestivalMotif.FLOWER,0xFFD9B269,0xFFE8CC93)
            "七夕" in name -> event("七夕在农历七月初七，源于乞巧等传统习俗，也承载着对美好情感的祝愿。",FestivalMotif.HEART,0xFFD78CA8,0xFFE5B8CF)
            "腊八" in name -> event("农历腊月初八是腊八节。一碗热粥，让岁末的等待与年味渐渐温暖起来。",FestivalMotif.SNOW,0xFFD4A773,0xFFEBD5B8)
            "国庆" in name -> event("国庆节在每年十月一日。用一段旅程、一场相聚，或一页记录，留下属于今天的记忆。",FestivalMotif.STAR,0xFFE86457,0xFFF3C66D)
            "元旦" in name -> event("元旦是公历新年的第一天。把愿望写下来，让新的开始有一个轻盈的方向。",FestivalMotif.RIBBON,0xFF9DB9DF,0xFFF0C78A)
            "劳动" in name -> event("劳动节向日常的付出致意。认真生活的人，也值得拥有一段安心休息的时光。",FestivalMotif.RIBBON,0xFFE8A877,0xFFB7D2BB)
            "妇女" in name -> event("国际妇女节关注女性的成就与平等。愿每一种选择都被尊重，每一种成长都拥有空间。",FestivalMotif.FLOWER,0xFFD89DB4,0xFFEBC5D4)
            "儿童" in name -> event("儿童节属于童真与想象。留一点好奇心，让今天也有轻快、明亮的小快乐。",FestivalMotif.BALLOON,0xFF87C5E4,0xFFF2C976)
            "教师" in name -> event("教师节表达对教育与陪伴成长的感谢。也许一句问候，就能把记忆中的温暖再次点亮。",FestivalMotif.BOOK,0xFFB5BADA,0xFFE4C799)
            "母亲" in name -> event("母亲节向母亲与日常照料者表达感谢。把平时没说出口的关心，变成今天的一次陪伴。",FestivalMotif.FLOWER,0xFFE1A5B7,0xFFF2CFD7)
            "父亲" in name -> event("父亲节适合向父亲表达关心与感谢。一通电话、一顿饭，也能让朴素的心意被听见。",FestivalMotif.BOOK,0xFF8FAFC6,0xFFC8D9E1)
            "情人" in name -> event("情人节让心意有了表达的机会。给珍惜的人一句真诚的话，也别忘了好好照顾自己。",FestivalMotif.HEART,0xFFDE8FA5,0xFFF1BCC8)
            "万圣" in name -> event("万圣节常以南瓜灯、装扮与糖果营造想象力十足的夜晚。给今天留一点有趣的小惊喜。",FestivalMotif.PUMPKIN,0xFFF1A25B,0xFFB1A0CE)
            "感恩" in name -> event("感恩节让人们想起值得感谢的人与事。把一个温暖的名字写下来，或直接说声谢谢。",FestivalMotif.MAPLE,0xFFD5A16F,0xFFE8C294)
            "植树" in name -> event("植树节提醒我们关心身边的绿色。种下、照料与耐心等待，都是让生活生长的方式。",FestivalMotif.WILLOW,0xFF8CB9A0,0xFFBCD79D)
            "青年" in name -> event("青年节致意年轻的理想与行动。为想做的事迈出一小步，让今天成为新的起点。",FestivalMotif.RIBBON,0xFF8ABED2,0xFFEBC892)
            "建军" in name -> event("八一建军节向守护与奉献致意。珍惜安宁的日常，也记得那些承担责任的人。",FestivalMotif.STAR,0xFFC99F72,0xFF9EB69A)
            "建党" in name -> event("七一是中国共产党成立纪念日。回望历史，也珍惜今天的生活与身边的相伴。",FestivalMotif.STAR,0xFFE86457,0xFFF3C66D)
            "护士" in name -> event("国际护士节向护理工作者致意。细致的照护与耐心的陪伴，让许多平凡时刻拥有力量。",FestivalMotif.FLOWER,0xFF97C8CE,0xFFD7E5DF)
            "消费者" in name -> event("国际消费者权益日关注公平交易与消费者权益。理性选择，也为自己的需要留出空间。",FestivalMotif.BOOK,0xFF9BB9D0,0xFFD6CBB5)
            "愚人" in name -> event("愚人节常以轻松的玩笑带来趣味。让幽默保持善意，把快乐留给彼此。",FestivalMotif.BALLOON,0xFFA8BADD,0xFFE7BE85)
            "地球" in name || "环境" in name -> event("今天把目光留给我们共同生活的环境。从一个小小的日常选择开始，关心自然，也关心未来。",FestivalMotif.WILLOW,0xFF8CB9A0,0xFF9DBED6)
            else -> {
                val palette=listOf(0xFFA9BFDE to 0xFFE8C58F,0xFFD6A4BD to 0xFFB9CDE2,0xFF9FC9BC to 0xFFE4C89B)
                val colors=palette[Math.floorMod(name.hashCode(),palette.size)]
                event("今天是$name。为这个特别的日子留下一段记录，也把一句祝福送给你珍惜的人。",FestivalMotif.RIBBON,colors.first,colors.second)
            }
        }
    }

    private fun describeTerm(name:String):FestivalEvent {
        val texts=mapOf(
            "立春" to "春意开始萌动，万物等待新的生长。", "雨水" to "雨意渐浓，空气里多了一分柔润。",
            "惊蛰" to "春雷唤醒生机，日子渐渐活泼起来。", "春分" to "昼夜渐趋均衡，春色向更远处展开。",
            "清明" to "春光明净，适合踏青，也适合怀念。", "谷雨" to "春雨滋养新绿，春天走向丰盛。",
            "立夏" to "夏意初至，光线与绿意都更明亮。", "小满" to "万物渐盈，成长也可以从容一点。",
            "芒种" to "耕耘与收获交接，让付出慢慢生长。", "夏至" to "白昼来到一年里的长处，留意身边的光。",
            "小暑" to "暑意渐起，为生活留一处清凉。", "大暑" to "盛夏热烈，也记得给自己片刻休息。",
            "立秋" to "秋意开始酝酿，夏天仍在慢慢告别。", "处暑" to "暑气渐退，日子向清爽过渡。",
            "白露" to "晨露与凉意相伴，早晚记得添衣。", "秋分" to "秋色渐深，在均衡的节奏里安放自己。",
            "寒露" to "露意渐凉，秋天多了一层安静。", "霜降" to "秋意更深，把温暖留在日常里。",
            "立冬" to "冬天启程，适合把生活过得暖一点。", "小雪" to "寒意渐浓，静候冬日的小小变化。",
            "大雪" to "冬意渐深，愿心里仍有明亮的暖意。", "冬至" to "冬至是重要的传统节气，也有团聚与饮食习俗。漫长的夜里，记得给彼此温暖。",
            "小寒" to "寒意渐紧，把照顾自己放在心上。", "大寒" to "岁末寒深，新的轮回也已在路上。")
        val winter=name in listOf("立冬","小雪","大雪","冬至","小寒","大寒")
        return FestivalEvent(name,texts[name] ?: "顺着时节的变化，记录今天的风与光。",if(winter) FestivalMotif.SNOW else FestivalMotif.WILLOW,
            if(winter) 0xFFAFCFE5 else 0xFF9FC3AF,if(winter) 0xFFD5E2ED else 0xFFE1D4AB,true)
    }
}
