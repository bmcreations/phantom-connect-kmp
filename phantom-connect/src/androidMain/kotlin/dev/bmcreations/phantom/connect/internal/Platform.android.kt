package dev.bmcreations.phantom.connect.internal

import android.annotation.SuppressLint
import android.content.Context

/**
 * Holds the application [Context] for platform factory functions.
 * Must be initialized via [PhantomSdkInitializer.init] before using [PhantomSdk.create].
 */
@SuppressLint("StaticFieldLeak")
internal object PhantomSdkInitializer {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requireContext(): Context =
        appContext ?: throw IllegalStateException(
            "PhantomSdk.init(context) must be called before using PhantomSdk. " +
                "Call it in your Application.onCreate() or Activity.onCreate()."
        )
}

internal actual fun platformKeyStore(): Ed25519KeyStoreProvider =
    AndroidEd25519KeyStore.create(PhantomSdkInitializer.requireContext())

internal actual fun platformSessionStore(): SessionStoreProvider =
    AndroidSessionStore.create(PhantomSdkInitializer.requireContext())

internal actual fun platformConnectSheetProvider(): ConnectSheetProvider =
    AndroidConnectSheetProvider()

internal actual fun getPlatform(): String = "android"

internal actual fun initPlatform(context: Any) {
    PhantomSdkInitializer.init(context as android.content.Context)
}

internal actual val sdkType: String = "react-native" // hack to work around validation on server
