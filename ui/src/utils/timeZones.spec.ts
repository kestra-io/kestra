import {afterEach, describe, expect, it, vi} from "vitest"
import {timeZones} from "./timeZones"

type IntlWithSupportedValues = typeof Intl & {
    supportedValuesOf: (key: "timeZone") => string[]
}

const intlWithSupportedValues = Intl as IntlWithSupportedValues

afterEach(() => {
    vi.restoreAllMocks()
})

describe("timeZones", () => {
    it("returns the supported time zones", () => {
        const supportedTimeZones = ["Europe/Belgrade", "America/New_York"]
        vi.spyOn(intlWithSupportedValues, "supportedValuesOf").mockReturnValue(supportedTimeZones)

        expect(timeZones()).toEqual(supportedTimeZones)
    })

    it("asks for the timeZone values", () => {
        const supportedValuesOf = vi.spyOn(intlWithSupportedValues, "supportedValuesOf").mockReturnValue(["UTC"])

        timeZones()

        expect(supportedValuesOf.mock.calls).toEqual([["timeZone"]])
    })

    it("falls back to UTC when supportedValuesOf is absent", () => {
        vi.spyOn(intlWithSupportedValues, "supportedValuesOf")
        Object.defineProperty(Intl, "supportedValuesOf", {
            configurable: true,
            value: undefined,
        })

        expect(timeZones()).toEqual(["UTC"])
    })

    it("falls back to UTC when no time zones are supported", () => {
        vi.spyOn(intlWithSupportedValues, "supportedValuesOf").mockReturnValue([])

        expect(timeZones()).toEqual(["UTC"])
    })
})
