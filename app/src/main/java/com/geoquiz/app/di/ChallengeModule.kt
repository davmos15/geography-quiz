package com.geoquiz.app.di

import com.geoquiz.app.BuildConfig
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The challenge link signer, keyed with `CHALLENGE_HMAC_KEY` from the build
 * (`signing.properties` `challengeHmacKey`, env `CHALLENGE_HMAC_KEY`, or the dev key).
 */
@Module
@InstallIn(SingletonComponent::class)
object ChallengeModule {

    @Provides
    @Singleton
    fun provideChallengeLinkSigner(): ChallengeLinkSigner =
        ChallengeLinkSigner.fromBase64Url(BuildConfig.CHALLENGE_HMAC_KEY)
}
