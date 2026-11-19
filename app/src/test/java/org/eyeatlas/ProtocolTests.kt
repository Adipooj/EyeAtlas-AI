package org.eyeatlas

import org.eyeatlas.core.TestDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolTests {
    @Test
    fun baselineProtocolVerification() {
        val domain = TestDomain.ACUITY
        assertEquals("ACUITY", domain.name)
    }
}
