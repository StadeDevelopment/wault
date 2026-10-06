package net.wault.db

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.wault.requireAppContext
import java.io.File

actual fun platformDriverFactory(): DriverFactory = object : DriverFactory {
    override fun create(path: String): SqlDriver {
        File(path).parentFile?.mkdirs()
        return AndroidSqliteDriver(
            schema = WaultDb.Schema,
            context = requireAppContext(),
            name = path,
            callback = object : AndroidSqliteDriver.Callback(WaultDb.Schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    db.setForeignKeyConstraintsEnabled(true)
                    db.enableWriteAheadLogging()
                }
            }
        )
    }
}
