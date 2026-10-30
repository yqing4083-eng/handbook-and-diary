package com.qingyu.personalaios.core.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.qingyu.personalaios.core.ui.*

/** The book and profile share one shape, text measurement and inline IME flow. */
@Composable fun InlineGlassName(id:String,name:String,enabled:Boolean,modifier:Modifier=Modifier,
    maxLength:Int=60,label:String="更改书本名称",inputLabel:String="书本名称",
    onEditing:(Boolean)->Unit={},onBounds:(Rect)->Unit={},onCommit:(String)->Unit) {
    var editing by remember(id) { mutableStateOf(false) }
    var value by remember(id) { mutableStateOf(TextFieldValue(name)) }
    var hadFocus by remember(id) { mutableStateOf(false) }
    val requester=remember { FocusRequester() }
    val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
    val color=LocalSpaceColors.current.foreground;val measurer=rememberTextMeasurer();val density=LocalDensity.current
    val style=TextStyle(color=color,fontSize=22.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    LaunchedEffect(name) { if(!editing) value=TextFieldValue(name) }
    val measured=with(density) { measurer.measure(AnnotatedString(value.text.ifEmpty { " " }),style).size.width.toDp() }+36.dp
    val width by animateDpAsState(measured.coerceIn(64.dp,280.dp),tween(160),label="name width")
    GlassButton({ if(enabled) { editing=true;value=value.copy(selection=TextRange(value.text.length)) } },
        modifier.width(width).heightIn(min=44.dp).onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .semantics { contentDescription=label },GlassGeometry.Corner(18.dp)) {
        if(editing) BasicTextField(value,{ value=it.copy(text=it.text.replace("\n","").take(maxLength)) },
            Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp).focusRequester(requester).onFocusChanged {
                if(it.isFocused) hadFocus=true
                else if(hadFocus) { hadFocus=false;editing=false;keyboard?.hide();onCommit(value.text.trim().ifBlank { name }) }
            }.semantics { contentDescription=inputLabel },textStyle=style,cursorBrush=SolidColor(color),singleLine=true,
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={ focus.clearFocus();keyboard?.hide() }))
        else SpaceText(name,22.sp,Modifier.padding(horizontal=16.dp,vertical=8.dp),maxLines=1)
    }
    LaunchedEffect(editing) { if(editing) { requester.requestFocus();keyboard?.show() } }
    DisposableEffect(editing) { onEditing(editing);onDispose { if(editing) onEditing(false) } }
    BackHandler(editing) { focus.clearFocus();keyboard?.hide() }
}
