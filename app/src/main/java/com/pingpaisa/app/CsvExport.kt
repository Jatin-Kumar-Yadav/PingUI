package com.pingpaisa.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.pingpaisa.app.data.Transaction
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * Settings → Export your data (step 55): all transactions as a CSV file, sent through Android's
 * share sheet. [build] is plain Kotlin and unit-tested; [share] writes the file and opens the sheet.
 */
internal object CsvExport {

    val HEADER = listOf("Date", "Type", "Amount", "Merchant or payer", "Category", "Subcategory", "Note", "Source", "Reference")

    /** Oldest first. Dates become yyyy-MM-dd so spreadsheets sort them; unreadable dates are kept as stored. */
    fun build(all: List<Transaction>): String {
        val rows = all.map { t -> HomeSummary.parseDate(t.date) to t }
            .sortedWith(compareBy<Pair<LocalDate?, Transaction>> { it.first ?: LocalDate.MAX }.thenBy { it.second.id })
            .map { (date, t) ->
                listOf(
                    date?.toString() ?: t.date,
                    t.type,
                    String.format(Locale.US, "%.2f", t.amount),
                    t.recipient, t.category, t.subcategory, t.note,
                    if (t.source.equals("manual", ignoreCase = true)) "manual" else "SMS",
                    t.refNumber
                )
            }
        return (listOf(HEADER) + rows).joinToString("\r\n", postfix = "\r\n") { row -> row.joinToString(",") { cell(it) } }
    }

    /**
     * One CSV cell. Text that a spreadsheet would run as a formula (starting with = + - @, tab or CR)
     * gets a leading ' first; then cells with a comma, quote or line break are quoted, quotes doubled.
     * Our own numbers ("1250.00") and dates never start with those characters, so they stay numbers.
     */
    fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun fileName(today: LocalDate): String = "pingpaisa-transactions-$today.csv"

    /**
     * Writes the CSV to the app's private cache (folder "exports", listed in res/xml/file_paths.xml;
     * the previous export is deleted first) and opens the share sheet for it.
     */
    fun share(context: Context, all: List<Transaction>, today: LocalDate = LocalDate.now()) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(today))
        file.writeText(build(all), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "PingPaisa transactions")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Export your transactions").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
