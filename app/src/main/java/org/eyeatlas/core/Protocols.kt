package org.eyeatlas.core

interface TestProtocol {
    val domain: TestDomain
    fun isComplete(): Boolean
}
