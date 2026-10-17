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

    /**
     * Review fix: [SoundEffectPlayer.loadedSoundIds] is a [java.util.concurrent.ConcurrentHashMap]
     * keyset because the real [android.media.SoundPool]'s load-complete callback can arrive on a
     * background/binder thread, concurrently with another sample's callback. This drives
     * `notifyResourceLoaded` for all three clips from separate real threads at once (standing in
     * for that concurrency) and checks every one is still recorded - a plain `mutableSetOf()`
     * would risk a lost update (or a `ConcurrentModificationException`) under exactly this
     * pattern.
     */
    @Test
    fun `load-complete callbacks arriving concurrently on different threads are all recorded`() = runTest {
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val player = SoundEffectPlayer(context, settingsWith(soundEffectsEnabled = true))
        val shadow = shadowOf(player.soundPool)

        val resources = listOf(R.raw.sound_correct, R.raw.sound_wrong, R.raw.sound_perfect)
        val threads = resources.map { resId -> Thread { shadow.notifyResourceLoaded(resId, true) } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        player.playCorrect()
        player.playWrong()
        player.playPerfect()

        assertTrue(shadow.wasResourcePlayed(R.raw.sound_correct))
        assertTrue(shadow.wasResourcePlayed(R.raw.sound_wrong))
        assertTrue(shadow.wasResourcePlayed(R.raw.sound_perfect))
    }

    /**
     * Review fix: [SoundEffectPlayer] now registers its `OnLoadCompleteListener` (in `init`)
     * before any `soundPool.load(...)` call, so an eager load-complete cannot be dropped. This is
     * mostly a code-structure guarantee Robolectric's `ShadowSoundPool` cannot race-test directly
     * (it never fires a completion synchronously from `load()`), but this still pins the
     * observable contract every other test here already relies on: a clip reported loaded,
     * however soon after construction, is always picked up.
     */
    @Test
    fun `a load-complete reported immediately after construction is still picked up`() = runTest {
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val player = SoundEffectPlayer(context, settingsWith(soundEffectsEnabled = true))

        shadowOf(player.soundPool).notifyResourceLoaded(R.raw.sound_correct, true)
        player.playCorrect()

        assertTrue(shadowOf(player.soundPool).wasResourcePlayed(R.raw.sound_correct))
    }
}
