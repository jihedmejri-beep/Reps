package com.reps.app.feature.nutrition.assistant

import com.reps.app.R
import com.reps.app.domain.model.AssistantConversation
import com.reps.app.domain.model.AssistantError
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.MealDraft
import com.reps.app.domain.model.User
import com.reps.app.domain.repository.AssistantConversationRepository
import com.reps.app.domain.repository.AssistantExchange
import com.reps.app.domain.repository.AssistantResult
import com.reps.app.domain.repository.CoachedAnalysis
import com.reps.app.domain.repository.NutritionAssistantRepository
import com.reps.app.domain.repository.UnderstandingResponse
import com.reps.app.domain.repository.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chat submit lifecycle. The regression these guard: after a successful
 * RAG reply the phase was never released, so Send stayed disabled for good.
 *
 * The ViewModel runs on an unconfined scope, so completing a scripted reply
 * resumes the turn synchronously and every assertion sees the settled state.
 */
class NutritionAssistantViewModelTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    /** Each call to [understand] waits on its own deferred until the test answers it. */
    private class ScriptedAssistant : NutritionAssistantRepository {
        val calls = mutableListOf<String>()
        val pending = mutableListOf<CompletableDeferred<AssistantResult<UnderstandingResponse>>>()
        var immediate: AssistantResult<UnderstandingResponse>? = null

        override suspend fun understand(
            history: List<AssistantExchange>,
            message: String,
            conversationId: String?,
        ): AssistantResult<UnderstandingResponse> {
            calls += message
            immediate?.let { return it }
            val reply = CompletableDeferred<AssistantResult<UnderstandingResponse>>()
            pending += reply
            return reply.await()
        }

        override suspend fun analyseAndCoach(draft: MealDraft, goal: Goal): AssistantResult<CoachedAnalysis> =
            AssistantResult.Failure(AssistantError.ModelUnavailable)

        override suspend fun ask(
            history: List<AssistantExchange>,
            question: String,
            goal: Goal,
            conversationId: String?,
        ): AssistantResult<String> = AssistantResult.Failure(AssistantError.Unknown)

        fun answer(index: Int, text: String) =
            pending[index].complete(AssistantResult.Success(UnderstandingResponse(message = text, conversationId = "conv-1")))

        fun fail(index: Int, error: AssistantError) = pending[index].complete(AssistantResult.Failure(error))
    }

    private class InMemoryHistory : AssistantConversationRepository {
        val conversations = MutableStateFlow<List<AssistantConversation>>(emptyList())
        override fun observeConversations(): Flow<List<AssistantConversation>> = conversations
        override suspend fun upsert(conversation: AssistantConversation) {
            conversations.value = conversations.value.filterNot { it.id == conversation.id } + conversation
        }
        override suspend fun delete(id: String) {
            conversations.value = conversations.value.filterNot { it.id == id }
        }
    }

    private object NoUser : UserRepository {
        override fun observeUser(): Flow<User?> = flowOf(null)
        override suspend fun updateUser(user: User) = Unit
    }

    private val assistant = ScriptedAssistant()
    private val history = InMemoryHistory()

    private fun viewModel(): NutritionAssistantViewModel =
        NutritionAssistantViewModel(assistant, history, NoUser, scope).also { vm ->
            scope.launch { vm.uiState.collect {} }
        }

    private val NutritionAssistantViewModel.state get() = uiState.value

    @Test
    fun firstMessage_success_releasesSend() {
        val vm = viewModel()
        assertTrue(vm.send("How much protein do I need?"))
        assertTrue(vm.state.responding)

        assistant.answer(0, "About 1.6 g per kg.")

        assertEquals(ReplyPhase.IDLE, vm.state.phase)
        assertFalse(vm.state.responding)
        assertEquals(listOf("How much protein do I need?", "About 1.6 g per kg."), vm.state.messages.map { it.literal })
    }

    @Test
    fun secondMessage_afterFirstReply_isAcceptedAndAnswered() {
        val vm = viewModel()
        vm.send("Q1")
        assistant.answer(0, "A1")

        assertTrue(vm.send("Q2"))
        assertTrue(vm.state.responding)
        assistant.answer(1, "A2")

        assertEquals(listOf("Q1", "Q2"), assistant.calls)
        assertFalse(vm.state.responding)
        assertEquals(listOf("Q1", "A1", "Q2", "A2"), vm.state.messages.map { it.literal })
    }

    @Test
    fun manyTurns_neverLeaveSendDisabled() {
        val vm = viewModel()
        repeat(10) { i ->
            assertTrue("turn $i rejected", vm.send("Q$i"))
            assistant.answer(i, "A$i")
            assertFalse("stuck after turn $i", vm.state.responding)
        }
    }

    @Test
    fun failedRequest_releasesSend_showsError_andRestoresDraft() {
        val vm = viewModel()
        vm.send("What should I eat?")
        assistant.fail(0, AssistantError.Network)

        assertEquals(ReplyPhase.FAILED, vm.state.phase)
        assertFalse(vm.state.responding)
        assertEquals(R.string.assistant_error_network, vm.state.messages.last().textRes)
        assertEquals("What should I eat?", vm.state.restoredDraft?.text)
        assertTrue(history.conversations.value.isEmpty())
    }

    @Test
    fun timeout_releasesSend_withTimeoutMessage() {
        val vm = viewModel()
        vm.send("Q")
        assistant.fail(0, AssistantError.Timeout)

        assertFalse(vm.state.responding)
        assertEquals(R.string.assistant_error_timeout, vm.state.messages.last().textRes)
    }

    @Test
    fun notConfigured_failsImmediately_withoutRestoringDraft() {
        assistant.immediate = AssistantResult.Failure(AssistantError.NotConfigured)
        val vm = viewModel()
        vm.send("Q")

        assertEquals(ReplyPhase.FAILED, vm.state.phase)
        assertEquals(R.string.assistant_error_not_configured, vm.state.messages.last().textRes)
        assertNull(vm.state.restoredDraft)
    }

    @Test
    fun immediateSuccess_withoutSuspending_stillReleasesSend() {
        assistant.immediate = AssistantResult.Success(UnderstandingResponse(message = "A", conversationId = "c"))
        val vm = viewModel()
        vm.send("Q")
        assertFalse(vm.state.responding)
    }

    @Test
    fun recoveryAfterFailure_nextMessageSucceeds() {
        val vm = viewModel()
        vm.send("Q1")
        assistant.fail(0, AssistantError.ModelUnavailable)

        assertTrue(vm.send("Q1"))
        assistant.answer(1, "A1")

        assertEquals(ReplyPhase.IDLE, vm.state.phase)
        assertEquals("A1", vm.state.messages.last().literal)
        assertEquals(1, history.conversations.value.size)
    }

    @Test
    fun emptyOrWhitespaceMessage_isRejected() {
        val vm = viewModel()
        assertFalse(vm.send(""))
        assertFalse(vm.send("   \n\t"))
        assertTrue(assistant.calls.isEmpty())
        assertTrue(vm.state.messages.isEmpty())
    }

    @Test
    fun rapidRepeatedTaps_submitExactlyOnce() {
        val vm = viewModel()
        val accepted = (1..5).map { vm.send("Same question") }

        assertEquals(listOf(true, false, false, false, false), accepted)
        assertEquals(1, assistant.calls.size)
        assertEquals(1, vm.state.messages.size)
    }

    @Test
    fun cancellationViaNewChat_releasesSend_andDropsLateReply() {
        val vm = viewModel()
        vm.send("Q1")
        vm.newChat()

        assertFalse(vm.state.responding)
        assertTrue(vm.state.messages.isEmpty())

        assertTrue(vm.send("Q2"))
        assistant.answer(0, "late A1")
        assertTrue("abandoned turn changed state", vm.state.responding)
        assistant.answer(1, "A2")
        assertEquals(listOf("Q2", "A2"), vm.state.messages.map { it.literal })
    }

    @Test
    fun repliesStayPairedWithTheirQuestions_inHistory() {
        val vm = viewModel()
        vm.send("Q1")
        assistant.answer(0, "A1")
        vm.send("Q2")
        assistant.answer(1, "A2")

        val stored = history.conversations.value.single().messages
        assertEquals(listOf(true to "Q1", false to "A1", true to "Q2", false to "A2"), stored.map { it.fromUser to it.text })
        assertEquals(stored.map { it.id }.sorted(), stored.map { it.id })
    }
}
