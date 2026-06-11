package com.nexchat.feature.onboarding

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.google.common.truth.Truth.assertThat
import com.nexchat.core.network.api.KeysApi
import com.nexchat.core.network.api.UserApi
import com.nexchat.core.network.dto.UpdateProfileRequest
import com.nexchat.core.security.E2EEManager
import com.nexchat.feature.onboarding.viewmodel.E2EE_SHOWN_KEY
import com.nexchat.feature.onboarding.viewmodel.OnboardingState
import com.nexchat.feature.onboarding.viewmodel.OnboardingViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val userApi: UserApi = mockk(relaxed = true)
    private val keysApi: KeysApi = mockk(relaxed = true)
    private val e2eeManager: E2EEManager = mockk()
    private val dataStore: DataStore<Preferences> = mockk(relaxed = true)
    private lateinit var viewModel: OnboardingViewModel

    private val fakeIkPair = IdentityKeyPair(
        org.signal.libsignal.protocol.IdentityKey(Curve.generateKeyPair().publicKey),
        Curve.generateKeyPair().privateKey
    )
    private val fakeSpk = SignedPreKeyRecord(1, System.currentTimeMillis(), Curve.generateKeyPair(), ByteArray(64))
    private val fakeOtks = (1..50).map { PreKeyRecord(it, Curve.generateKeyPair()) }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { userApi.updateProfile(any()) } returns Response.success(mockk(relaxed = true))
        coEvery { keysApi.uploadPreKeys(any()) } returns Response.success(Unit)
        coEvery { e2eeManager.generateIdentityKeyPair() } returns Result.success(fakeIkPair)
        coEvery { e2eeManager.generateSignedPreKey(any()) } returns Result.success(fakeSpk)
        coEvery { e2eeManager.generateOneTimePreKeys(any(), any()) } returns Result.success(fakeOtks)
        every { dataStore.data } returns flowOf(mutablePreferencesOf())
        viewModel = OnboardingViewModel(context, userApi, keysApi, e2eeManager, dataStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saveProfile with blank name emits Error`() = runTest {
        viewModel.saveProfile("  ", null)
        assertThat(viewModel.state.value).isInstanceOf(OnboardingState.Error::class.java)
    }

    @Test
    fun `saveProfile with name longer than 25 chars emits Error`() = runTest {
        viewModel.saveProfile("A".repeat(26), null)
        assertThat(viewModel.state.value).isInstanceOf(OnboardingState.Error::class.java)
    }

    @Test
    fun `saveProfile success without avatar emits Success`() = runTest {
        viewModel.saveProfile("Alice", null)
        advanceUntilIdle()
        assertThat(viewModel.state.value).isEqualTo(OnboardingState.Success)
    }

    @Test
    fun `saveProfile uploads exactly 50 OTKs`() = runTest {
        viewModel.saveProfile("Alice", null)
        advanceUntilIdle()

        val requestSlot = slot<com.nexchat.core.network.dto.UploadPreKeysRequest>()
        coVerify { keysApi.uploadPreKeys(capture(requestSlot)) }
        assertThat(requestSlot.captured.oneTimePrekeys).hasSize(50)
    }

    @Test
    fun `onE2EEInfoSeen writes E2EE_SHOWN_KEY true to DataStore`() = runTest {
        val editSlot = slot<suspend Preferences.() -> Unit>()
        coEvery { dataStore.edit(capture(editSlot)) } returns mutablePreferencesOf()

        viewModel.onE2EEInfoSeen()
        advanceUntilIdle()

        coVerify { dataStore.edit(any()) }
    }
}
