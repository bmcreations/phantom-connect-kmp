package dev.bmcreations.phantom.connect

import dev.bmcreations.phantom.connect.internal.platform.SdkLogger
import kotlin.test.*

class LoggerTest {

    @BeforeTest
    fun setup() {
        SdkLogger.logger = null
    }

    @AfterTest
    fun teardown() {
        SdkLogger.logger = null
    }

    @Test
    fun loggerReceivesCallsAtCorrectLevels() {
        val logs = mutableListOf<Triple<LogLevel, String, String>>()
        SdkLogger.logger = PhantomLogger { level, tag, message ->
            logs.add(Triple(level, tag, message))
        }

        SdkLogger.debug("TestTag", "debug msg")
        SdkLogger.info("TestTag", "info msg")
        SdkLogger.warn("TestTag", "warn msg")
        SdkLogger.error("TestTag", "error msg")

        assertEquals(4, logs.size)
        assertEquals(Triple(LogLevel.DEBUG, "TestTag", "debug msg"), logs[0])
        assertEquals(Triple(LogLevel.INFO, "TestTag", "info msg"), logs[1])
        assertEquals(Triple(LogLevel.WARN, "TestTag", "warn msg"), logs[2])
        assertEquals(Triple(LogLevel.ERROR, "TestTag", "error msg"), logs[3])
    }

    @Test
    fun noOpWhenLoggerIsNull() {
        SdkLogger.logger = null
        // Should not throw
        SdkLogger.debug("Tag", "msg")
        SdkLogger.info("Tag", "msg")
        SdkLogger.warn("Tag", "msg")
        SdkLogger.error("Tag", "msg")
    }

    @Test
    fun loggerCanBeReplacedAtRuntime() {
        val firstLogs = mutableListOf<String>()
        val secondLogs = mutableListOf<String>()

        SdkLogger.logger = PhantomLogger { _, _, message -> firstLogs.add(message) }
        SdkLogger.info("Tag", "first")

        SdkLogger.logger = PhantomLogger { _, _, message -> secondLogs.add(message) }
        SdkLogger.info("Tag", "second")

        assertEquals(listOf("first"), firstLogs)
        assertEquals(listOf("second"), secondLogs)
    }
}
