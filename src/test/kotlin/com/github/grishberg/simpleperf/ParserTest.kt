package com.github.grishberg.simpleperf

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ParserTest {
    private val underTest: Parser = Parser()

    @Test
    private fun `parse trace`() {
        val result = underTest.parse("simpleperf.proto")
        assertEquals(result.)
    }
    private fun openTrace(fn: String): File {
        val classLoader = javaClass.classLoader
        return File(classLoader.getResource(fn).file)
    }
}