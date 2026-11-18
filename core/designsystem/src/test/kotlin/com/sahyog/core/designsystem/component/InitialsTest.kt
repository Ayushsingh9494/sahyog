package com.sahyog.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {

    @Test
    fun `two words give two initials`() = assertEquals("RK", initialsOf("ramesh kumar"))

    @Test
    fun `extra words and whitespace are ignored`() = assertEquals("SD", initialsOf("  Sunita   Devi Sharma "))

    @Test
    fun `single word gives one initial`() = assertEquals("A", initialsOf("Anil"))

    @Test
    fun `blank name falls back to placeholder`() = assertEquals("?", initialsOf("   "))

    @Test
    fun `devanagari names are supported`() = assertEquals("रक", initialsOf("रमेश कुमार"))
}
