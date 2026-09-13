package com.jeremy.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.KeyEvent

class LauncherAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Track global focus or window changes if needed
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            val source = event.source
            // You can inspect focused node details here
            source?.recycle()
        }
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        // Return true to intercept D-pad or remote key events globally if Compose focus fails
        return super.onKeyEvent(event)
    }
}
