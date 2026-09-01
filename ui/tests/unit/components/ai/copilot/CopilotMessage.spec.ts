import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import CopilotMessage from "../../../../../src/components/ai/copilot/CopilotMessage.vue"
import {mountGlobal} from "./_helpers"
import type {ChatMessage} from "../../../../../src/components/ai/copilot/useAiChat"

const mountMessage = (message: ChatMessage) =>
    mount(CopilotMessage, {props: {message}, global: mountGlobal})

describe("CopilotMessage", () => {
    it("renders a user prompt in a right-aligned bubble", () => {
        const w = mountMessage({id: "1", role: "USER", type: "TEXT", content: "hello there"})
        expect(w.find(".copilot-msg-user").exists()).toBe(true)
        expect(w.text()).toContain("hello there")
    })

    it("renders a ``` fenced segment of the user prompt as a literal code block", () => {
        const w = mountMessage({
            id: "1a", role: "USER", type: "TEXT",
            content: "Fix this flow:\n```yaml\nid: demo\nnamespace: company.team\n```\nIt fails on start.",
        })
        const code = w.find("[data-test=\"copilot-user-code\"]")
        expect(code.exists()).toBe(true)
        expect(code.element.textContent).toBe("id: demo\nnamespace: company.team")
        const texts = w.findAll(".copilot-bubble-text")
        expect(texts[0].text()).toBe("Fix this flow:")
        expect(texts[1].text()).toBe("It fails on start.")
    })

    it("treats an unclosed ``` fence as a code block running to the end of the prompt", () => {
        const w = mountMessage({
            id: "1b", role: "USER", type: "TEXT",
            content: "Why is this wrong?\n```yaml\nid: demo\ntasks: []",
        })
        expect(w.find("[data-test=\"copilot-user-code\"]").element.textContent).toBe("id: demo\ntasks: []")
    })

    it("renders a plain user prompt without any code block", () => {
        const w = mountMessage({id: "1c", role: "USER", type: "TEXT", content: "id: demo\nnamespace: company.team"})
        expect(w.find("[data-test=\"copilot-user-code\"]").exists()).toBe(false)
        // Line breaks survive into the DOM; `white-space: pre-wrap` renders them.
        expect(w.find(".copilot-bubble-text").element.textContent).toBe("id: demo\nnamespace: company.team")
    })

    it("renders assistant text as a styled bubble through the markdown renderer", () => {
        const w = mountMessage({id: "2", role: "ASSISTANT", type: "TEXT", content: "**bold** answer"})
        expect(w.find(".copilot-bubble-assistant").exists()).toBe(true)
        const md = w.find(".ks-markdown")
        expect(md.exists()).toBe(true)
        expect(md.text()).toContain("**bold** answer")
    })

    it("renders a context-change notice with a lowercase type word and the id as a code token", () => {
        const w = mountMessage({
            id: "ctx", role: "SYSTEM", type: "CONTEXT",
            context: {action: "removed", noun: "ai.copilot.contextNoun.flow", id: "good-morning"},
        })
        const notice = w.find("[data-test=\"copilot-context-notice\"]")
        expect(notice.exists()).toBe(true)
        expect(notice.text()).toBe("Removed flow good-morning from context.")
        // The id renders as a monospace code token, not plain text.
        expect(notice.find("code.copilot-context-id").text()).toBe("good-morning")
    })

    it("renders a collapsible tool_call with its name in the title and args as JSON", () => {
        const w = mountMessage({
            id: "3", role: "TOOL", type: "TOOL_CALL",
            toolCall: {tool: "read-execution", family: "READ", arguments: {id: "exec-1"}},
        })
        expect(w.find(".copilot-tool-label").text()).toContain("read-execution")
        expect(w.find(".copilot-tool-args").text()).toContain("\"id\": \"exec-1\"")
    })

    it("spins the tool_call header only while the step is running", () => {
        const message: ChatMessage = {
            id: "3r", role: "TOOL", type: "TOOL_CALL",
            toolCall: {tool: "read-execution", family: "READ", arguments: {}},
        }
        const running = mount(CopilotMessage, {props: {message, isRunning: true}, global: mountGlobal})
        expect(running.find(".copilot-tool-spinner").exists()).toBe(true)
        const idle = mount(CopilotMessage, {props: {message, isRunning: false}, global: mountGlobal})
        expect(idle.find(".copilot-tool-spinner").exists()).toBe(false)
    })

    it("renders an ok tool_result with a success message", () => {
        const w = mountMessage({
            id: "4", role: "TOOL", type: "TOOL_RESULT",
            toolResult: {tool: "restart-execution", outcome: "ok"},
        })
        expect(w.find(".copilot-tool-result").exists()).toBe(true)
        expect(w.text()).toContain("completed")
    })

    it("renders a rejected tool_result with the rejected message", () => {
        const w = mountMessage({
            id: "5", role: "TOOL", type: "TOOL_RESULT",
            toolResult: {tool: "restart-execution", outcome: "rejected"},
        })
        expect(w.text()).toContain("rejected")
    })

    it("renders an errored tool_result with the failed message", () => {
        const w = mountMessage({
            id: "5b", role: "TOOL", type: "TOOL_RESULT",
            toolResult: {tool: "read-execution", outcome: "error"},
        })
        expect(w.text()).toContain("failed")
        // An error is not a success — it must not render as ok.
        expect(w.text()).not.toContain("completed")
    })

    it("shows the result payload detail on a reloaded ok tool_result", () => {
        const w = mountMessage({
            id: "5c", role: "TOOL", type: "TOOL_RESULT",
            toolResult: {outcome: "ok", result: {executionId: "exec-1", state: "SUCCESS"}},
            toolCall: {tool: "read-execution", kind: "PLATFORM", family: "READ", arguments: {}},
        })
        // Tool name resolves from the paired toolCall when the persisted result map has no `tool`.
        expect(w.text()).toContain("read-execution")
        expect(w.find("[data-test=\"copilot-tool-result-detail\"]").exists()).toBe(true)
        expect(w.text()).toContain("exec-1")
        expect(w.text()).toContain("SUCCESS")
    })

    it("shows the error message as detail on an errored tool_result", () => {
        const w = mountMessage({
            id: "5d", role: "TOOL", type: "TOOL_RESULT",
            toolResult: {tool: "read-flow", outcome: "error", error: "Flow not found: x"},
        })
        expect(w.find("[data-test=\"copilot-tool-result-detail\"]").exists()).toBe(true)
        expect(w.text()).toContain("Flow not found: x")
    })

    it("has no detail collapsible on a live tool_result carrying only the outcome", () => {
        const w = mountMessage({
            id: "5e", role: "TOOL", type: "TOOL_RESULT",
            toolResult: {tool: "list-flows", outcome: "ok"},
        })
        expect(w.find("[data-test=\"copilot-tool-result-detail\"]").exists()).toBe(false)
    })

    it("renders an artefact_draft message as a draft card", () => {
        const w = mountMessage({
            id: "6", role: "ASSISTANT", type: "ARTEFACT_DRAFT",
            draft: {draftId: "d1", kind: "FLOW", yaml: "id: demo\nnamespace: x", valid: true, constraints: null},
        })
        expect(w.find(".copilot-draft").exists()).toBe(true)
        expect(w.find("[data-test=\"copilot-draft-yaml\"]").text()).toContain("id: demo")
    })

    it("renders a CANCELLED message as a subtle system marker", () => {
        const w = mountMessage({id: "7", role: "SYSTEM", type: "CANCELLED"})
        const marker = w.find("[data-test=\"copilot-cancelled\"]")
        expect(marker.exists()).toBe(true)
        expect(marker.text()).toBe("Turn cancelled")
    })

    it("renders a resolved PROPOSED_ACTION inline as a read-only card (no confirm/reject buttons)", () => {
        const w = mountMessage({
            id: "8", role: "ASSISTANT", type: "PROPOSED_ACTION",
            proposedAction: {confirmationId: "c1", tool: "restart-execution", family: "ACT", summary: "Restart execution exec-1", arguments: {executionId: "exec-1"}},
        })
        const card = w.find("[data-test=\"copilot-proposed-history\"]")
        expect(card.exists()).toBe(true)
        expect(card.text()).toContain("Restart execution exec-1")
        expect(card.text()).toContain("exec-1")
        // Read-only: the interactive actions belong to the pending card in CopilotChat, not here.
        expect(w.find("[data-test=\"copilot-approve\"]").exists()).toBe(false)
        expect(w.find("[data-test=\"copilot-reject\"]").exists()).toBe(false)
    })

    it("suppresses the PROPOSED_ACTION inline when it is the pending one", () => {
        const w = mount(CopilotMessage, {
            props: {
                message: {id: "9", role: "ASSISTANT", type: "PROPOSED_ACTION", proposedAction: {confirmationId: "c2", tool: "restart-execution", summary: "x", arguments: {}}},
                isPending: true,
            },
            global: mountGlobal,
        })
        expect(w.find("[data-test=\"copilot-proposed-history\"]").exists()).toBe(false)
    })

    it("rebuilds a reloaded PROPOSED_ACTION from content + the paired toolCall", () => {
        const w = mountMessage({
            id: "10", role: "ASSISTANT", type: "PROPOSED_ACTION",
            content: "Update flow demo",
            toolCall: {tool: "update-flow", kind: "PLATFORM", family: "MUTATE", arguments: {namespace: "x", flowId: "demo"}},
        })
        const card = w.find("[data-test=\"copilot-proposed-history\"]")
        expect(card.exists()).toBe(true)
        expect(card.text()).toContain("Update flow demo")
        expect(card.text()).toContain("demo")
    })
})
