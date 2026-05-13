package dev.bmcreations.phantom.connect

import dev.bmcreations.phantom.connect.internal.auth.TokenExchange
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class TokenExchangeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun exchangeAuthCodeSendsCorrectFormParams() = runTest {
        var capturedBody: String? = null
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            capturedUrl = request.url.toString()
            respond(
                content = """{"access_token":"at","refresh_token":"rt","token_type":"Bearer","expires_in":3600}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val exchange = TokenExchange(httpClient)

        exchange.exchangeAuthCode(
            authApiBaseUrl = "https://auth.phantom.app",
            clientId = "my-app",
            redirectUri = "myapp://callback",
            code = "auth-code-123",
            codeVerifier = "verifier-456",
        )

        assertNotNull(capturedBody)
        assertEquals("https://auth.phantom.app/oauth2/token", capturedUrl)
        assertTrue(capturedBody!!.contains("grant_type=authorization_code"))
        assertTrue(capturedBody!!.contains("client_id=my-app"))
        assertTrue(capturedBody!!.contains("code=auth-code-123"))
        assertTrue(capturedBody!!.contains("code_verifier=verifier-456"))
    }

    @Test
    fun exchangeAuthCodeReturnsTokenResponse() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"access_token":"eyJ.test.tok","refresh_token":"refresh-xyz","token_type":"Bearer","expires_in":7200}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val exchange = TokenExchange(httpClient)

        val response = exchange.exchangeAuthCode(
            authApiBaseUrl = "https://auth.phantom.app",
            clientId = "app",
            redirectUri = "app://cb",
            code = "code",
            codeVerifier = "verifier",
        )

        assertEquals("eyJ.test.tok", response.access_token)
        assertEquals("refresh-xyz", response.refresh_token)
        assertEquals("Bearer", response.token_type)
        assertEquals(7200L, response.expires_in)
    }

    @Test
    fun exchangeAuthCodeThrowsOnError() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"invalid_grant","error_description":"bad code"}""",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val exchange = TokenExchange(httpClient)

        assertFailsWith<IllegalStateException> {
            exchange.exchangeAuthCode(
                authApiBaseUrl = "https://auth.phantom.app",
                clientId = "app",
                redirectUri = "app://cb",
                code = "bad-code",
                codeVerifier = "verifier",
            )
        }
    }

    @Test
    fun refreshTokenSendsCorrectParams() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"access_token":"new-at","token_type":"Bearer","expires_in":3600}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val exchange = TokenExchange(httpClient)

        exchange.refreshToken(
            authApiBaseUrl = "https://auth.phantom.app",
            clientId = "my-app",
            redirectUri = "myapp://callback",
            refreshToken = "old-refresh-token",
        )

        assertNotNull(capturedBody)
        assertTrue(capturedBody!!.contains("grant_type=refresh_token"))
        assertTrue(capturedBody!!.contains("client_id=my-app"))
        assertTrue(capturedBody!!.contains("refresh_token=old-refresh-token"))
    }
}
