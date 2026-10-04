package com.mealcoach.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SocialBlockService extends AccessibilityService {
    private final Set<String> blockedPkgs=new HashSet<>(Arrays.asList(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.google.android.youtube",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.facebook.katana",
            "com.instagram.barcelona",
            "com.snapchat.android"
    ));

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        if(!PenaltyManager.blocked(this))return;
        CharSequence pkg=event.getPackageName();
        if(pkg==null||!blockedPkgs.contains(pkg.toString()))return;

        performGlobalAction(GLOBAL_ACTION_HOME);
        Intent i=new Intent(this,BlockedActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    @Override public void onInterrupt(){}
}
