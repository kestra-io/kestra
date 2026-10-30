import {describe, it, expect, vi, afterEach} from "vitest"

import {useSurveySkip} from "./useSurveyData"

const SURVEY_SKIP_KEY = "basicAuthSurveySkipped"

describe("useSurveySkip", () => {
    afterEach(() => {
        localStorage.removeItem(SURVEY_SKIP_KEY)
    })

    it("reports the survey as not skipped on a fresh state", () => {
        const {isSurveySkipped, surveySkipStatus, surveySkipData} = useSurveySkip()

        expect(surveySkipData.value).toBeNull()
        expect(isSurveySkipped.value).toBe(false)
        expect(surveySkipStatus.value).toBe("not_skipped")
    })

    it("records a skip and reports the survey as skipped from then on", () => {
        const {storeSurveySkipData, isSurveySkipped, surveySkipStatus} = useSurveySkip()

        storeSurveySkipData({})

        expect(JSON.parse(localStorage.getItem(SURVEY_SKIP_KEY) ?? "null")).toMatchObject({skipped: true, dialogShown: false})
        expect(isSurveySkipped.value).toBe(true)
        expect(surveySkipStatus.value).toBe("skipped")
    })

    it("keeps the skip for a second caller", () => {
        useSurveySkip().storeSurveySkipData({})

        expect(useSurveySkip().isSurveySkipped.value).toBe(true)
    })

    it("does not show the dialog again once it has been shown", () => {
        const skippedAt = new Date(Date.now() - 31 * 24 * 60 * 60 * 1000).toISOString()
        const first = useSurveySkip()
        first.storeSurveySkipData({skippedAt})
        expect(first.shouldShowHelloDialog()).toBe(true)

        first.markSurveyDialogShown()

        expect(useSurveySkip().shouldShowHelloDialog()).toBe(false)
    })

    it("treats a malformed stored value as not skipped instead of throwing", () => {
        const warn = vi.spyOn(console, "warn").mockImplementation(() => {})
        localStorage.setItem(SURVEY_SKIP_KEY, "{not json")

        const {isSurveySkipped, shouldShowHelloDialog} = useSurveySkip()

        expect(isSurveySkipped.value).toBe(false)
        expect(shouldShowHelloDialog()).toBe(false)
        expect(warn).toHaveBeenCalled()
        warn.mockRestore()
    })

    it("stays skipped when skipped twice", () => {
        const {storeSurveySkipData, isSurveySkipped} = useSurveySkip()

        storeSurveySkipData({})
        storeSurveySkipData({})

        expect(localStorage).toHaveLength(1)
        expect(JSON.parse(localStorage.getItem(SURVEY_SKIP_KEY) ?? "null")).toMatchObject({skipped: true, survey_action: "skipped"})
        expect(isSurveySkipped.value).toBe(true)
    })
})
