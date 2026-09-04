package io.kestra.webserver.services.ai.agent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * A {@link StreamingChatModel} test double that replays queued assistant messages, one per model call,
 * and records the requests it was sent. Shared by every test that drives a Copilot turn — see
 * {@link AbstractAiAgentTest}, which wires one into the mocked AI service.
 * <p>
 * With no response queued the call fails, which is how a test exercises a mid-stream model failure;
 * {@link #hang()} instead never completes the response, so the orchestrator's bounded wait must time out.
 */
public final class ScriptedStreamingChatModel implements StreamingChatModel {
    private final Deque<AiMessage> responses = new ArrayDeque<>();
    private final List<List<ChatMessage>> requestMessages = new CopyOnWriteArrayList<>();
    private volatile boolean hang;

    public void enqueue(final AiMessage message) {
        responses.addLast(message);
    }

    /** Stop completing responses, so the next call never returns. */
    public void hang() {
        this.hang = true;
    }

    public void clear() {
        responses.clear();
        requestMessages.clear();
        this.hang = false;
    }

    /**
     * A snapshot of the messages sent on the most recent chat call. Snapshotted because the orchestrator
     * keeps mutating the same list as its loop appends responses.
     */
    public List<ChatMessage> lastRequestMessages() {
        if (requestMessages.isEmpty()) {
            throw new AssertionError("No chat request was sent to the model");
        }
        return requestMessages.getLast();
    }

    @Override
    public void chat(final ChatRequest request, final StreamingChatResponseHandler handler) {
        requestMessages.add(new ArrayList<>(request.messages()));
        if (hang) {
            return;
        }
        AiMessage ai = responses.pollFirst();
        if (ai == null) {
            handler.onError(new IllegalStateException("No scripted LLM response available"));
            return;
        }
        if (ai.text() != null && !ai.text().isEmpty()) {
            handler.onPartialResponse(ai.text());
        }
        handler.onCompleteResponse(ChatResponse.builder().aiMessage(ai).build());
    }
}
