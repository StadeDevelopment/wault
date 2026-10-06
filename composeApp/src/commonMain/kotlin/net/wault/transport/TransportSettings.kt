package net.wault.transport

import net.wault.db.WaultDb

data class TransportConfig(val type: TransportType, val enabled: Boolean, val config: String)

class TransportSettings(private val db: WaultDb) {

    fun all(): List<TransportConfig> {
        val byName = db.waultDbQueries.selectTransportConfig().executeAsList().associateBy { it.name }
        return TransportType.entries.map { type ->
            val row = byName[type.name]
            TransportConfig(
                type = type,
                enabled = row?.enabled?.let { it == 1L } ?: defaultEnabled(type),
                config = row?.settings?.decodeToString() ?: ""
            )
        }
    }

    fun get(type: TransportType): TransportConfig =
        all().first { it.type == type }

    fun setEnabled(type: TransportType, enabled: Boolean) {
        val current = get(type)
        db.waultDbQueries.upsertTransportConfig(
            type.name,
            if (enabled) 1L else 0L,
            current.config.encodeToByteArray()
        )
    }

    fun setConfig(type: TransportType, config: String) {
        val current = get(type)
        db.waultDbQueries.upsertTransportConfig(
            type.name,
            if (current.enabled) 1L else 0L,
            config.encodeToByteArray()
        )
    }

    private fun defaultEnabled(type: TransportType): Boolean = when (type) {
        TransportType.LAN -> true
        TransportType.TOR -> true
    }
}
