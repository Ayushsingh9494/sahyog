package com.sahyog.core.common.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppResultTest {

    @Test
    fun `map transforms success value`() {
        val result: AppResult<Int> = AppResult.Success(2)

        assertEquals(AppResult.Success("4"), result.map { (it * 2).toString() })
    }

    @Test
    fun `map passes failure through untouched`() {
        val result: AppResult<Int> = AppResult.Failure(AppError.Network)

        assertEquals(AppResult.Failure(AppError.Network), result.map { it * 2 })
    }

    @Test
    fun `onSuccess and onFailure only fire for their own case`() {
        var successCalled = false
        var failureCalled = false

        AppResult.Success(1).onSuccess { successCalled = true }.onFailure { failureCalled = true }

        assertTrue(successCalled)
        assertFalse(failureCalled)
    }
}
