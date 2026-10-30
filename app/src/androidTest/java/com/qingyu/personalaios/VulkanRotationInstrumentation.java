package com.qingyu.personalaios;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

/** Current four-tab startup smoke check. No user data is edited. */
public final class VulkanRotationInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args);start(); }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text) {
        if(node==null)return null;
        if(text.contentEquals(node.getText()==null?"":node.getText()) || text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo value=find(node.getChild(i),text);if(value!=null)return value;}
        return null;
    }
    @Override public void onStart() {
        Bundle report=new Bundle();
        try {
            Intent intent=new Intent().setClassName(getTargetContext(),"com.qingyu.personalaios.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getTargetContext().startActivity(intent);
            long deadline=SystemClock.uptimeMillis()+20000;
            while(find(getUiAutomation().getRootInActiveWindow(),"Today")==null && SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100);
            for(String tab:new String[]{"Today","日历","日记","Me"}) {
                getUiAutomation().clearCache();AccessibilityNodeInfo node=find(getUiAutomation().getRootInActiveWindow(),tab);
                if(node==null)throw new IllegalStateException("Missing tab: "+tab);
                if(!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    android.graphics.Rect rect=new android.graphics.Rect();node.getBoundsInScreen(rect);
                    long down=SystemClock.uptimeMillis();
                    for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}) {
                        android.view.MotionEvent event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,rect.exactCenterX(),rect.exactCenterY(),0);
                        sendPointerSync(event);event.recycle();
                    }
                }
                SystemClock.sleep(1200);
            }
            if(find(getUiAutomation().getRootInActiveWindow(),"Patch")!=null)throw new IllegalStateException("Retired assistant remained");
            report.putString("stream","Four-tab navigation smoke passed; no assistant entry.\n");finish(Activity.RESULT_OK,report);
        } catch(Exception e){report.putString("stream","FAILED: "+e);finish(Activity.RESULT_CANCELED,report);}
    }
}
