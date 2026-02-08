package com.morningmindful.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for Room TypeConverters.
 */
class ConvertersTest {

    private val converters = Converters()

    // fromLocalDate tests

    @Test
    fun `fromLocalDate converts date to ISO string`() {
        val date = LocalDate.of(2024, 1, 15)
        val result = converters.fromLocalDate(date)
        assertEquals("2024-01-15", result)
    }

    @Test
    fun `fromLocalDate returns null for null input`() {
        val result = converters.fromLocalDate(null)
        assertNull(result)
    }

    @Test
    fun `fromLocalDate handles first day of year`() {
        val date = LocalDate.of(2024, 1, 1)
        val result = converters.fromLocalDate(date)
        assertEquals("2024-01-01", result)
    }

    @Test
    fun `fromLocalDate handles last day of year`() {
        val date = LocalDate.of(2024, 12, 31)
        val result = converters.fromLocalDate(date)
        assertEquals("2024-12-31", result)
    }

    @Test
    fun `fromLocalDate handles leap year date`() {
        val date = LocalDate.of(2024, 2, 29)
        val result = converters.fromLocalDate(date)
        assertEquals("2024-02-29", result)
    }

    // toLocalDate tests

    @Test
    fun `toLocalDate parses ISO string to date`() {
        val result = converters.toLocalDate("2024-01-15")
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun `toLocalDate returns null for null input`() {
        val result = converters.toLocalDate(null)
        assertNull(result)
    }

    @Test
    fun `toLocalDate handles first day of year`() {
        val result = converters.toLocalDate("2024-01-01")
        assertEquals(LocalDate.of(2024, 1, 1), result)
    }

    @Test
    fun `toLocalDate handles last day of year`() {
        val result = converters.toLocalDate("2024-12-31")
        assertEquals(LocalDate.of(2024, 12, 31), result)
    }

    @Test
    fun `toLocalDate handles leap year date`() {
        val result = converters.toLocalDate("2024-02-29")
        assertEquals(LocalDate.of(2024, 2, 29), result)
    }

    // Round-trip tests

    @Test
    fun `round trip conversion preserves date`() {
        val original = LocalDate.of(2024, 6, 15)
        val asString = converters.fromLocalDate(original)
        val backToDate = converters.toLocalDate(asString)
        assertEquals(original, backToDate)
    }

    @Test
    fun `round trip for today`() {
        val today = LocalDate.now()
        val asString = converters.fromLocalDate(today)
        val backToDate = converters.toLocalDate(asString)
        assertEquals(today, backToDate)
    }

    @Test
    fun `round trip for past date`() {
        val pastDate = LocalDate.of(2020, 3, 14)
        val asString = converters.fromLocalDate(pastDate)
        val backToDate = converters.toLocalDate(asString)
        assertEquals(pastDate, backToDate)
    }

    @Test
    fun `round trip for future date`() {
        val futureDate = LocalDate.of(2030, 12, 25)
        val asString = converters.fromLocalDate(futureDate)
        val backToDate = converters.toLocalDate(asString)
        assertEquals(futureDate, backToDate)
    }

    // Edge cases

    @Test(expected = java.time.format.DateTimeParseException::class)
    fun `toLocalDate throws for invalid format`() {
        converters.toLocalDate("15/01/2024")
    }

    @Test(expected = java.time.format.DateTimeParseException::class)
    fun `toLocalDate throws for invalid date`() {
        converters.toLocalDate("2024-02-30") // Feb 30 doesn't exist
    }

    @Test(expected = java.time.format.DateTimeParseException::class)
    fun `toLocalDate throws for empty string`() {
        converters.toLocalDate("")
    }
}
