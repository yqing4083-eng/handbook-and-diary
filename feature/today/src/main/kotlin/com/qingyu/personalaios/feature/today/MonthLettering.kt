package com.qingyu.personalaios.feature.today

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qingyu.personalaios.core.ui.HelloLettering
import com.qingyu.personalaios.core.ui.HelloWord

@Composable internal fun MonthHeader(month:Int) {
    val word=HelloLettering.name(month)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width=minOf((HelloLettering.advance(word)*.37f).dp,(maxWidth-16.dp)/2)
        val height=width*(140f/HelloLettering.advance(word))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            HelloWord(word,Modifier.size(width,height),HelloLettering.colors(month))
            HeaderClock(Modifier.size(width,height))
        }
    }
}
