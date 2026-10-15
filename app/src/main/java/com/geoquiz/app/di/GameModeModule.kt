package com.geoquiz.app.di

import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.classic.CapitalsGameMode
import com.geoquiz.app.domain.mode.classic.CountriesGameMode
import com.geoquiz.app.domain.mode.classic.FlagsGameMode
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Registers every game mode with [com.geoquiz.app.domain.mode.GameModeRegistry].
 * Adding a mode = one `@Binds @IntoSet` line here (plus its GameMode class and generator).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GameModeModule {

    @Binds @IntoSet
    abstract fun countries(mode: CountriesGameMode): GameMode

    @Binds @IntoSet
    abstract fun capitals(mode: CapitalsGameMode): GameMode

    @Binds @IntoSet
    abstract fun flags(mode: FlagsGameMode): GameMode

    @Binds @IntoSet
    abstract fun silhouettes(mode: SilhouettesGameMode): GameMode

    @Binds @IntoSet
    abstract fun tapMap(mode: TapMapGameMode): GameMode

    @Binds @IntoSet
    abstract fun borderHop(mode: BorderHopGameMode): GameMode
}
