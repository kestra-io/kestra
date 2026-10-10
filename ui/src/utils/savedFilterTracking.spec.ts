import {describe, it, expect, vi, beforeEach} from "vitest"

const posthogEventsMock = vi.fn()
const mockConfigs: {isUiAnonymousUsageEnabled?: boolean} = {}

vi.mock("../stores/api", () => ({
    useApiStore: () => ({posthogEvents: posthogEventsMock}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: mockConfigs}),
}))

import {trackSavedFilter} from "./savedFilterTracking"

describe("trackSavedFilter", () => {
    beforeEach(() => {
        posthogEventsMock.mockReset()
        delete mockConfigs.isUiAnonymousUsageEnabled
    })

    it("sends an event with type SAVED_FILTER and mapped payload keys", () => {
        trackSavedFilter({action: "save", page: "flows", filtersCount: 3})

        expect(posthogEventsMock).toHaveBeenCalledTimes(1)
        expect(posthogEventsMock).toHaveBeenCalledWith({
            type: "SAVED_FILTER",
            action: "save",
            filter_page: "flows",
            filters_count: 3,
        })
    })

    it("sends no event when isUiAnonymousUsageEnabled is false", () => {
        mockConfigs.isUiAnonymousUsageEnabled = false

        trackSavedFilter({action: "save", page: "flows", filtersCount: 3})

        expect(posthogEventsMock).not.toHaveBeenCalled()
    })

    it("sends an event when the flag is true", () => {
        mockConfigs.isUiAnonymousUsageEnabled = true

        trackSavedFilter({action: "apply", page: "executions", filtersCount: 1})

        expect(posthogEventsMock).toHaveBeenCalledTimes(1)
    })

    it("sends an event when the flag is absent", () => {
        trackSavedFilter({action: "delete", page: "flows", filtersCount: 0})

        expect(posthogEventsMock).toHaveBeenCalledTimes(1)
    })

    it("does not propagate errors thrown by the stores", () => {
        posthogEventsMock.mockImplementationOnce(() => {
            throw new Error("boom")
        })

        expect(() =>
            trackSavedFilter({action: "save", page: "flows", filtersCount: 2}),
        ).not.toThrow()
    })
})
