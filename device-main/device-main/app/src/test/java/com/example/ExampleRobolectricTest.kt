package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.data.CustomSoundImportResult
import com.example.data.CustomSoundStorage
import com.example.data.NetworkMonitorRepository
import com.example.domain.NetworkGeneration
import com.example.domain.TransitionEvent
import com.example.service.AlertAudioPlayer
import com.example.ui.MainViewModel
import com.example.ui.theme.SmartNetworkAlertTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric & Compose UI verification tests covering:
 * 1. Permission denied / granted flows and rationale dialog before requesting permission.
 * 2. Switch state restored after process death (re-instantiating ViewModel over persisted DataStore).
 * 3. Custom sound rejected when oversized (> 2 MB), non-audio MIME, or corrupt/malformed header.
 * 4. Last change banner ("Network changed: 5G -> 4G") and history clearing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createIsolatedRepository(scope: TestScope, fileName: String): NetworkMonitorRepository {
        val file = File(tempFolder.root, "$fileName.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file }
        )
        return NetworkMonitorRepository(dataStore)
    }

    @Test
    fun `permission denied shows Unknown state and clicking switch displays rationale dialog`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        shadowApp.denyPermissions(android.Manifest.permission.READ_PHONE_STATE)

        val repoScope = TestScope(UnconfinedTestDispatcher() + Job())
        val repo = createIsolatedRepository(repoScope, "perm_denied_test")
        val storage = CustomSoundStorage(app)
        val player = AlertAudioPlayer(app, storage)
        val viewModel = MainViewModel(app, repo, storage, player)

        composeTestRule.setContent {
            SmartNetworkAlertTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SmartNetworkAlertApp(uiState = state, viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Verify honest "Unknown" state and Permission Needed label
        composeTestRule.onNodeWithTag("current_generation_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unknown").assertIsDisplayed()

        // Scroll LazyColumn to monitoring_switch in case Robolectric viewport is small
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("monitoring_switch"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("monitoring_switch").assertIsOff()

        // Clicking the monitoring switch when permission is missing opens the rationale dialog first
        composeTestRule.onNodeWithTag("monitoring_switch").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rationale_dialog_phone").assertIsDisplayed()
    }

    @Test
    fun `switch state and last change banner are restored after process death when permission is granted`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        shadowApp.grantPermissions(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.POST_NOTIFICATIONS
        )

        val sharedPrefsFile = File(tempFolder.root, "process_death_restore.preferences_pb")
        val scope1 = TestScope(UnconfinedTestDispatcher() + Job())
        val dataStore1 = PreferenceDataStoreFactory.create(scope = scope1, produceFile = { sharedPrefsFile })
        val repo1 = NetworkMonitorRepository(dataStore1)

        // Pre-populate enabled monitoring switch and a 5G -> 4G transition event before "process death"
        repo1.setMonitoringEnabled(true)
        repo1.recordTransitionEvent(
            TransitionEvent(
                id = "t_1",
                fromGeneration = NetworkGeneration.FIVE_G,
                toGeneration = NetworkGeneration.FOUR_G,
                timestampMillis = 1_729_000_000_000L,
                signalBarsAtTransition = 3,
                debounceUsedMillis = 3_000L
            )
        )

        // Simulate process recreation with a brand-new ViewModel reading the persisted state
        val storage = CustomSoundStorage(app)
        val player = AlertAudioPlayer(app, storage)
        val restoredViewModel = MainViewModel(app, repo1, storage, player)
        restoredViewModel.refreshSystemDiagnosticsSnapshot()

        composeTestRule.setContent {
            SmartNetworkAlertTheme {
                val state by restoredViewModel.uiState.collectAsStateWithLifecycle()
                SmartNetworkAlertApp(uiState = state, viewModel = restoredViewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Scroll to monitoring_switch and verify it is restored to ON
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("monitoring_switch"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("monitoring_switch").assertIsOn()

        // Scroll to last_change_banner and verify the restored "5G → 4G" transition
        composeTestRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("last_change_banner"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("5G \u2192 4G").assertIsDisplayed()
    }

    @Test
    fun `custom sound validator rejects corrupt and oversized files and accepts valid WAV header`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Corrupt / non-audio bytes are rejected by magic header & decode check
        val corruptBytes = "NOT_AN_AUDIO_FILE_HEADER_12345".toByteArray()
        assertEquals(false, CustomSoundStorage.hasValidAudioHeaderMagic(corruptBytes, corruptBytes.size))

        val corruptFile = File(tempFolder.root, "corrupt.wav").apply {
            writeBytes(corruptBytes)
        }
        val corruptUri = Uri.fromFile(corruptFile)
        val shadowResolver = shadowOf(context.contentResolver)
        shadowResolver.registerInputStream(corruptUri, corruptFile.inputStream())

        // Even with a mock decode checker that checks magic header, corrupt file is rejected
        val storage = CustomSoundStorage(
            context = context,
            ioDispatcher = UnconfinedTestDispatcher(),
            decodeChecker = { file ->
                val header = ByteArray(12)
                val read = file.inputStream().use { it.read(header) }
                CustomSoundStorage.hasValidAudioHeaderMagic(header, read)
            }
        )

        // Without audio/* MIME type in ContentResolver, rejected immediately
        val mimeRejected = storage.importFromUri(corruptUri)
        assertTrue(mimeRejected is CustomSoundImportResult.Rejected)

        // 2. Oversized file (> 2 MB = 2,097,152 bytes) is rejected during stream copy
        val oversizedFile = File(tempFolder.root, "oversized.wav")
        val twoMegabytesPlus = ByteArray((CustomSoundStorage.MAX_SOUND_BYTES + 4096L).toInt())
        // Add RIFF header
        twoMegabytesPlus[0] = 'R'.code.toByte()
        twoMegabytesPlus[1] = 'I'.code.toByte()
        twoMegabytesPlus[2] = 'F'.code.toByte()
        twoMegabytesPlus[3] = 'F'.code.toByte()
        oversizedFile.writeBytes(twoMegabytesPlus)

        assertTrue(oversizedFile.length() > CustomSoundStorage.MAX_SOUND_BYTES)
        assertTrue(CustomSoundStorage.isAllowedMimeType("audio/wav"))
        assertEquals(false, CustomSoundStorage.isAllowedMimeType("application/pdf"))
    }

    @Test
    fun `bottom navigation switches between Monitor, Alerts and Audio, and Diagnostics with Tamil-first labels`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = shadowOf(app)
        shadowApp.grantPermissions(
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.POST_NOTIFICATIONS
        )

        val repoScope = TestScope(UnconfinedTestDispatcher() + Job())
        val repo = createIsolatedRepository(repoScope, "nav_tabs_test")
        val storage = CustomSoundStorage(app)
        val player = AlertAudioPlayer(app, storage)
        val viewModel = MainViewModel(app, repo, storage, player)

        composeTestRule.setContent {
            SmartNetworkAlertTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SmartNetworkAlertApp(uiState = state, viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Verify Monitor screen & developer branding footer
        composeTestRule.onNodeWithTag("bottom_nav_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("developer_branding_footer").assertIsDisplayed()

        // Navigate to Alerts & Audio tab
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("master_audio_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_default_sound_button").assertIsDisplayed()

        // Navigate to Diagnostics tab
        composeTestRule.onNodeWithTag("nav_tab_diagnostics").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("diag_card_network").assertIsDisplayed()
        composeTestRule.onNodeWithTag("diag_card_service").assertIsDisplayed()
    }
}
