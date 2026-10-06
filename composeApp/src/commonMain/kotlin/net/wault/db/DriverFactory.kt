package net.wault.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver

interface DriverFactory {
    fun create(path: String): SqlDriver
}

expect fun platformDriverFactory(): DriverFactory

class DatabaseSchemaException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

object DatabaseSchema {

    const val VERSION: Int = 1

    fun requireCompatible(driver: SqlDriver) {
        val probes = listOf(
            "SELECT id FROM Item LIMIT 0",
            "SELECT id FROM Folder LIMIT 0",
            "SELECT id FROM Device LIMIT 0",
            "SELECT deviceId FROM RatchetSession LIMIT 0",
            "SELECT deviceId FROM SyncCursor LIMIT 0",
            "SELECT key FROM KeyValue LIMIT 0"
        )
        for (sql in probes) {
            runCatching {
                driver.executeQuery(null, sql, { _: SqlCursor -> QueryResult.Value(Unit) }, 0)
            }.onFailure {
                throw DatabaseSchemaException("schema probe failed: $sql", it)
            }
        }
    }
}
