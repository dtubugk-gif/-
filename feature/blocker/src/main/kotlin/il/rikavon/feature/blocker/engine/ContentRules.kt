package il.rikavon.feature.blocker.engine

import il.rikavon.core.data.model.FeedFeature
import il.rikavon.feature.blocker.R

/**
 * Where known browsers keep the address text. Only these packages ever have a view read, and only that
 * one view; every other app is invisible to the service beyond the name of the window in front.
 */
object BrowserAddressBars {
    private val chromium =
        listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.chromium.chrome",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
        ).associateWith { "$it:id/url_bar" }

    private val others =
        mapOf(
            "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox_beta" to "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view",
            "org.mozilla.fenix" to "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.opera.browser" to "com.opera.browser:id/url_field",
            "com.opera.mini.native" to "com.opera.mini.native:id/url_field",
            "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
        )

    private val all = chromium + others

    val packages: Set<String> get() = all.keys

    /** The address bar's view id for [packageName], or null when it is not a browser we know. */
    fun idFor(packageName: String): String? = all[packageName]
}

/**
 * How a blocked feed is recognised inside its app: the view ids of its player. YouTube calls Shorts "reels"
 * internally; Instagram calls Reels "clips" (its "reel_viewer" ids are Stories, which stay allowed).
 */
object FeedRules {
    private val ids: Map<FeedFeature, List<String>> =
        mapOf(
            FeedFeature.YOUTUBE_SHORTS to
                listOf(
                    "com.google.android.youtube:id/reel_recycler",
                    "com.google.android.youtube:id/reel_player_page_container",
                    "com.google.android.youtube:id/reel_watch_fragment_root",
                    "com.google.android.youtube:id/reel_player_underlay",
                    "com.google.android.youtube:id/reel_progress_bar",
                ),
            FeedFeature.INSTAGRAM_REELS to
                listOf(
                    "com.instagram.android:id/clips_viewer_view_pager",
                    "com.instagram.android:id/clips_video_container",
                    "com.instagram.android:id/clips_viewer_container",
                    "com.instagram.android:id/clips_swipe_refresh_container",
                ),
        )

    /** The blocked feed showing in [packageName] right now, if any; [present] answers "is this view id on screen". */
    fun detect(packageName: String, blocked: Set<FeedFeature>, present: (String) -> Boolean): FeedFeature? =
        blocked.firstOrNull { it.packageName == packageName && ids.getValue(it).any(present) }

    fun labelRes(feature: FeedFeature): Int =
        when (feature) {
            FeedFeature.YOUTUBE_SHORTS -> R.string.feed_youtube_shorts
            FeedFeature.INSTAGRAM_REELS -> R.string.feed_instagram_reels
        }
}
