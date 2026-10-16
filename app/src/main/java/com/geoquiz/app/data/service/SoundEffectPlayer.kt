package com.geoquiz.app.data.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.geoquiz.app.R
import com.geoquiz.app.data.local.preferences.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the three original "game-feel" sound effects (task 8.5): a quick rising chime for a
 * correct answer, a short low buzz for a wrong one, and a brighter arpeggio for a perfect score.
 * The clips are synthesised by `tools/sound/generate_sound_effects.py` (see `data/SOURCES.md`),
 * not downloaded.
 *
 * Playback is skipped silently, never throwing or substituting another cue, when:
 * - the setting `SettingsRepository.soundEffects` is off, or
 * - the system ringer is not [AudioManager.RINGER_MODE_NORMAL] (silent or vibrate/DND), so this
 *   always respects the system silent switch, the same way 3.3's haptics already respect the
 *   system touch-feedback setting.
 *
 * Fires from the same points `QuizViewModel` already emits its `feedbackEvents` (correct/wrong)
 * and from Results for a perfect score, alongside the confetti celebration
 * ([com.geoquiz.app.ui.quiz.feedback.ConfettiOverlay]); never a separate trigger path.
 */
@Singleton
class SoundEffectPlayer @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Internal (not private) so `SoundEffectPlayerTest` can inspect playback with Robolectric's
     * `ShadowSoundPool`, the same Robolectric + fake pattern as `AdManagerTest`. */
    internal val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** Sound-pool sample ids that have finished loading; playback before that is skipped. */
    private val loadedSoundIds = mutableSetOf<Int>()

    private val correctSoundId = soundPool.load(context, R.raw.sound_correct, 1)
    private val wrongSoundId = soundPool.load(context, R.raw.sound_wrong, 1)
    private val perfectSoundId = soundPool.load(context, R.raw.sound_perfect, 1)

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSoundIds += sampleId
        }
    }

    /** A correct typed answer or Easy pick. */
    suspend fun playCorrect() = play(correctSoundId)

    /** A wrong typed answer (a strike at Hard) or a wrong Easy pick. */
    suspend fun playWrong() = play(wrongSoundId)

    /** A perfect-score result on Results. */
    suspend fun playPerfect() = play(perfectSoundId)

    private suspend fun play(soundId: Int) {
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (!settingsRepository.soundEffects.first()) return
        if (soundId !in loadedSoundIds) return
        soundPool.play(soundId, VOLUME, VOLUME, PRIORITY, LOOP_NONE, PLAYBACK_RATE)
    }

    private companion object {
        const val MAX_STREAMS = 3
        const val VOLUME = 1f
        const val PRIORITY = 1
        const val LOOP_NONE = 0
        const val PLAYBACK_RATE = 1f
    }
}

/**
 * Lets a leaf composable reach the singleton [SoundEffectPlayer] without a ViewModel, the same
 * pattern [com.geoquiz.app.ui.ads.BannerAd] already uses for `ConsentManager`: Results plays the
 * perfect-score chime directly (alongside the confetti celebration), since `ResultsViewModel`
 * has no other reason to depend on it.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SoundEffectPlayerEntryPoint {
    fun soundEffectPlayer(): SoundEffectPlayer
}

/** A [SoundEffectPlayer] fetched once per composition via Hilt's application entry point. */
@Composable
fun rememberSoundEffectPlayer(): SoundEffectPlayer {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, SoundEffectPlayerEntryPoint::class.java)
            .soundEffectPlayer()
    }
}
