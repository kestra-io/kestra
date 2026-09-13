import {describe, it, expect, vi, afterAll, beforeEach} from "vitest"
import {mount} from "@vue/test-utils"
import type {AiSseFrame} from "../../../../../src/components/ai/copilot/types"
import {mountGlobal} from "./_helpers"

// Mock the axios client (thread create) and the SSE reader so we can drive frames
// deterministically without a backend — same harness as useAiChat.spec.
const post = vi.fn()
const get = vi.fn()
vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({post, get})}))

let nextFrames: AiSseFrame[] = []
vi.mock("../../../../../src/components/ai/copilot/streamSse", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../../../../../src/components/ai/copilot/streamSse")>()
    return {
        ...actual,
        streamSse: vi.fn(async ({onFrame}: {onFrame: (f: AiSseFrame) => void}) => {
            for (const f of nextFrames) onFrame(f)
        }),
    }
})
vi.mock("override/utils/route", () => ({apiUrl: () => "http://localhost/api/v1/main"}))

// The dashboard draft card reads the custom-dashboards capability flag from the misc store.
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {}})}))

import {useAiChat} from "../../../../../src/components/ai/copilot/useAiChat"
import CopilotMessage from "../../../../../src/components/ai/copilot/CopilotMessage.vue"

// The AI Copilot v2 tool catalog, mirrored from the backend agent tools. This is the single
// place the FE test suite enumerates every tool; add new tools here when the backend adds them.
const PLATFORM_TOOLS = [
    {tool: "read-execution", family: "READ"},
    {tool: "list-executions", family: "READ"},
    {tool: "read-execution-logs", family: "READ"},
    {tool: "read-flow", family: "READ"},
    {tool: "list-flows", family: "READ"},
    {tool: "search-plugins", family: "READ"},
    {tool: "get-plugin-schema", family: "READ"},
    {tool: "validate-flow", family: "READ"},
    {tool: "restart-execution", family: "ACT"},
] as const

const AUTHORING_TOOLS = [
    {tool: "author-flow", kind: "FLOW", title: "Proposed flow"},
    {tool: "author-dashboard", kind: "DASHBOARD", title: "Proposed dashboard"},
    {tool: "author-app", kind: "APP", title: "Proposed app"},
] as const

/** A stream that exercises every tool: each platform tool calls + returns; each authoring tool
 *  calls, emits a draft, then returns. */
function catalogFrames(): AiSseFrame[] {
    const frames: AiSseFrame[] = []
    for (const t of PLATFORM_TOOLS) {
        frames.push({event: "tool_call", data: {tool: t.tool, kind: "PLATFORM", family: t.family, arguments: {}}})
        frames.push({event: "tool_result", data: {tool: t.tool, outcome: "ok"}})
    }
    for (const t of AUTHORING_TOOLS) {
        frames.push({event: "tool_call", data: {tool: t.tool, kind: "AUTHORING", arguments: {}}})
        frames.push({event: "artefact_draft", data: {draftId: t.tool, kind: t.kind, yaml: `id: ${t.tool}`, valid: true, constraints: null}})
        frames.push({event: "tool_result", data: {tool: t.tool, outcome: "ok"}})
    }
    frames.push({event: "done", data: {status: "IDLE"}})
    return frames
}

const mountMessage = (message: any) => mount(CopilotMessage, {props: {message}, global: mountGlobal})

describe("AI Copilot v2 — full tool catalog", () => {
    beforeEach(() => {
        post.mockReset()
        get.mockReset()
        nextFrames = []
        post.mockResolvedValue({data: {uid: "t1", mode: "EDIT", status: "IDLE", createdAt: "", updatedAt: ""}})
    })

    afterAll(() => {
        localStorage.clear()
    })

    it("reduces a stream that exercises every tool into the expected transcript", async () => {
        const chat = useAiChat()
        nextFrames = catalogFrames()
        await chat.sendChat({prompt: "exercise every tool", mode: "EDIT"})

        // Every tool produced a tool_call carrying its name.
        const calledTools = chat.messages.value.filter((m) => m.type === "TOOL_CALL").map((m) => m.toolCall?.tool)
        for (const {tool} of [...PLATFORM_TOOLS, ...AUTHORING_TOOLS]) {
            expect(calledTools).toContain(tool)
        }

        // Every tool produced an "ok" tool_result.
        const okResults = chat.messages.value.filter((m) => m.type === "TOOL_RESULT" && m.toolResult?.outcome === "ok")
        expect(okResults).toHaveLength(PLATFORM_TOOLS.length + AUTHORING_TOOLS.length)

        // Authoring tools each produced an artefact draft of the right kind.
        const draftKinds = chat.messages.value.filter((m) => m.type === "ARTEFACT_DRAFT").map((m) => m.draft?.kind)
        expect(draftKinds).toEqual(AUTHORING_TOOLS.map((t) => t.kind))

        expect(chat.status.value).toBe("IDLE")
    })

    it("preserves the tool_call → tool_result family/kind on the reduced messages", async () => {
        const chat = useAiChat()
        nextFrames = catalogFrames()
        await chat.sendChat({prompt: "exercise every tool", mode: "EDIT"})

        for (const {tool, family} of PLATFORM_TOOLS) {
            const call = chat.messages.value.find((m) => m.type === "TOOL_CALL" && m.toolCall?.tool === tool)
            expect(call?.toolCall?.family).toBe(family)
            expect(call?.toolCall?.kind).toBe("PLATFORM")
        }
        for (const {tool} of AUTHORING_TOOLS) {
            const call = chat.messages.value.find((m) => m.type === "TOOL_CALL" && m.toolCall?.tool === tool)
            expect(call?.toolCall?.kind).toBe("AUTHORING")
        }
    })

    it("renders a tool_call row showing the tool name for every platform tool", () => {
        for (const {tool, family} of PLATFORM_TOOLS) {
            const w = mountMessage({id: tool, role: "TOOL", type: "TOOL_CALL", toolCall: {tool, family, arguments: {}}})
            expect(w.find(".copilot-tool-label").text()).toContain(tool)
        }
    })

    it("renders an ok and a rejected tool_result for every platform tool", () => {
        for (const {tool} of PLATFORM_TOOLS) {
            const ok = mountMessage({id: `${tool}-ok`, role: "TOOL", type: "TOOL_RESULT", toolResult: {tool, outcome: "ok"}})
            expect(ok.text()).toContain(tool)
            expect(ok.text()).toContain("completed")

            const rejected = mountMessage({id: `${tool}-x`, role: "TOOL", type: "TOOL_RESULT", toolResult: {tool, outcome: "rejected"}})
            expect(rejected.text()).toContain("rejected")
        }
    })

    it("renders an artefact-draft card for every authoring tool kind", () => {
        for (const {tool, kind, title} of AUTHORING_TOOLS) {
            const w = mountMessage({
                id: tool, role: "ASSISTANT", type: "ARTEFACT_DRAFT",
                draft: {draftId: tool, kind, yaml: `id: ${tool}`, valid: true, constraints: null},
            })
            expect(w.find(".copilot-draft").exists()).toBe(true)
            expect(w.text()).toContain(title)
            expect(w.find("[data-test=\"copilot-draft-yaml\"]").text()).toContain(`id: ${tool}`)
        }
    })
})
