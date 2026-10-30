package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.CustomSoundStorage
import com.example.domain.NetworkGeneration
import com.example.domain.NetworkGenerationMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test suite for physical device / CI verification.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContextAndVerifySecurityConstraints() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(appContext.packageName.startsWith("com.aistudio.smartnetworkalert"))

        // Verify mapper and custom sound security helper in instrumented runtime
        assertEquals(
            NetworkGeneration.FIVE_G,
            NetworkGenerationMapper.map(
                networkType = NetworkGenerationMapper.NETWORK_TYPE_NR,
                overrideNetworkType = NetworkGenerationMapper.OVERRIDE_NETWORK_TYPE_NONE
            )
        )
        assertTrue(CustomSoundStorage.isAllowedMimeType("audio/mpeg"))
        assertFalse(CustomSoundStorage.isAllowedMimeType("text/html"))
    }
}
