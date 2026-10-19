import {describe, expect, it} from "vitest"
import {BODY_PREVIEW_LIMIT, parseHttpOutputs} from "./httpSummary"
import {downloadOutputs, encryptedBodyOutputs, noRequestOutputs, redactedHeadersOutputs, successOutputs} from "./httpSummary.fixtures"

describe("parseHttpOutputs", () => {
    it("shouldReturnUndefinedWhenOutputsAreEmpty", () => {
        expect(parseHttpOutputs({}, "request")).toBeUndefined()
        expect(parseHttpOutputs(undefined, "request")).toBeUndefined()
    })

    it("shouldNeverExposeTheBodyWhenItIsEncrypted", () => {
        const summary = parseHttpOutputs({...encryptedBodyOutputs, body: "leaked"}, "request")
        expect(summary?.body).toEqual({kind: "encrypted"})
    })

    it("shouldDeriveHostAndPathFromTheUriWhenRequestSummaryIsMissing", () => {
        const summary = parseHttpOutputs(noRequestOutputs, "request")
        expect(summary).toMatchObject({request: undefined, host: "api.example.com", path: "/v1/items", code: 204})
    })

    it("shouldTruncateALongBody", () => {
        const summary = parseHttpOutputs({...successOutputs, body: "x".repeat(BODY_PREVIEW_LIMIT + 10)}, "request")
        expect(summary?.body).toMatchObject({kind: "text", truncated: true})
    })

    it("shouldFlagRedactedHeaders", () => {
        const rows = parseHttpOutputs(redactedHeadersOutputs, "request")?.headers
        expect(rows?.find((row) => row.name === "set-cookie")?.redacted).toBe(true)
        expect(rows?.find((row) => row.name === "x-request-id")?.redacted).toBe(false)
    })

    it("shouldExposeTheStoredFileAndNoBodyForDownload", () => {
        const summary = parseHttpOutputs({...downloadOutputs, body: "ignored"}, "download")
        expect(summary?.fileUri).toContain("data.csv")
        expect(summary?.body).toBeUndefined()
        expect(summary?.bytes).toBe(1048576)
    })
})
