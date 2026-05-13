package dev.bmcreations.phantom.connect.internal.platform

import dev.bmcreations.phantom.connect.internal.auth.SessionStoreProvider
import dev.bmcreations.phantom.connect.internal.crypto.Ed25519KeyStoreProvider
import dev.bmcreations.phantom.connect.internal.ui.ConnectSheetProvider

internal expect fun platformKeyStore(): Ed25519KeyStoreProvider
internal expect fun platformP256KeyStore(): dev.bmcreations.phantom.connect.internal.crypto.P256KeyStoreProvider
internal expect fun platformSessionStore(): SessionStoreProvider
internal expect fun platformConnectSheetProvider(): ConnectSheetProvider
internal expect fun initPlatform(context: Any)

internal expect fun getPlatform(): String

internal expect val sdkType: String
