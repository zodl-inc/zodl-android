package com.flexa.core.view

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectConnectionConsentTest {

    @Test
    fun `an agreement ends when direct connections are off after a change`() {
        assertFalse(agreementAfterModeChange(agreed = true, allowed = false))
    }

    @Test
    fun `an agreement stays while direct connections are allowed`() {
        assertTrue(agreementAfterModeChange(agreed = true, allowed = true))
    }

    @Test
    fun `no agreement is not made by a change`() {
        assertFalse(agreementAfterModeChange(agreed = false, allowed = true))
        assertFalse(agreementAfterModeChange(agreed = false, allowed = false))
    }
}
