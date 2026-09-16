package il.rikavon.feature.blocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import il.rikavon.feature.blocker.engine.BrowserAddressBars
import il.rikavon.feature.blocker.engine.FeedRules
import il.rikavon.feature.blocker.engine.InstantBlockBus
import il.rikavon.feature.blocker.engine.InstantBlockPolicy
import il.rikavon.feature.blocker.engine.InstantRequest
import il.rikavon.feature.blocker.engine.WebsiteMatcher
import javax.inject.Inject

/**
 * The instant path, and the only place content is ever looked at.
 *
 * Window-state events: the moment a window from another app comes to the front, a blocked app is sent home
 * before it has drawn more than a frame and the block screen is requested; an app whose limit just changed
 * gets the breathing pause instead; an app the pet begs about gets the call the moment it opens.
 *
 * Content-changed events, only when the user has blocked websites or feeds: in a known browser the one
 * address-bar view is read and matched against the block list; in YouTube / Instagram the presence of the
 * Shorts / Reels player views is checked by id. A hit backs out (BACK) and requests the block screen.
 * Nothing is read in any other app, nothing is stored, nothing leaves the phone.
 */
@AndroidEntryPoint
class BlockerAccessibilityService : AccessibilityService() {
    @Inject lateinit var bus: InstantBlockBus

    private lateinit var policy: InstantBlockPolicy
    private val lastBackOut = mutableMapOf<String, Long>()
    private val lastScan = mutableMapOf<String, Long>()

    /** The last package (other than this app) whose window was in front: a change is an "open". */
    private var frontPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        policy = InstantBlockPolicy(packageName)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo =
            (serviceInfo ?: AccessibilityServiceInfo()).apply {
                eventTypes =
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                notificationTimeout = EVENT_TIMEOUT_MILLIS
            }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val current = event ?: return
        val target = current.packageName?.toString() ?: return
        when (current.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val opened = target != packageName && target != frontPackage
                if (target != packageName) frontPackage = target
                when {
                    policy.shouldSendHome(target, bus.blocked.value) -> {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        bus.request(InstantRequest.Block(target))
                    }
                    policy.shouldSendHome(target, bus.gated.value) -> bus.request(InstantRequest.Gate(target))
                    opened && target in bus.pleading.value -> {
                        bus.request(InstantRequest.Plead(target))
                        inspectContent(target)
                    }
                    else -> inspectContent(target)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> inspectContent(target)
        }
    }

    override fun onInterrupt() = Unit

    /** Looks at the one thing this app cares about in [target], at most every [SCAN_GAP_MILLIS]. */
    private fun inspectContent(target: String) {
        if (target == packageName) return
        val now = SystemClock.uptimeMillis()
        val coolingDown = now - (lastBackOut[target] ?: 0L) < BACK_OUT_COOLDOWN_MILLIS
        val tooSoon = now - (lastScan[target] ?: 0L) < SCAN_GAP_MILLIS
        if (coolingDown || tooSoon) return
        lastScan[target] = now
        if (!blockedSite(target)) blockedFeed(target)
    }

    private fun blockedSite(target: String): Boolean {
        val addressBar = BrowserAddressBars.idFor(target) ?: return false
        val sites = bus.sites.value
        if (sites.isEmpty()) return false
        val domain = WebsiteMatcher.blockedBy(WebsiteMatcher.host(textOf(addressBar)), sites) ?: return false
        backOut(target)
        bus.request(InstantRequest.Site(target, domain))
        return true
    }

    private fun blockedFeed(target: String) {
        val feeds = bus.feeds.value
        if (feeds.none { it.packageName == target }) return
        val feature = FeedRules.detect(target, feeds, ::hasView) ?: return
        backOut(target)
        bus.request(InstantRequest.Feed(target, feature))
    }

    private fun textOf(viewId: String): CharSequence? =
        rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()?.text

    private fun hasView(viewId: String): Boolean =
        rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)?.isNotEmpty() == true

    private fun backOut(target: String) {
        lastBackOut[target] = SystemClock.uptimeMillis()
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private companion object {
        const val EVENT_TIMEOUT_MILLIS = 50L
        const val SCAN_GAP_MILLIS = 600L
        const val BACK_OUT_COOLDOWN_MILLIS = 2_500L
    }
}
