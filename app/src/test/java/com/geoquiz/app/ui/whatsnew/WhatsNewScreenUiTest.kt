package com.geoquiz.app.ui.whatsnew

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.geoquiz.app.R
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * [WhatsNewDialog] (P10-F): "Got it", the close icon and the system back action all dismiss it
 * exactly once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhatsNewScreenUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val sections = whatsNewSections(emptySet())

    private fun launch(onDismiss: () -> Unit) {
        compose.setContent {
            GeographyQuizTheme {
                WhatsNewDialog(sections = sections, onDismiss = onDismiss)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `shows the title and the changes section`() {
        launch(onDismiss = {})

        compose.onNodeWithText(compose.activity.getString(R.string.whats_new_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.whats_new_section_changes)).assertIsDisplayed()
    }

    @Test
    fun `Got it dismisses once`() {
        var dismissed = 0
        launch(onDismiss = { dismissed++ })

        compose.onNodeWithText(compose.activity.getString(R.string.whats_new_got_it)).performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun `the close icon dismisses once`() {
        var dismissed = 0
        launch(onDismiss = { dismissed++ })

        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_close)).performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun `the system back action dismisses once`() {
        var dismissed = 0
        launch(onDismiss = { dismissed++ })

        // The dialog is its own window: the system back action is delivered to it, not the host
        // activity's OnBackPressedDispatcher, so it is simulated on the dialog itself (the
        // underlying android.app.Dialog Compose's Dialog() shows, found via Robolectric's
        // shadow), the same way a real back-button press would reach whichever window has focus.
        val dialog = ShadowDialog.getLatestDialog()
        dialog.onBackPressed()
        compose.waitForIdle()

        assertEquals(1, dismissed)
    }
}
