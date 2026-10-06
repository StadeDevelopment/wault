package net.wault.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

actual fun platformDriverFactory(): DriverFactory = object : DriverFactory {
    override fun create(path: String) = run {
        File(path).parentFile?.mkdirs()
        val existed = File(path).exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path", Properties())
        if (!existed) WaultDb.Schema.create(driver)
        driver.execute(null, "PRAGMA journal_mode=WAL", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        driver
    }
}
