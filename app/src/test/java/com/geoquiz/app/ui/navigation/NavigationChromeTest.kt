package com.geoquiz.app.ui.navigation

import com.geoquiz.app.ui.components.WindowLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Bar, rail or neither, by route and window (3.7). */
class NavigationChromeTest {

    private val phone = WindowLayout(widthDp = 411, heightDp = 891)
    private val tablet = WindowLayout(widthDp = 1280, heightDp = 800)
    private val landscapePhone = WindowLayout(widthDp = 891, heightDp = 411)

    private val tabRoutes = listOf(Screen.Play, Screen.Stats, Screen.Achievements, Screen.Settings).map { it.route }

    private val otherRoutes = listOf(
        Screen.Quiz.route, Screen.Results.route, Screen.AnswerReview.route, Screen.CategoryList.route,
        Screen.ChallengeAccept.route, Screen.Challenges.route, Screen.Credits.route,
        Screen.OpenSourceLicences.route, Screen.DebugMenu.route
    )

    @Test
    fun `the four tabs show the bottom bar on a phone`() {
        tabRoutes.forEach { assertEquals(it, NavigationChrome.BottomBar, navigationChrome(it, phone)) }
    }

    @Test
    fun `the four tabs show the rail on a tablet and a phone in landscape`() {
        tabRoutes.forEach {
            assertEquals(it, NavigationChrome.Rail, navigationChrome(it, tablet))
            assertEquals(it, NavigationChrome.Rail, navigationChrome(it, landscapePhone))
        }
    }

    @Test
    fun `every other route shows neither`() {
        otherRoutes.forEach { route ->
            listOf(phone, tablet, landscapePhone).forEach { layout ->
                assertEquals("$route on $layout", NavigationChrome.None, navigationChrome(route, layout))
            }
        }
    }

    @Test
    fun `before the first destination is known the start tab's chrome shows`() {
        assertEquals(NavigationChrome.BottomBar, navigationChrome(null, phone))
        assertEquals(NavigationChrome.Rail, navigationChrome(null, tablet))
    }

    @Test
    fun `the tab routes are exactly the four bar items`() {
        assertEquals(tabRoutes.toSet(), topLevelRoutes)
    }
}
