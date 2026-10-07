package com.geoquiz.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FlagAssetPathTest {

    @Test
    fun `upper-case cca3 maps to lower-case svg in flags folder`() {
        assertEquals("flags/aus.svg", flagAssetPath("AUS"))
    }

    @Test
    fun `already lower-case code is unchanged`() {
        assertEquals("flags/nzl.svg", flagAssetPath("nzl"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("flags/unk.svg", flagAssetPath(" UNK "))
    }

    @Test
    fun `asset uri points at android_asset`() {
        assertEquals("file:///android_asset/flags/fra.svg", flagAssetUri("FRA"))
    }
}
