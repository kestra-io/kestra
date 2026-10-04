import {beforeEach, describe, expect, it, vi} from "vitest"

const posthogEventsMock = vi.fn()
const mockConfigs: {isUiAnonymousUsageEnabled?: boolean} = {}

vi.mock("../../../src/stores/api", () => ({
    useApiStore: () => ({posthogEvents: posthogEventsMock}),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: mockConfigs}),
}))

import {trackSavedFilter} from "../../../src/utils/savedFilterTracking"

describe("trackSavedFilter", () => {
    beforeEach(() => {
        posthogEventsMock.mockReset()
        delete mockConfigs.isUiAnonymousUsageEnabled
    })

    it("sends the saved filter analytics payload", () => {
        trackSavedFilter({
            action: "save",
            page: "executions",
            filtersCount: 3,
        })

        expect(posthogEventsMock).toHaveBeenCalledOnce()
        expect(posthogEventsMock).toHaveBeenCalledWith({
            type: "SAVED_FILTER",
            action: "save",
            filter_page: "executions",
            filters_count: 3,
        })
    })

    it("does not send an event when anonymous UI usage is disabled", () => {
        mockConfigs.isUiAnonymousUsageEnabled = false

        trackSavedFilter({
            action: "apply",
            page: "executions",
            filtersCount: 2,
        })

        expect(posthogEventsMock).not.toHaveBeenCalled()
    })

    it.each([true, undefined])(
        "sends an event when anonymous UI usage is %s",
        (isEnabled) => {
            if (isEnabled === undefined) {
                delete mockConfigs.isUiAnonymousUsageEnabled
            } else {
                mockConfigs.isUiAnonymousUsageEnabled = isEnabled
            }

            trackSavedFilter({
                action: "apply",
                page: "executions",
                filtersCount: 2,
            })

            expect(posthogEventsMock).toHaveBeenCalledOnce()
        },
    )

    it("does not propagate analytics errors", () => {
        posthogEventsMock.mockImplementationOnce(() => {
            throw new Error("analytics failed")
        })

        expect(() =>
            trackSavedFilter({
                action: "delete",
                page: "executions",
                filtersCount: 1,
            }),
        ).not.toThrow()
    })
})
