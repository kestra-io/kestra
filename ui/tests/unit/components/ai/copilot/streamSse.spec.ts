import {describe, it, expect, vi, afterEach} from "vitest"
import {parseFrame, streamSse, SseHttpError} from "../../../../../src/components/ai/copilot/streamSse"

/** Builds a Response-like whose body streams `chunks` as separate reads. */
function sseResponse(chunks: string[], {ok = true, status = 200} = {}) {
    const encoder = new TextEncoder()
    let i = 0
    return {
        ok,
        status,
        text: async () => "",
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
    afterEach(() => vi.unstubAllGlobals())

    it("POSTs JSON with the SSE Accept header and streams frames in order", async () => {
        const fetchMock = vi.fn().mockResolvedValue(
            sseResponse(["event: token\ndata: {\"text\":\"Hi\"}\n\n", "event: done\ndata: {\"status\":\"IDLE\"}\n\n"]),
        )
        vi.stubGlobal("fetch", fetchMock)

        const frames: {event: string; data: unknown}[] = []
        await streamSse({url: "/x/chat", body: {prompt: "hi"}, onFrame: (f) => frames.push(f)})

        const [, init] = fetchMock.mock.calls[0]
        expect(init.method).toBe("POST")
        expect(init.headers.Accept).toBe("text/event-stream")
        expect(JSON.parse(init.body)).toEqual({prompt: "hi"})
        expect(frames).toEqual([
            {event: "token", data: {text: "Hi"}},
            {event: "done", data: {status: "IDLE"}},
        ])
    })

    it("reassembles an event split across chunk boundaries", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(
            sseResponse(["event: to", "ken\ndata: {\"text\":\"split\"}", "\n\n"]),
        ))
        const frames: {event: string; data: unknown}[] = []
        await streamSse({url: "/x", body: {}, onFrame: (f) => frames.push(f)})
        expect(frames).toEqual([{event: "token", data: {text: "split"}}])
    })

    it("throws SseHttpError with the status on a non-2xx response", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(sseResponse([], {ok: false, status: 409})))
        await expect(streamSse({url: "/x", body: {}, onFrame: () => {}}))
            .rejects.toBeInstanceOf(SseHttpError)
    })
})
