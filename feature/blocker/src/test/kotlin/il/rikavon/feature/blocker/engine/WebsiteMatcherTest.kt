package il.rikavon.feature.blocker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebsiteMatcherTest {
    private val blocked = setOf("instagram.com", "tiktok.com")

    @Test
    fun `the host is pulled out of whatever the address bar shows`() {
        assertEquals("m.instagram.com", WebsiteMatcher.host("https://m.instagram.com/reels?x=1"))
        assertEquals("instagram.com", WebsiteMatcher.host("Instagram.com/p/abc"))
        assertEquals("tiktok.com", WebsiteMatcher.host("http://tiktok.com:443/#top"))
    }

    @Test
    fun `a search query or an empty bar is not a host`() {
        assertNull(WebsiteMatcher.host("how to stop scrolling"))
        assertNull(WebsiteMatcher.host(""))
        assertNull(WebsiteMatcher.host(null))
        assertNull(WebsiteMatcher.host("localhost"))
    }

    @Test
    fun `a domain blocks itself, www, and every subdomain, nothing else`() {
        assertEquals("instagram.com", WebsiteMatcher.blockedBy("instagram.com", blocked))
        assertEquals("instagram.com", WebsiteMatcher.blockedBy("www.instagram.com", blocked))
        assertEquals("instagram.com", WebsiteMatcher.blockedBy("m.instagram.com", blocked))
        assertNull(WebsiteMatcher.blockedBy("notinstagram.com", blocked))
        assertNull(WebsiteMatcher.blockedBy("instagram.com.evil.example", blocked))
        assertNull(WebsiteMatcher.blockedBy(null, blocked))
    }
}
