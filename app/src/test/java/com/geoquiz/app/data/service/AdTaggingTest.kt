package com.geoquiz.app.data.service

import com.google.android.gms.ads.RequestConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decision D8: every request is child-directed, under the age of consent and rated G. */
class AdTaggingTest {

    @Test
    fun `child-directed treatment is on`() {
        assertEquals(
            RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE,
            AdTagging.TAG_FOR_CHILD_DIRECTED_TREATMENT
        )
    }

    @Test
    fun `under age of consent treatment is on for ads and UMP`() {
        assertEquals(
            RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE,
            AdTagging.TAG_FOR_UNDER_AGE_OF_CONSENT
        )
        assertTrue(AdTagging.UMP_TAG_FOR_UNDER_AGE_OF_CONSENT)
    }

    @Test
    fun `max ad content rating is G`() {
        assertEquals(RequestConfiguration.MAX_AD_CONTENT_RATING_G, AdTagging.MAX_AD_CONTENT_RATING)
        assertEquals("G", AdTagging.MAX_AD_CONTENT_RATING)
    }

    @Test
    fun `request configuration carries all three tags`() {
        val config = AdTagging.requestConfiguration()
        assertEquals(
            RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE,
            config.tagForChildDirectedTreatment
        )
        assertEquals(
            RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE,
            config.tagForUnderAgeOfConsent
        )
        assertEquals(RequestConfiguration.MAX_AD_CONTENT_RATING_G, config.maxAdContentRating)
    }
}
