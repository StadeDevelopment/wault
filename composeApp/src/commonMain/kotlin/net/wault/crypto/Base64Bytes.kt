package net.wault.crypto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object Base64Bytes : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Base64Bytes", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) =
        encoder.encodeString(Base64.UrlSafe.encode(value))

    override fun deserialize(decoder: Decoder): ByteArray =
        Base64.UrlSafe.decode(decoder.decodeString())
}

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.toBase64Url(): String = Base64.UrlSafe.encode(this)

@OptIn(ExperimentalEncodingApi::class)
fun String.base64UrlToBytes(): ByteArray = Base64.UrlSafe.decode(this)
