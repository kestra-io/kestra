import {beforeEach, describe, expect, it, vi} from "vitest"
import {capturePosthogEvent, disablePosthog} from "../posthog"
import {sendPosthogEvent} from "./posthogEvents"

vi.mock("../posthog", () => ({
    capturePosthogEvent: vi.fn(),
    disablePosthog: vi.fn(),
}))

describe("sendPosthogEvent", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it("disables PostHog and sends nothing when anonymous usage is disabled", () => {
        sendPosthogEvent({isUiAnonymousUsageEnabled: false}, {type: "flow_created"})

        expect(disablePosthog).toHaveBeenCalledOnce()
        expect(capturePosthogEvent).not.toHaveBeenCalled()
    })

    it("sends an event when the config is undefined", () => {
        sendPosthogEvent(undefined, {type: "flow_created"})

        expect(capturePosthogEvent).toHaveBeenCalledOnce()
        expect(disablePosthog).not.toHaveBeenCalled()
    })

    it("strips internal fields from the payload without mutating the source event", () => {
        const data = {
            type: "flow_created",
            date: "2026-10-20",
            counter: 2,
            flowId: "my-flow",
            details: {source: "editor"},
        }
        const originalData = structuredClone(data)

        sendPosthogEvent({isUiAnonymousUsageEnabled: true}, data)

        expect(capturePosthogEvent).toHaveBeenCalledWith(
            {isUiAnonymousUsageEnabled: true},
            "app.flow.created",
            {
                flowId: "my-flow",
                details: {source: "editor"},
            },
        )
        expect(data).toEqual(originalData)
    })

    it("sends PAGE events as pageviews and resolves other event names", () => {
        sendPosthogEvent(undefined, {type: "PAGE"})
        sendPosthogEvent(undefined, {type: "flow_created"})

        expect(capturePosthogEvent).toHaveBeenNthCalledWith(
            1,
            undefined,
            "$pageview",
            expect.any(Object),
        )
        expect(capturePosthogEvent).toHaveBeenNthCalledWith(
            2,
            undefined,
            "app.flow.created",
            {},
        )
    })

    it("uses fullPath for the pageview URL when it is present", () => {
        const originalTitle = document.title
        document.title = "Kestra flow"
        const pageEvent = {
            type: "PAGE",
            page: {
                origin: "https://kestra.io",
                path: "/flows",
                fullPath: "/flows/my-flow?tab=tasks",
            },
        }
        const originalPageEvent = structuredClone(pageEvent)

        try {
            sendPosthogEvent(undefined, pageEvent)
        } finally {
            document.title = originalTitle
        }

        expect(capturePosthogEvent).toHaveBeenCalledWith(
            undefined,
            "$pageview",
            expect.objectContaining({
                $current_url: "https://kestra.io/flows/my-flow?tab=tasks",
                $host: "kestra.io",
                $pathname: "/flows",
                $title: "Kestra flow",
            }),
        )
        expect(pageEvent).toEqual(originalPageEvent)
    })

    it("falls back to path when the pageview has no fullPath", () => {
        sendPosthogEvent(undefined, {
            type: "PAGE",
            page: {
                origin: "https://kestra.io",
                path: "/flows",
            },
        })

        expect(capturePosthogEvent).toHaveBeenCalledWith(
            undefined,
            "$pageview",
            expect.objectContaining({
                $current_url: "https://kestra.io/flows",
            }),
        )
    })

    it("uses the current location host when the page origin is not a valid URL", () => {
        sendPosthogEvent(undefined, {
            type: "PAGE",
            page: {
                origin: "not-a-url",
                path: "/flows",
                fullPath: "/flows/my-flow",
            },
        })

        expect(capturePosthogEvent).toHaveBeenCalledWith(
            undefined,
            "$pageview",
            expect.objectContaining({
                $current_url: "not-a-url/flows/my-flow",
                $host: window.location.host,
            }),
        )
    })
})