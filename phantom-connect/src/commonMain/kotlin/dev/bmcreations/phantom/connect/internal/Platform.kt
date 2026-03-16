package dev.bmcreations.phantom.connect.internal

internal expect fun platformKeyStore(): Ed25519KeyStoreProvider
internal expect fun platformSessionStore(): SessionStoreProvider
internal expect fun platformConnectSheetProvider(): ConnectSheetProvider
internal expect fun initPlatform(context: Any)

internal expect val sdkType: String
