package com.geoquiz.app.data.service

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.R
import com.geoquiz.app.data.local.preferences.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * [SoundEffectPlayer] wraps real [android.media.SoundPool]/[AudioManager], so this follows the
 * Robolectric + fake pattern already used for `data/service` classes (see `AdManagerTest`),
 * faking only [SettingsRepository] and driving the system ringer with Robolectric's
 * `ShadowAudioManager`.
 */
@RunWith(RobolectricTestRunner::class)
class SoundEffectPlayerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun settingsWith(soundEffectsEnabled: Boolean) = mockk<SettingsRepository> {
        every { soundEffects } returns flowOf(soundEffectsEnabled)
    }

    private fun playerLoaded(soundEffectsEnabled: Boolean): SoundEffectPlayer {
        val player = SoundEffectPlayer(context, settingsWith(soundEffectsEnabled))
        // The real SoundPool decodes asynchronously; this stands in for that completing, the
        // same way a production load eventually does before playCorrect()/playWrong() are first
        // reachable from a finished quiz.
        val shadow = shadowOf(player.soundPool)
        shadow.notifyResourceLoaded(R.raw.sound_correct, true)
        shadow.notifyResourceLoaded(R.raw.sound_wrong, true)
        shadow.notifyResourceLoaded(R.raw.sound_perfect, true)
        return player
    }

    @Test
    fun `plays the correct sound when the ringer is normal and the setting is on`() = runTest {
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val player = playerLoaded(soundEffectsEnabled = true)

        player.playCorrect()

        assertTrue(shadowOf(player.soundPool).wasResourcePlayed(R.raw.sound_correct))
    }

    @Test
    fun `plays the wrong and perfect sounds the same way`() = runTest {
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val player = playerLoaded(soundEffectsEnabled = true)

        player.playWrong()
        player.playPerfect()

        assertTrue(shadowOf(player.soundPool).wasResourcePlayed(R.raw.sound_wrong))
        assertTrue(shadowOf(player.soundPool).wasResourcePlayed(R.raw.sound_perfect))
    }

    @Test
    fun `no sound plays when the sound effects setting is off, even with a normal ringer`() = runTest {
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val player = playerLoaded(soundEffectsEnabled = false)

        player.playCorrect()

        assertFalse(shadowOf(player.soundPool).wasResourcePlayed(R.raw.sound_correct))
    }

    @Test
    fun `no sound plays in silent mode, even with the setting on`() = runTest {
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        val player = playerLoaded(soundEffectsEnabled = true)

        player.playWrong()

        assertFalse(shadowOf(player.soundPool).wasResourcePlayed(R.raw.sound_wrong))
    }

    @Test
    fun `no sound plays in vibrate-only DND mode, even with the setting on`() = runTest {
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        val player = playerLoaded(soundEffectsEnabled = true)

        player.playPerfect()

        assertFalse(shadowOf(player.soundPool).wasResourcePlayed(R.raw.sound_perfect))
    }

    @Test
    fun `nothing is played before the clip finishes loading`() = runTest {
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        // Not using playerLoaded(): no notifyResourceLoaded() call, so the clip is "still loading".
        val player = SoundEffectPlayer(context, settingsWith(soundEffectsEnabled = true))

        player.playCorrect()

        assertFalse(shadowOf(player.soundPool).wasResourcePlayed(R.raw.sound_correct))
    }
}
