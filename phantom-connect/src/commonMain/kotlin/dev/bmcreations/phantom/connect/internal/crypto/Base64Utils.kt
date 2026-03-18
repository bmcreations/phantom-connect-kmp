package dev.bmcreations.phantom.connect.internal.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
internal fun ByteArray.toBase64Url(): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(this)

@OptIn(ExperimentalEncodingApi::class)
internal fun String.fromBase64Url(): ByteArray =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(this)
