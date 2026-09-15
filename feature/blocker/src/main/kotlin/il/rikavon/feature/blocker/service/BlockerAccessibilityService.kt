package il.rikavon.feature.blocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import il.rikavon.feature.blocker.engine.InstantBlockBus
import il.rikavon.feature.blocker.engine.InstantBlockPolicy
import javax.inject.Inject

/**
 * The instant path. Android tells this service the moment a window from another app comes to the front;
 * if that app is blocked right now it is sent home before it has drawn more than a frame, and the block
 * screen is requested from the enforcement service. Only window-state events are subscribed and window
 * content is never retrieved. Optional: without it the polling loop still blocks, a second or two later.
 */
@AndroidEntryPoint
class BlockerAccessibilityService : AccessibilityService() {
    @Inject lateinit var bus: InstantBlockBus

    private lateinit var policy: InstantBlockPolicy

    override fun onCreate() {
        super.onCreate()
        policy = InstantBlockPolicy(packageName)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo =
            (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = EVENT_TIMEOUT_MILLIS
            }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        if (current.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!policy.shouldSendHome(current.packageName, bus.blocked.value)) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        bus.request(current.packageName.toString())
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val EVENT_TIMEOUT_MILLIS = 50L
    }
}
