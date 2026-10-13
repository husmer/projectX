package com.husmer.hanzigate

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Watches for X coming to the foreground (the service config limits events to X's package)
 * and puts the quiz in front of it unless it was recently unlocked.
 */
class GateService : AccessibilityService() {
    private var lastLaunch = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() !in BLOCKED) return
        val now = System.currentTimeMillis()
        if (now < HanziApp.get(this).store.unlockedUntil || now - lastLaunch < 1500) return
        lastLaunch = now
        startActivity(Intent(this, QuizActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onInterrupt() = Unit

    companion object {
        /** If X ever changes its package name, update it here and in res/xml/gate_service.xml. */
        val BLOCKED = setOf("com.twitter.android")

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, GateService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
