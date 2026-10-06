package net.wault.importer

object Csv {

    fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        var fieldStarted = false

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
            fieldStarted = false
        }

        fun endRow() {
            endField()
            if (row.size > 1 || row.firstOrNull()?.isNotEmpty() == true) rows.add(row.toList())
            row.clear()
        }

        val source = text.removePrefix("﻿")

        while (index < source.length) {
            val char = source[index]
            when {
                quoted && char == '"' && index + 1 < source.length && source[index + 1] == '"' -> {
                    field.append('"')
                    index += 2
                    continue
                }

                char == '"' && (quoted || !fieldStarted) -> {
                    quoted = !quoted
                    fieldStarted = true
                }

                !quoted && char == ',' -> endField()

                !quoted && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && index + 1 < source.length && source[index + 1] == '\n') index++
                    endRow()
                }

                else -> {
                    field.append(char)
                    fieldStarted = true
                }
            }
            index++
        }

        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
