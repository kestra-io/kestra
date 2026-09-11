import {describe, it, expect, vi, beforeEach} from "vitest"

// streamSse goes through useClient().stream() so the shared interceptors (CSRF header,
// progress) apply — mock it at the SDK boundary; the facade's own behavior is covered
// by the client-facade spec.
const streamMock = vi.fn()
vi.mock("@kestra-io/kestra-sdk", async () => {
    // parseProblem is a pure function with no transport dependency, so use the real one: the point of
    // these tests is that a problem body reaches SseHttpError.detail as prose, not as raw JSON.
    const {parseProblem} = await import("../../../../../packages/hey-api-plugin/src/problem")
    return {useClient: () => ({stream: streamMock}), parseProblem}
})

import {parseFrame, streamSse, SseHttpError} from "../../../../../src/components/ai/copilot/streamSse"

/** Builds a Response-like whose body streams `chunks` as separate reads. */
function sseResponse(chunks: string[], {ok = true, status = 200, body = "", contentType = ""} = {}) {
    const encoder = new TextEncoder()
    let i = 0
    return {
        ok,
        status,
        headers: {get: (name: string) => (name.toLowerCase() === "content-type" ? contentType : null)},
        text: async () => body,
        body: {
            getReader() {
                return {
                    read: async () =>
                        i < chunks.length
                            ? {done: false, value: encoder.encode(chunks[i++])}
                            : {done: true, value: undefined},
                    releaseLock() {},
                }
            },
        },
    } as unknown as Response
}

describe("parseFrame", () => {
    it("parses a token event", () => {
        const frame = parseFrame("event: token\ndata: {\"text\":\"Hello\"}")
        expect(frame).toEqual({event: "token", data: {text: "Hello"}})
    })

    it("parses a proposed_action event with a null tool (Plan card)", () => {
        const raw = "event: proposed_action\ndata: {\"confirmationId\":\"a1\",\"tool\":null,\"summary\":\"Plan\"}"
        expect(parseFrame(raw)).toEqual({
            event: "proposed_action",
            data: {confirmationId: "a1", tool: null, summary: "Plan"},
        })
    })

    it("parses a done event carrying the resting status", () => {
        expect(parseFrame("event: done\ndata: {\"status\":\"AWAITING_CONFIRMATION\"}")).toEqual({
            event: "done",
            data: {status: "AWAITING_CONFIRMATION"},
        })
    })

    it("joins multiple data: lines with a newline before JSON parsing", () => {
        const frame = parseFrame("event: token\ndata: {\"text\":\ndata: \"multi\"}")
        expect(frame?.data).toEqual({text: "multi"})
    })

    it("strips a single leading space after the colon", () => {
        // "data: X" and "data:X" must parse identically per the SSE spec.
        expect(parseFrame("event:token\ndata:{\"text\":\"x\"}")).toEqual({event: "token", data: {text: "x"}})
    })

    it("ignores comment lines", () => {
        expect(parseFrame(": keep-alive\nevent: token\ndata: {\"text\":\"x\"}")?.event).toBe("token")
    })

    it("returns null when the event name is missing", () => {
        expect(parseFrame("data: {\"text\":\"x\"}")).toBeNull()
    })

    it("returns null when there is no data line", () => {
        expect(parseFrame("event: done")).toBeNull()
    })

    it("returns null on malformed JSON rather than throwing", () => {
        expect(parseFrame("event: token\ndata: {not json}")).toBeNull()
    })
})

describe("streamSse", () => {
    beforeEach(() => streamMock.mockReset())

    it("streams through useClient().stream with the SSE Accept header and delivers frames in order", async () => {
        streamMock.mockResolvedValue(
            sseResponse(["event: token\ndata: {\"text\":\"Hi\"}\n\n", "event: done\ndata: {\"status\":\"IDLE\"}\n\n"]),
        )

        const frames: {event: string; data: unknown}[] = []
        const abort = new AbortController()
        await streamSse({url: "/x/chat", body: {prompt: "hi"}, signal: abort.signal, onFrame: (f) => frames.push(f)})

        const [url, body, config] = streamMock.mock.calls[0]
        expect(url).toBe("/x/chat")
        expect(body).toEqual({prompt: "hi"})
        expect(config.headers.Accept).toBe("text/event-stream")
        expect(config.signal).toBe(abort.signal)
        expect(frames).toEqual([
            {event: "token", data: {text: "Hi"}},
            {event: "done", data: {status: "IDLE"}},
        ])
    })

    it("reassembles an event split across chunk boundaries", async () => {
        streamMock.mockResolvedValue(sseResponse(["event: to", "ken\ndata: {\"text\":\"split\"}", "\n\n"]))
        const frames: {event: string; data: unknown}[] = []
        await streamSse({url: "/x", body: {}, onFrame: (f) => frames.push(f)})
        expect(frames).toEqual([{event: "token", data: {text: "split"}}])
    })

    it("throws SseHttpError with the status on a non-2xx response", async () => {
        streamMock.mockResolvedValue(sseResponse([], {ok: false, status: 409}))
        await expect(streamSse({url: "/x", body: {}, onFrame: () => {}}))
            .rejects.toBeInstanceOf(SseHttpError)
    })

    it("surfaces a problem document's detail rather than the raw JSON", async () => {
        // Without this, the Copilot would render the serialized problem body verbatim to the user.
        const problem = {
            type: "https://kestra.io/docs/api-reference/problems/conflict",
            title: "Conflict",
            status: 409,
            detail: "A turn is already in flight for this thread.",
        }
        streamMock.mockResolvedValue(sseResponse([], {
            ok: false,
            status: 409,
            body: JSON.stringify(problem),
            contentType: "application/problem+json",
        }))

        await expect(streamSse({url: "/x", body: {}, onFrame: () => {}})).rejects.toMatchObject({
            status: 409,
            detail: "A turn is already in flight for this thread.",
            problemType: problem.type,
        })
    })

    it("falls back to the raw body when the failure is not a problem document", async () => {
        streamMock.mockResolvedValue(sseResponse([], {ok: false, status: 502, body: "upstream is down"}))
        await expect(streamSse({url: "/x", body: {}, onFrame: () => {}}))
            .rejects.toMatchObject({status: 502, detail: "upstream is down"})
    })
})
