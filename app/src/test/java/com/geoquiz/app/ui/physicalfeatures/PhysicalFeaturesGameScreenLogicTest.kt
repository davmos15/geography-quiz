package com.geoquiz.app.ui.physicalfeatures

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [shouldFitPhysicalFeatureTarget] (task P10-R1 review fix): the camera must fit a question's
 * tight frame exactly once, surviving both ordinary recomposition and a configuration change
 * (e.g. rotation) that recreates the whole composable but restores `framedId` from
 * `rememberSaveable`.
 */
class PhysicalFeaturesGameScreenLogicTest {

    @Test
    fun `a brand new target with nothing framed yet needs fitting`() {
        assertTrue(shouldFitPhysicalFeatureTarget(targetId = "nile", framedId = null))
    }

    @Test
    fun `a different target than the one already framed needs fitting`() {
        assertTrue(shouldFitPhysicalFeatureTarget(targetId = "amazon", framedId = "nile"))
    }

    @Test
    fun `the same target already framed does not need fitting again - recomposition or rotation`() {
        assertFalse(shouldFitPhysicalFeatureTarget(targetId = "nile", framedId = "nile"))
    }

    @Test
    fun `no current target never needs fitting`() {
        assertFalse(shouldFitPhysicalFeatureTarget(targetId = null, framedId = "nile"))
        assertFalse(shouldFitPhysicalFeatureTarget(targetId = null, framedId = null))
    }
}
