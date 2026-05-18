package dev.bmcreations.phantom.connect.wallet

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

/**
 * Android [DeeplinkLauncher] that opens the Phantom app via Intent
 * and waits for the callback via [PhantomWalletCallbackActivity].
 *
 * During a multi-hop ceremony, the callback activity stays alive as a
 * transparent overlay between hops so the host app never flashes.
 */
class AndroidDeeplinkLauncher(
    private val context: Context,
) : DeeplinkLauncher {

    override fun beginCeremony() {
        PhantomWalletCallbackActivity.stayAlive = true
    }

    override fun endCeremony() {
        PhantomWalletCallbackActivity.stayAlive = false
        PhantomWalletCallbackActivity.dismiss()
    }

    override suspend fun launch(url: String): DeeplinkResult {
        val deferred = CompletableDeferred<DeeplinkResult>()
        PhantomWalletCallbackActivity.pendingResult = deferred

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            PhantomWalletCallbackActivity.pendingResult = null
            return DeeplinkResult.Error(
                IllegalStateException("Cannot open Phantom app. Is it installed?", e)
            )
        }

        return deferred.await()
    }

    override suspend fun isAppInstalled(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo("app.phantom", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
