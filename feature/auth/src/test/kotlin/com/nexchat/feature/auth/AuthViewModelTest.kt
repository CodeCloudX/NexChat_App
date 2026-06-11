package com.nexchat.feature.auth

import com.google.common.truth.Truth.assertThat
import com.nexchat.core.common.Resource
import com.nexchat.feature.auth.repository.AuthRepository
import com.nexchat.feature.auth.viewmodel.AuthSideEffect
import com.nexchat.feature.auth.viewmodel.AuthUiState
import com.nexchat.feature.auth.viewmodel.AuthViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repo: AuthRepository = mockk()
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AuthViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendMagicLink with no at-sign emits Error state`() = runTest {
        viewModel.onSendMagicLink("invalidemail")
        assertThat(viewModel.uiState.value).isInstanceOf(AuthUiState.Error::class.java)
    }

    @Test
    fun `sendMagicLink with valid email emits MagicLinkSent and cooldown starts at 60`() = runTest {
        coEvery { repo.sendMagicLink(any()) } returns Resource.Success(Unit)

        viewModel.onSendMagicLink("test@example.com")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(AuthUiState.MagicLinkSent::class.java)
        assertThat((state as AuthUiState.MagicLinkSent).cooldownSecs).isEqualTo(60)
    }

    @Test
    fun `verifyToken for new user emits NavigateProfileSetup side effect`() = runTest {
        coEvery { repo.verifyMagicLink(any()) } returns Resource.Success(AuthRepository.AuthResult(isNewUser = true))

        val effects = mutableListOf<AuthSideEffect>()
        val job = launch { viewModel.sideEffect.collect { effects.add(it) } }

        viewModel.onVerifyToken("sometoken")
        advanceUntilIdle()

        assertThat(effects).contains(AuthSideEffect.NavigateProfileSetup)
        job.cancel()
    }

    @Test
    fun `verifyToken for existing user emits NavigateHome side effect`() = runTest {
        coEvery { repo.verifyMagicLink(any()) } returns Resource.Success(AuthRepository.AuthResult(isNewUser = false))

        val effects = mutableListOf<AuthSideEffect>()
        val job = launch { viewModel.sideEffect.collect { effects.add(it) } }

        viewModel.onVerifyToken("sometoken")
        advanceUntilIdle()

        assertThat(effects).contains(AuthSideEffect.NavigateHome)
        job.cancel()
    }

    @Test
    fun `verifyToken error emits Error state`() = runTest {
        coEvery { repo.verifyMagicLink(any()) } returns Resource.Error(Exception("Bad token"), "Bad token")

        viewModel.onVerifyToken("badtoken")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(AuthUiState.Error::class.java)
        assertThat((state as AuthUiState.Error).message).isEqualTo("Bad token")
    }
}
