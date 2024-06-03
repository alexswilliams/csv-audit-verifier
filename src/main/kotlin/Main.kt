package org.example

import com.opencsv.CSVReader
import java.io.File
import java.io.FileReader
import java.math.BigDecimal

private const val FOLDER = "/Users/alex.williams1/Desktop/Audit"

fun main() {
    runForDate("./all-accruals-for-2023-10-13.csv", samplingThreshold = "2000.00")
    runForDate("./all-accruals-for-2023-10-15.csv", samplingThreshold = "3000.00")
}

private fun runForDate(fileName: String, samplingThreshold: String) {
    println("\n\nFile: " + fileName.substringAfterLast("/"))
    csvFileAsLineSequence(fileName) { lines ->
        val totalsByEntry = sumAllDepositAccrualsForJournal(lines)
        println("Accrual Totals by Ledger Entry:\n - ${totalsByEntry.entries.joinToString("\n - ")}")
        println("Total of accruals over all entries: ${totalsByEntry.values.sumOf { it }}")
    }
    csvFileAsLineSequence(fileName) { lines ->
        val rowsBeforeAndAfterThreshold = rowAfterCumulativeDepositOf(lines, samplingThreshold.toBigDecimal())
        println("Cutoff threshold: $samplingThreshold")
        println("Row before cutoff: ${rowsBeforeAndAfterThreshold.first}")
        println("Row after cutoff:  ${rowsBeforeAndAfterThreshold.second}")
    }
}

private fun sumAllDepositAccrualsForJournal(allAccrualsForDay: Sequence<InputRow>) =
    allAccrualsForDay
        .filter { it.currency == "Gbp" && it.customerType == "Consumer" && it.accrualType == "Deposit" && it.sequence == 0 }
        .groupBy({ it.ledgerEntryUid }) { it.netAccrual }
        .mapValues { it.value.sum() }

private fun rowAfterCumulativeDepositOf(allAccrualsForDay: Sequence<InputRow>, totalToPass: BigDecimal) = allAccrualsForDay
    .filter { it.currency == "Gbp" && it.customerType == "Consumer" && it.accrualType == "Deposit" && it.sequence == 0 }
    .runningFold(Row.ZERO) { acc: Row, it: InputRow -> Row(it.accountUid, it.netAccrual, acc.runningTotal + it.netAccrual) }
    .zipWithNext()
    .takeWhile { it.first.runningTotal <= totalToPass }
    .last()


// Helpers:

private fun Iterable<BigDecimal>.sum() = this.sumOf { it }

private data class Row(val accountUid: String, val accrual: BigDecimal, val runningTotal: BigDecimal) {
    companion object {
        val ZERO = Row("", BigDecimal.ZERO, BigDecimal.ZERO)
    }
}

private data class InputRow(
    val accountUid: String,
    val ledgerEntryUid: String,
    val currency: String,
    val customerType: String,
    val accrualType: String,
    val sequence: Int,
    val netAccrual: BigDecimal,
)

private fun csvFileAsLineSequence(fileName: String, body: (Sequence<InputRow>) -> Unit) =
    FileReader(File(FOLDER, fileName)).use { fileReader ->
        CSVReader(fileReader).use { file ->
            file.skip(1) // header row
            body(
                file.asSequence().map {
                    InputRow(
                        accountUid = it[0],
                        ledgerEntryUid = it[1],
                        currency = it[3],
                        customerType = it[4],
                        accrualType = it[5],
                        sequence = it[10].toInt(),
                        netAccrual = it[7].toBigDecimal() + it[8].toBigDecimal() + it[9].toBigDecimal(),
                    )
                })
        }
    }
