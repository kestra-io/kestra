/**
 * Minimal POST-based Server-Sent Events reader.
 *
 * The browser `EventSource` API can only issue GET requests, but the AI Copilot
 * `…/chat` and `…/confirm` endpoints are POST-with-JSON-body streams. So we read
 * the response body as a `ReadableStream` and parse the SSE framing by hand.
 *
 * SSE framing (per the spec, as emitted by Micronaut):
 *   - events are separated by a blank line ("\n\n")
 *   - within an event, `event:` names it and one or more `data:` lines carry the
 *     payload (multiple `data:` lines are joined with "\n")
 *   - lines starting with ":" are comments and ignored
 */
import {parseProblem, useClient} from "@kestra-io/kestra-sdk"
import type {AiEventName, AiSseFrame} from "./types"

export interface StreamSseOptions {
    /** Absolute URL to POST to. */
    url: string
    /** Request body — JSON-serialised. */
    body: unknown
    /** Called once per fully-parsed event frame. */
    onFrame: (frame: AiSseFrame) => void
    /** Abort signal to cancel the in-flight stream (e.g. component unmount). */
    signal?: AbortSignal
}

/**
 * Opens the stream, POSTs `body`, and invokes `onFrame` for each SSE event until
 * the server closes the stream. Resolves when the stream ends cleanly; rejects on
 * a network/HTTP error or if aborted. A non-2xx response rejects before any frame
 * is delivered so callers can surface it (e.g. 409 when the thread is not IDLE).
 */
export async function streamSse({url, body, onFrame, signal}: StreamSseOptions): Promise<void> {
    const response = await useClient().stream(url, body, {
        headers: {Accept: "text/event-stream"},
        signal,
    })

    if (!response.ok) {
        // The body is read as text because an SSE stream can also fail mid-flight with no body at all.
        // When it is a problem document, show its detail rather than the raw JSON.
        const body = await response.text().catch(() => "")
        const problem = parseProblem(body, response.status, response.headers?.get?.("content-type"))
        throw new SseHttpError(response.status, problem?.detail ?? problem?.title ?? body, problem?.type)
    }
    if (!response.body) {
        throw new Error("SSE response has no readable body")
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ""

    try {
        for (;;) {
            const {done, value} = await reader.read()
            if (done) break

            buffer += decoder.decode(value, {stream: true})

            // An event is complete once we've seen a blank line. Normalise CRLF first.
            buffer = buffer.replace(/\r\n/g, "\n")
            let boundary = buffer.indexOf("\n\n")
            while (boundary !== -1) {
                const raw = buffer.slice(0, boundary)
                buffer = buffer.slice(boundary + 2)
                const frame = parseFrame(raw)
                if (frame) onFrame(frame)
                boundary = buffer.indexOf("\n\n")
            }
        }
    } finally {
        reader.releaseLock()
    }
}

/**
 * Parses one raw SSE event block into a frame, or null if it carries no usable
 * event/data (comment-only or malformed JSON). Kept exported for unit testing.
 */
export function parseFrame(raw: string): AiSseFrame | null {
    let event: string | undefined
    const dataLines: string[] = []

    for (const line of raw.split("\n")) {
        if (line === "" || line.startsWith(":")) continue
        const colon = line.indexOf(":")
        const field = colon === -1 ? line : line.slice(0, colon)
        // A single leading space after the colon is stripped, per the SSE spec.
        const rest = colon === -1 ? "" : line.slice(colon + 1).replace(/^ /, "")
        if (field === "event") {
            event = rest
        } else if (field === "data") {
            dataLines.push(rest)
        }
    }

    if (!event || dataLines.length === 0) return null

    try {
        return {event: event as AiEventName, data: JSON.parse(dataLines.join("\n"))}
    } catch {
        return null
    }
}

/** Thrown when the SSE endpoint responds with a non-2xx status (e.g. 409/404). */
export class SseHttpError extends Error {
    constructor(
        public readonly status: number,
        public readonly detail: string,
        /** Problem type URI, when the failure carried a problem document. */
        public readonly problemType?: string,
    ) {
        super(`SSE request failed with status ${status}`)
        this.name = "SseHttpError"
    }
}
