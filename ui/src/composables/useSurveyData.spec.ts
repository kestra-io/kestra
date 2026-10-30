import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"

import {useSurveySkip} from "./useSurveyData"

describe("useSurveySkip", () => {
    beforeEach(() => {
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
        vi.restoreAllMocks()
        vi.useRealTimers()
    })

    it("reports a fresh survey as not skipped", () => {
        const {isSurveySkipped} = useSurveySkip()

        expect(isSurveySkipped.value).toBe(false)
    })

    it("records a skip and reports the survey as skipped", () => {
        const {isSurveySkipped, storeSurveySkipData} = useSurveySkip()

        storeSurveySkipData({})

        expect(isSurveySkipped.value).toBe(true)
        expect(JSON.parse(localStorage.getItem("basicAuthSurveySkipped") ?? "null")).toMatchObject({
            skipped: true,
            survey_action: "skipped",
        })
    })

    it("keeps the skipped state across calls", () => {
        const firstCall = useSurveySkip()
        firstCall.storeSurveySkipData({})

        const secondCall = useSurveySkip()

        expect(secondCall.isSurveySkipped.value).toBe(true)
    })

    it("treats malformed stored data as not skipped", () => {
        localStorage.setItem("basicAuthSurveySkipped", "not-json")
        vi.spyOn(console, "warn").mockImplementation(() => {})

        expect(() => useSurveySkip()).not.toThrow()
        expect(useSurveySkip().isSurveySkipped.value).toBe(false)
    })

    it("keeps the same state when skipping twice", () => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date("2025-01-01T00:00:00.000Z"))
        const {isSurveySkipped, storeSurveySkipData} = useSurveySkip()

        storeSurveySkipData({})
        const storedAfterFirstSkip = localStorage.getItem("basicAuthSurveySkipped")
        storeSurveySkipData({})

        expect(isSurveySkipped.value).toBe(true)
        expect(localStorage.getItem("basicAuthSurveySkipped")).toBe(storedAfterFirstSkip)
    })
})
