package org.eyeatlas

import org.eyeatlas.core.DecisionEngine
import org.eyeatlas.core.EyeLaterality
import org.junit.Assert.assertNotNull
import org.junit.Test

class MultiDiseaseTests {
    @Test
    fun baselineMultiDiseaseVerification() {
        val engine = DecisionEngine()
        val results = engine.evaluate(EyeLaterality.OD)
        assertNotNull(results)
    }
}
