package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CsvExportTest {
    private fun tx(id: Int, amount: Double, recipient: String, date: String, note: String = "", type: String = "expense", source: String = "SMS") =
        Transaction(id = id, amount = amount, recipient = recipient, date = date, refNumber = "REF$id", rawText = "secret sms text",
            source = source, category = "Food & Dining", subcategory = "Restaurants", type = type, note = note)

    @Test fun headerAndRowsOldestFirst() {
        val lines = CsvExport.build(listOf(tx(2, 320.0, "Cafe", "03/11/25"), tx(1, 50000.0, "Employer", "01/11/25", type = "income", source = "manual")))
            .trimEnd().split("\r\n")
        assertEquals("Date,Type,Amount,Merchant or payer,Category,Subcategory,Note,Source,Reference", lines[0])
        assertEquals("2025-11-01,income,50000.00,Employer,Food & Dining,Restaurants,,manual,REF1", lines[1])
        assertEquals("2025-11-03,expense,320.00,Cafe,Food & Dining,Restaurants,,SMS,REF2", lines[2])
        assertEquals(3, lines.size)
    }

    @Test fun rawSmsTextIsNotExported() {
        assertFalse(CsvExport.build(listOf(tx(1, 1.0, "A", "01/11/25"))).contains("secret sms text"))
    }

    @Test fun escaping() {
        assertEquals("plain", CsvExport.cell("plain"))
        assertEquals("\"Cafe, Delhi\"", CsvExport.cell("Cafe, Delhi"))
        assertEquals("\"He said \"\"hi\"\"\"", CsvExport.cell("He said \"hi\""))
        assertEquals("\"two\nlines\"", CsvExport.cell("two\nlines"))
    }

    @Test fun formulaGuard() {
        assertEquals("'=HYPERLINK(1)", CsvExport.cell("=HYPERLINK(1)"))
        assertEquals("'+91 shop", CsvExport.cell("+91 shop"))
        assertEquals("'-5", CsvExport.cell("-5"))
        assertEquals("'@home", CsvExport.cell("@home"))
        assertEquals("\"'=A1,B1\"", CsvExport.cell("=A1,B1"))
    }

    @Test fun unreadableDateKeptAsStoredAndLast() {
        val lines = CsvExport.build(listOf(tx(1, 1.0, "Bad", "31/02/25"), tx(2, 2.0, "Good", "01/01/25"))).trimEnd().split("\r\n")
        assertTrue(lines[1].startsWith("2025-01-01"))
        assertTrue(lines[2].startsWith("31/02/25"))
    }

    @Test fun emptyExportHasOnlyTheHeader() {
        assertEquals(1, CsvExport.build(emptyList()).trimEnd().split("\r\n").size)
    }

    @Test fun fileName() {
        assertEquals("pingpaisa-transactions-2026-11-23.csv", CsvExport.fileName(LocalDate.of(2026, 11, 23)))
    }
}
