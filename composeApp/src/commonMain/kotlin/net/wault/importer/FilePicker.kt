package net.wault.importer

data class PickedFile(val name: String, val text: String)

expect suspend fun pickTextFile(): PickedFile?
