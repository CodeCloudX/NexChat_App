package com.nexchat.feature.contacts.viewmodel

import com.google.common.truth.Truth.assertThat
import com.nexchat.core.common.AppDispatchers
import com.nexchat.core.network.dto.PublicUser
import com.nexchat.feature.contacts.repository.ContactsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ContactsViewModel search debounce routing and addContact isolation.
 *
 * Email queries debounce at 800 ms; phone (≥10 digits) at 500 ms; plain text never
 * hits the network. These thresholds intentionally match WhatsApp-style UX trade-offs:
 * shorter debounce for phone because users finish typing a number faster than an email.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactsViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(scheduler)
    private val dispatchers = AppDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher,
    )

    // io.mockk.mockk with relaxed=true so un-stubbed calls return safe defaults.
    private val contactsRepository: ContactsRepository = io.mockk.mockk(relaxed = true)

    private lateinit var viewModel: ContactsViewModel

    private val stubUser = PublicUser(
        id = "user-001",
        phone = null,
        email = "test@example.com",
        displayName = "Test User",
        avatarUrl = null,
        lastSeen = null,
    )

    @Before
    fun setUp() {
        // searchServer must return a well-typed Result so the ViewModel doesn't throw.
        coEvery { contactsRepository.searchServer(any()) } returns Result.success(stubUser)

        viewModel = ContactsViewModel(
            contactsRepository = contactsRepository,
            dispatchers = dispatchers,
        )
    }

    // ─── Test 1 ──────────────────────────────────────────────────────────────

    /**
     * Email queries must be debounced by 800 ms. At t=799 ms the API must still
     * be idle; at t=800 ms exactly it fires once with the full email string.
     */
    @Test
    fun `TestSearch_Email_TriggersAPI_After800ms`() = runTest(scheduler) {
        viewModel.onSearch("test@example.com")

        // Before the debounce window closes — API must NOT have been called.
        scheduler.advanceTimeBy(799L)
        coVerify(exactly = 0) { contactsRepository.searchServer(any()) }

        // The final 1 ms crosses the 800 ms threshold — exactly one call expected.
        scheduler.advanceTimeBy(1L)
        coVerify(exactly = 1) { contactsRepository.searchServer("test@example.com") }
    }

    // ─── Test 2 ──────────────────────────────────────────────────────────────

    /**
     * Phone number queries (≥10 consecutive digits) debounce at 500 ms — users
     * complete numeric input faster, so a tighter window reduces perceived latency.
     */
    @Test
    fun `TestSearch_Phone_TriggersAPI_After500ms`() = runTest(scheduler) {
        viewModel.onSearch("9876543210") // exactly 10 digits → phone branch

        scheduler.advanceTimeBy(499L)
        coVerify(exactly = 0) { contactsRepository.searchServer(any()) }

        scheduler.advanceTimeBy(1L)
        coVerify(exactly = 1) { contactsRepository.searchServer("9876543210") }
    }

    // ─── Test 3 ──────────────────────────────────────────────────────────────

    /**
     * Plain-text (neither valid email nor ≥10 digit phone) must NEVER trigger a
     * server call regardless of elapsed time — only local Room FTS is permitted.
     */
    @Test
    fun `TestSearch_PlainText_NoAPICall`() = runTest(scheduler) {
        viewModel.onSearch("Alice")

        // Advance well beyond any conceivable debounce window.
        scheduler.advanceTimeBy(5_000L)

        coVerify(exactly = 0) { contactsRepository.searchServer(any()) }
    }

    // ─── Test 4 ──────────────────────────────────────────────────────────────

    /**
     * addContact is a local-only write path. It must call repository.addContact
     * and must NEVER invoke searchServer — adding a contact is not a search operation.
     */
    @Test
    fun `TestAddContact_LocalOnly_NoAPICall`() = runTest(scheduler) {
        val user = PublicUser(
            id = "user-add-001",
            phone = "9999999999",
            email = null,
            displayName = "Alice",
            avatarUrl = null,
            lastSeen = null,
        )

        viewModel.onAddToContacts(
            user = user,
            firstName = "Alice",
            lastName = null,
        )

        scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { contactsRepository.addContact(user, "Alice", null) }
        coVerify(exactly = 0) { contactsRepository.searchServer(any()) }
    }
}
