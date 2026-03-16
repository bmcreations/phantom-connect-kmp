package dev.bmcreations.phantom.connect.internal

import dev.bmcreations.phantom.connect.*
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "AuthOrchestrator"

internal class AuthOrchestrator(
    private val client: PhantomClient,
    private val keyStore: Ed25519KeyStoreProvider,
    private val sessionStore: SessionStoreProvider,
    private val oauthLauncher: OAuthLauncher,
    private val stamper: Ed25519Stamper,
    private val config: PhantomSdkConfig,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) {
    companion object {
        private val AUTHENTICATOR_TTL = 7.days
        private val RENEWAL_WINDOW = 2.days
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Connect with a social provider (Google, Apple).
     * Launches OAuth in a secure browser and waits for the redirect.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun connectWithSocial(provider: AuthProvider): ConnectResult {
        SdkLogger.info(TAG, "Connect started with provider=${provider.id}")
        // Phase 1: Clear old keys and generate a fresh keypair
        keyStore.delete(KeyStoreTags.ACTIVE)
        keyStore.delete(KeyStoreTags.PENDING)
        val publicKey = stamper.ensureKeyPair()
        val publicKeyBase58 = publicKey.toBase58()

        val sessionId = Uuid.random().toString()
        val platform = getPlatform()

        val loginUrl = buildLoginUrl(
            publicKey = publicKeyBase58,
            provider = provider,
            sessionId = sessionId,
            platform = platform,
        )

        // Phase 2: Launch OAuth and wait for redirect
        val oauthResult = oauthLauncher.launch(loginUrl, config.redirectScheme)

        return when (oauthResult) {
            is OAuthResult.Success -> {
                val params = oauthResult.params
                val walletId = params["wallet_id"]
                    ?: return ConnectResult.Error(IllegalStateException("Missing wallet_id in redirect"))
                val organizationId = params["organization_id"]
                    ?: return ConnectResult.Error(IllegalStateException("Missing organization_id in redirect"))
                val selectedAccountIndex = params["selected_account_index"]?.toIntOrNull() ?: 0
                val expiresInMs = params["expires_in_ms"]?.toLongOrNull() ?: AUTHENTICATOR_TTL.inWholeMilliseconds
                val authUserId = params["auth_user_id"]

                val now = timeProvider.now()
                val username = "user-${Uuid.random()}"

                val session = PhantomSession(
                    walletId = walletId,
                    organizationId = organizationId,
                    providerId = provider.id,
                    accountDerivationIndex = selectedAccountIndex,
                    authUserId = authUserId,
                    sessionId = sessionId,
                    expiresAt = now.toEpochMilliseconds() + expiresInMs,
                    authenticatorCreatedAt = now.toEpochMilliseconds(),
                    authenticatorExpiresAt = now.toEpochMilliseconds() + expiresInMs,
                    walletType = WalletType.UserWallet,
                    username = username,
                )

                // Fetch accounts for all configured chains
                val sessionWithAddresses = try {
                    val addresses = fetchAddresses(
                        organizationId = organizationId,
                        walletId = walletId,
                        accountIndex = selectedAccountIndex,
                        authUserId = authUserId,
                    )
                    session.copy(addresses = addresses)
                } catch (e: Exception) {
                    SdkLogger.warn(TAG, "Failed to fetch addresses: ${e.message}")
                    session
                }

                sessionStore.save(sessionWithAddresses)
                SdkLogger.info(TAG, "Connect succeeded for provider=${provider.id}")
                ConnectResult.Success(sessionWithAddresses)
            }

            is OAuthResult.Cancelled -> {
                SdkLogger.info(TAG, "Connect cancelled: ${oauthResult.reason}")
                ConnectResult.Cancelled(oauthResult.reason)
            }
            is OAuthResult.Error -> {
                SdkLogger.error(TAG, "Connect failed: ${oauthResult.cause.message}")
                ConnectResult.Error(oauthResult.cause)
            }
        }
    }

    /**
     * Create an app wallet programmatically (no browser).
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createAppWallet(): ConnectResult {
        keyStore.delete(KeyStoreTags.ACTIVE)
        keyStore.delete(KeyStoreTags.PENDING)
        val publicKey = stamper.ensureKeyPair()

        val username = "user-${Uuid.random()}"
        val sessionId = Uuid.random().toString()

        return try {
            val orgResult = client.createOrganization(
                organizationName = "phantom-kmp-${Uuid.random()}",
                username = username,
                publicKeyBytes = publicKey,
            )
            val organizationId = orgResult.jsonObject["organizationId"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing organizationId in createOrganization response")

            // Create wallet with all configured chain derivation paths
            val derivationPaths = config.chains.map { it.derivationPath(0) }
            val walletResult = client.createWallet(
                organizationId = organizationId,
                walletName = "Default Wallet",
                accounts = derivationPaths,
            )
            val walletId = walletResult.jsonObject["walletId"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing walletId in createWallet response")

            val now = timeProvider.now()
            val expiresInMs = AUTHENTICATOR_TTL.inWholeMilliseconds

            val session = PhantomSession(
                walletId = walletId,
                organizationId = organizationId,
                providerId = "device",
                accountDerivationIndex = 0,
                authUserId = null,
                sessionId = sessionId,
                expiresAt = now.toEpochMilliseconds() + expiresInMs,
                authenticatorCreatedAt = now.toEpochMilliseconds(),
                authenticatorExpiresAt = now.toEpochMilliseconds() + expiresInMs,
                walletType = WalletType.AppWallet,
                username = username,
            )

            sessionStore.save(session)
            ConnectResult.Success(session)
        } catch (e: Exception) {
            ConnectResult.Error(e)
        }
    }

    /** Load the current session, renewing the authenticator if needed. */
    suspend fun getSession(): PhantomSession? {
        val session = sessionStore.load() ?: return null
        val renewed = renewAuthenticatorIfNeeded(session)

        // Backfill addresses if missing
        if (renewed.addresses.isEmpty()) {
            val backfilled = try {
                val addresses = fetchAddresses(
                    organizationId = renewed.organizationId,
                    walletId = renewed.walletId,
                    accountIndex = renewed.accountDerivationIndex,
                    authUserId = renewed.authUserId,
                )
                renewed.copy(addresses = addresses)
            } catch (e: Exception) {
                SdkLogger.warn(TAG, "Failed to backfill addresses: ${e.message}")
                renewed
            }
            if (backfilled.addresses.isNotEmpty()) {
                sessionStore.save(backfilled)
            }
            return backfilled
        }

        return renewed
    }

    /** Clear session and keys. */
    suspend fun logout() {
        keyStore.delete(KeyStoreTags.ACTIVE)
        keyStore.delete(KeyStoreTags.PENDING)
        sessionStore.clear()
    }

    /** Sign a UTF-8 message using the current session. */
    suspend fun signMessage(message: String, chain: Chain = Chain.Solana): String {
        val session = requireSession()
        val derivationPath = resolveDerivationPath(session, chain)
        val result = client.signUtf8Message(
            organizationId = session.organizationId,
            walletId = session.walletId,
            message = message,
            chain = chain,
            derivationPath = derivationPath,
            authUserId = session.authUserId,
        )
        return result.jsonObject["signature"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing signature in response")
    }

    /** Sign a transaction without broadcasting. */
    suspend fun signTransaction(transactionBase64: String, chain: Chain = Chain.Solana): String {
        val session = requireSession()
        val derivationPath = resolveDerivationPath(session, chain)
        val result = client.signTransaction(
            organizationId = session.organizationId,
            walletId = session.walletId,
            transactionBase64 = transactionBase64,
            chain = chain,
            derivationPath = derivationPath,
            authUserId = session.authUserId,
        )
        return result.jsonObject["signedTransaction"]?.jsonPrimitive?.content
            ?: result.jsonObject["signature"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing signed transaction in response")
    }

    /** Sign and submit a transaction. */
    suspend fun signAndSendTransaction(transactionBase64: String, chain: Chain = Chain.Solana): String {
        val session = requireSession()
        val derivationPath = resolveDerivationPath(session, chain)
        val account = session.address(chain)

        // User wallets on Solana require the /prepare step for spending limits
        val transactionForSigning = if (
            session.walletType == WalletType.UserWallet &&
            chain == Chain.Solana &&
            account != null
        ) {
            val authPubKey = try { stamper.getPublicKeyBase58() } catch (_: Exception) { null }
            client.prepare(
                transactionBase64 = transactionBase64,
                organizationId = session.organizationId,
                chain = chain,
                network = config.network,
                account = account,
                authenticatorPublicKey = authPubKey,
                xRpcMethod = "signAndSendTransaction",
            )
        } else {
            transactionBase64
        }

        val response = client.signAndSubmitTransaction(
            organizationId = session.organizationId,
            walletId = session.walletId,
            transactionBase64 = transactionForSigning,
            chain = chain,
            derivationPath = derivationPath,
            network = config.network,
            account = account,
            authUserId = session.authUserId,
        )

        // Extract tx hash from rpc_submission_result (top-level field, not inside result)
        // Can be: { result: "txSignature" } or { result: { result: "txSignature" } }
        val submissionResult = response.rpcSubmissionResult
        if (submissionResult is JsonObject) {
            val error = submissionResult["error"]
            if (error != null) {
                val errorMsg = when (error) {
                    is JsonObject -> error["data"]?.jsonPrimitive?.content
                        ?: error["message"]?.jsonPrimitive?.content
                        ?: error.toString()
                    else -> error.toString()
                }
                throw IllegalStateException("Transaction broadcast failed: $errorMsg")
            }
            val envelope = submissionResult["result"]
            if (envelope != null) {
                // Direct string: { result: "txSignature" }
                if (envelope is JsonPrimitive && envelope.isString) {
                    return envelope.content
                }
                // Nested: { result: { result: "txSignature" } }
                if (envelope is JsonObject) {
                    envelope["result"]?.jsonPrimitive?.content?.let { return it }
                    envelope["error"]?.let {
                        throw IllegalStateException("Transaction broadcast failed: $it")
                    }
                }
            }
        }

        // Fallback: look in result object
        val result = response.result
        return result?.jsonObject?.get("transactionHash")?.jsonPrimitive?.content
            ?: result?.jsonObject?.get("signature")?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing transaction hash in response")
    }

    /** Ethereum personal_sign (EIP-191). */
    suspend fun signPersonalMessage(message: String): String {
        val session = requireSession()
        val derivationPath = resolveDerivationPath(session, Chain.Ethereum)
        SdkLogger.debug(TAG, "signPersonalMessage on Ethereum")
        val result = client.signPersonalMessage(
            organizationId = session.organizationId,
            walletId = session.walletId,
            message = message,
            derivationPath = derivationPath,
            authUserId = session.authUserId,
        )
        return result.jsonObject["signature"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing signature in response")
    }

    /** Ethereum signTypedData_v4 (EIP-712). */
    suspend fun signTypedData(typedDataJson: String): String {
        val session = requireSession()
        val derivationPath = resolveDerivationPath(session, Chain.Ethereum)
        SdkLogger.debug(TAG, "signTypedData on Ethereum")
        val result = client.signTypedData(
            organizationId = session.organizationId,
            walletId = session.walletId,
            typedDataJson = typedDataJson,
            derivationPath = derivationPath,
            authUserId = session.authUserId,
        )
        return result.jsonObject["signature"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("Missing signature in response")
    }

    /** Batch sign multiple transactions in a single KMS RPC call. */
    suspend fun signAllTransactions(transactionsBase64: List<String>, chain: Chain = Chain.Solana): List<String> {
        val session = requireSession()
        val derivationPath = resolveDerivationPath(session, chain)
        SdkLogger.debug(TAG, "signAllTransactions count=${transactionsBase64.size} chain=${chain.id}")
        val result = client.signAllTransactions(
            organizationId = session.organizationId,
            walletId = session.walletId,
            transactionsBase64 = transactionsBase64,
            chain = chain,
            derivationPath = derivationPath,
            authUserId = session.authUserId,
        )
        val signedArray = result.jsonObject["signedTransactions"]?.jsonArray
            ?: throw IllegalStateException("Missing signedTransactions in response")
        return signedArray.map { it.jsonPrimitive.content }
    }

    // ── Private Helpers ──

    private suspend fun requireSession(): PhantomSession =
        getSession() ?: throw IllegalStateException("No active session")

    /**
     * Resolve the derivation path for a chain from session addresses,
     * falling back to computing it from the account derivation index.
     */
    private fun resolveDerivationPath(session: PhantomSession, chain: Chain): String {
        return session.addresses
            .firstOrNull { it.chainId == chain.id }
            ?.derivationPath
            ?: chain.derivationPath(session.accountDerivationIndex)
    }

    private suspend fun fetchAddresses(
        organizationId: String,
        walletId: String,
        accountIndex: Int,
        authUserId: String?,
    ): List<WalletAddress> {
        val derivationPaths = config.chains.map { chain ->
            chain to chain.derivationPath(accountIndex)
        }

        val allPaths = derivationPaths.map { it.second }
        val accountsResult = client.getAccounts(
            organizationId = organizationId,
            walletId = walletId,
            accounts = allPaths,
            authUserId = authUserId,
        )

        return parseAddresses(accountsResult, derivationPaths)
    }

    private fun parseAddresses(
        result: JsonElement,
        derivationPaths: List<Pair<Chain, String>>,
    ): List<WalletAddress> {
        val accounts = when (result) {
            is JsonArray -> result
            is JsonObject -> result["accounts"]?.jsonArray ?: return emptyList()
            else -> return emptyList()
        }

        return accounts.mapIndexedNotNull { index, account ->
            val address = account.jsonObject["address"]?.jsonPrimitive?.content
                ?: return@mapIndexedNotNull null
            val (chain, derivationPath) = derivationPaths.getOrElse(index) {
                derivationPaths.first()
            }
            WalletAddress(
                chainId = chain.id,
                address = address,
                derivationPath = derivationPath,
            )
        }
    }

    // ── Authenticator Renewal (Three-Phase Commit) ──

    private suspend fun renewAuthenticatorIfNeeded(session: PhantomSession): PhantomSession {
        val now = timeProvider.now()
        val expiresAt = Instant.fromEpochMilliseconds(session.authenticatorExpiresAt)
        val renewalThreshold = expiresAt - RENEWAL_WINDOW

        if (now < renewalThreshold) {
            return session
        }

        SdkLogger.info(TAG, "Authenticator renewal triggered")
        return try {
            rotateAuthenticator(session)
        } catch (e: Exception) {
            SdkLogger.warn(TAG, "Authenticator renewal failed: ${e.message}")
            session
        }
    }

    private suspend fun rotateAuthenticator(session: PhantomSession): PhantomSession {
        keyStore.delete(KeyStoreTags.PENDING)
        val pendingPublicKey = keyStore.generateKeyPair(KeyStoreTags.PENDING)

        try {
            client.createAuthenticator(
                organizationId = session.organizationId,
                username = session.username,
                publicKeyBytes = pendingPublicKey,
                replaceExpirable = true,
                authUserId = session.authUserId,
            )

            commitRotation()

            val now = timeProvider.now()
            val updatedSession = session.copy(
                authenticatorCreatedAt = now.toEpochMilliseconds(),
                authenticatorExpiresAt = now.toEpochMilliseconds() + AUTHENTICATOR_TTL.inWholeMilliseconds,
            )
            sessionStore.save(updatedSession)
            return updatedSession
        } catch (e: Exception) {
            rollbackRotation()
            throw e
        }
    }

    private suspend fun commitRotation() {
        keyStore.delete(KeyStoreTags.ACTIVE)
        keyStore.move(KeyStoreTags.PENDING, KeyStoreTags.ACTIVE)
    }

    private suspend fun rollbackRotation() {
        keyStore.delete(KeyStoreTags.PENDING)
    }

    // ── URL Construction ──

    private fun buildLoginUrl(
        publicKey: String,
        provider: AuthProvider,
        sessionId: String,
        platform: String,
    ): String {
        val params = buildMap {
            put("public_key", publicKey)
            put("app_id", config.appId)
            put("redirect_uri", config.redirectUri)
            put("session_id", sessionId)
            put("provider", provider.id)
            put("clear_previous_session", "false")
            put("allow_refresh", "true")
            put("sdk_version", config.sdkVersion)
            put("sdk_type", sdkType)
            put("platform", platform)
        }
        val queryString = params.entries.joinToString("&") { (k, v) ->
            "$k=${urlEncode(v)}"
        }
        return "${config.loginBaseUrl}/login?$queryString"
    }
}

// Simple URL encoding for query params
private fun urlEncode(value: String): String = buildString {
    for (c in value) {
        when {
            c.isLetterOrDigit() || c in "-_.~" -> append(c)
            c == ' ' -> append("+")
            else -> {
                val bytes = c.toString().encodeToByteArray()
                for (b in bytes) {
                    append('%')
                    append((b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}

internal expect fun getPlatform(): String
