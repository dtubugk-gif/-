package il.rikavon.screenshots

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream

/**
 * Drives a Compose screen under Robolectric with a paused clock: polls for text, clicks through semantics,
 * scrolls lazy lists one item at a time and draws the window into a bitmap. Shared by the documentation
 * screenshots and the Play Store assets so both come from the same real app.
 */
internal class ShotHarness(
    private val compose: ComposeTestRule,
    private val context: Context,
    private val debugDir: File,
) {
    /** Draws the screen a timed-out wait was looking at, so the failure comes with a picture. */
    private var debugShot: (() -> Bitmap)? = null

    fun <A : Activity> launch(activity: Class<A>): ActivityScenario<A> {
        val launched = ActivityScenario.launch(activity)
        debugShot = { draw(launched) }
        return launched
    }

    /** Polls until a node with [text] exists (background loads, navigation), then lets animations settle. */
    fun waitFor(text: String, timeoutMillis: Long = WAIT_TIMEOUT_MILLIS, substringOk: Boolean = true) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(REAL_SLEEP_MILLIS)
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
            if (exists(text, substringOk)) {
                settle()
                return
            }
        }
        debugShot?.let {
            val ascii = text.filter { c -> c in 'a'..'z' || c in 'A'..'Z' || c.isDigit() }.take(TIMEOUT_NAME_CHARS)
            save(it(), debugDir, "debug_timeout_" + ascii.ifEmpty { text.hashCode().toUInt().toString(radix = 16) })
        }
        println("TIMEOUT waiting for '$text' codepoints=" + text.codePoints().toArray().joinToString())
        println("TIMEOUT tree: " + runCatching { compose.onRoot().printToString() }.getOrElse { it.toString() })
        error("timed out waiting for '$text'")
    }

    /**
     * Scrolls the screen's lazy list one item at a time until a node with [text] is composed. The clock is
     * paused, so each step gets a few frames for the list to lay out before the next look-up;
     * `performScrollToNode` would spin forever here because it never lets a frame through.
     */
    fun scrollTo(text: String) {
        val list = compose.onNode(hasScrollAction())
        var index = 0
        while (!exists(text)) {
            check(index < MAX_SCROLL_ITEMS) { "could not scroll to '$text'" }
            val target = index++
            runCatching { list.performSemanticsAction(SemanticsActions.ScrollToIndex) { it(target) } }
            settle(SCROLL_STEP_MILLIS)
        }
        settle()
    }

    /** True when at least one node shows [text]; a substring may legitimately match several nodes. */
    fun exists(text: String, substring: Boolean = true): Boolean =
        runCatching {
            compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)

    /**
     * Invokes the click action of the first clickable node showing [text]. Goes through semantics rather
     * than touch injection so rows that are composed just below the viewport (lazy-list prefetch) still work.
     */
    fun click(text: String, substring: Boolean = false) {
        compose
            .onAllNodesWithText(text, substring = substring)
            .filter(hasClickAction())
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    /** Same as [click] for nodes identified by content description (icon-only buttons). */
    fun clickDescribed(description: String) {
        compose
            .onAllNodesWithContentDescription(description)
            .filter(hasClickAction())
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
    }

    /** Lets background work land, idles the main looper and advances the Compose clock frame by frame. */
    fun settle(millis: Long = SETTLE_MILLIS) {
        val frames = (millis / FRAME_MILLIS).toInt()
        repeat(frames) {
            Thread.sleep(REAL_SLEEP_MILLIS)
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The window of [scenario]'s activity as pixels, after a short settle. */
    fun <A : Activity> draw(scenario: ActivityScenario<A>): Bitmap {
        settle()
        lateinit var bitmap: Bitmap
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            check(view.width > 0 && view.height > 0) { "decor view has no size" }
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    fun save(bitmap: Bitmap, dir: File, name: String) {
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
    }

    /** A string resource of the app by name, resolved in the test's current locale. */
    fun string(name: String, vararg args: Any): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        check(id != 0) { "missing string $name" }
        return context.getString(id, *args)
    }

    companion object {
        const val SDK = 34
        const val SETTLE_MILLIS = 1_600L
        const val LONG_SETTLE = 3_200L
        private const val FRAME_MILLIS = 16L
        private const val REAL_SLEEP_MILLIS = 4L
        private const val PNG_QUALITY = 100
        private const val WAIT_TIMEOUT_MILLIS = 30_000L
        private const val TIMEOUT_NAME_CHARS = 12
        private const val MAX_SCROLL_ITEMS = 40
        private const val SCROLL_STEP_MILLIS = 400L

        fun screenshotsDir(): File = File(System.getProperty("rikavon.screenshots.dir") ?: "build/screenshots")

        fun playDir(): File = File(System.getProperty("rikavon.play.dir") ?: "build/play")
    }
}
