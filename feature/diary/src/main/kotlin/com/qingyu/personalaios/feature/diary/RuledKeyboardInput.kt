package com.qingyu.personalaios.feature.diary

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Owns only the Android input connection. RuledFlow remains the single source
 * of geometry for typed text, original handwriting, stickers and the cursor.
 * Native TextView scrolling must never move the fixed paper or its rules. */
internal class RuledKeyboardView(context:Context):EditText(context) {
    var syncing=false
    var changed:(String,Int,Int)->Unit={ _,_,_-> }
    var selection:((Int,Int,Int,Int)->Unit)?=null
    private var changeStart=0
    private var removed=0
    init {
        background=null;setPadding(0,0,0,0)
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setHorizontallyScrolling(false);isVerticalScrollBarEnabled=false;isHorizontalScrollBarEnabled=false
        isCursorVisible=false;contentDescription="日记键盘输入"
        filters=arrayOf(android.text.InputFilter.LengthFilter(100000))
        addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) { changeStart=start;removed=count }
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=Unit
            override fun afterTextChanged(s:Editable) {
                if(!syncing) { changed(s.toString(),changeStart,removed);publishSelection() }
            }
        })
    }
    // This is an input bridge, not a second visible text layout.
    override fun onDraw(canvas:Canvas)=Unit
    override fun scrollTo(x:Int,y:Int)=super.scrollTo(0,0)
    override fun requestRectangleOnScreen(rectangle:Rect,immediate:Boolean)=false
    override fun onSelectionChanged(start:Int,end:Int) { super.onSelectionChanged(start,end);if(!syncing) publishSelection() }
    private fun publishSelection() {
        val value=text ?: return
        selection?.invoke(selectionStart.coerceAtLeast(0),selectionEnd.coerceAtLeast(0),BaseInputConnection.getComposingSpanStart(value),BaseInputConnection.getComposingSpanEnd(value))
    }
    fun sync(value:String,cursor:Int) {
        syncing=true
        try {
            if(text.toString()!=value) setText(value)
            val offset=cursor.coerceIn(0,length())
            if(selectionEnd!=offset || selectionStart!=offset) setSelection(offset)
        } finally { syncing=false }
    }
    fun open(value:String,cursor:Int) {
        sync(value,cursor);requestFocus();sync(value,cursor)
        post { if(isAttachedToWindow && hasFocus()) (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this,InputMethodManager.SHOW_IMPLICIT) }
    }
}

@Composable internal fun RuledKeyboardInput(text:String,cursor:Int,bridge:RuledWritingBridge?,modifier:Modifier,
    onChange:(String,Int,Int)->Unit,onSelection:(Int,Int,Int,Int)->Unit) {
    val change by rememberUpdatedState(onChange)
    val selection by rememberUpdatedState(onSelection)
    AndroidView(factory={ context -> RuledKeyboardView(context).apply {
        changed={ next,start,removed -> change(next,start,removed) }
        this.selection={ a,b,c,d -> selection(a,b,c,d) }
        sync(text,cursor)
        bridge?.openKeyboard={ value,offset -> open(value,offset) }
    } },update={ it.sync(text,cursor) },onRelease={ view ->
        bridge?.openKeyboard={ _,_-> };view.changed={ _,_,_-> };view.selection={ _,_,_,_-> }
        view.clearFocus()
    },modifier=modifier)
}
