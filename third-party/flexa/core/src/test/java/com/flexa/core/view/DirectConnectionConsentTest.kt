package com.flexa.core.view

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectConnectionConsentTest {

    @Test
    fun `an agreement ends when direct connections go from allowed to off`() {
        assertTrue(consentEndsWith(wasAllowed = true, allowed = false))
    }

    @Test
    fun `agreeing while direct connections are off does not end by itself`() {
        assertFalse(consentEndsWith(wasAllowed = false, allowed = false))
    }

    @Test
    fun `turning direct connections on does not end an agreement`() {
        assertFalse(consentEndsWith(wasAllowed = false, allowed = true))
        assertFalse(consentEndsWith(wasAllowed = true, allowed = true))
    }
}
