package com.sahyog.core.common.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `valid indian mobile numbers pass`() {
        listOf("9876543210", "6000000000", "7123456789", "8999999999").forEach {
            assertTrue(it, PhoneNumberValidator.isValid(it))
        }
    }

    @Test
    fun `invalid mobile numbers fail`() {
        listOf("", "987654321", "98765432101", "5876543210", "0876543210", "98765abcde").forEach {
            assertFalse(it, PhoneNumberValidator.isValid(it))
        }
    }

    @Test
    fun `phone sanitize keeps digits and caps length while typing`() {
        assertEquals("98765", PhoneNumberValidator.sanitize("98-7 65"))
        assertEquals("9876543210", PhoneNumberValidator.sanitize("98765432109"))
    }

    @Test
    fun `phone sanitize strips pasted country code and trunk prefix`() {
        assertEquals("9876543210", PhoneNumberValidator.sanitize("+91 98765 43210"))
        assertEquals("9876543210", PhoneNumberValidator.sanitize("09876543210"))
    }

    @Test
    fun `otp requires exactly six digits`() {
        assertTrue(OtpValidator.isValid("123456"))
        assertFalse(OtpValidator.isValid("12345"))
        assertFalse(OtpValidator.isValid("12345a"))
        assertEquals("123456", OtpValidator.sanitize("12-34 567"))
    }

    @Test
    fun `names accept latin and devanagari`() {
        assertTrue(NameValidator.isValid("Ramesh Kumar"))
        assertTrue(NameValidator.isValid("रमेश कुमार"))
        assertTrue(NameValidator.isValid("D'Souza-Pai"))
    }

    @Test
    fun `names reject too short, digits and symbols`() {
        assertFalse(NameValidator.isValid(" A "))
        assertFalse(NameValidator.isValid("Ramesh123"))
        assertFalse(NameValidator.isValid("<script>"))
    }
}
