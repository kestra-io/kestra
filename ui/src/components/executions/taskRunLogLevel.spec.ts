import {describe, it, expect, afterEach} from "vitest"
import {LOG_LEVELS} from "@kestra-io/design-system"
import {storageKeys} from "../../utils/constants"
import {toLevelKey, defaultTaskRunLogLevel} from "./taskRunLogLevel"

afterEach(() => {
    localStorage.removeItem(storageKeys.DEFAULT_LOG_LEVEL)
})

describe("toLevelKey", () => {
    it("returns every value in LOG_LEVELS unchanged", () => {
        for (const level of LOG_LEVELS) {
            expect(toLevelKey(level)).toBe(level)
        }
    })

    it("falls back to INFO for an unknown string", () => {
        expect(toLevelKey("UNKNOWN")).toBe("INFO")
    })

    it("falls back to INFO for null", () => {
        expect(toLevelKey(null)).toBe("INFO")
    })

    it("falls back to INFO for a lowercase level name (match is case-sensitive)", () => {
        expect(toLevelKey("debug")).toBe("INFO")
        expect(toLevelKey("warn")).toBe("INFO")
        expect(toLevelKey("error")).toBe("INFO")
    })
})

describe("defaultTaskRunLogLevel", () => {
    it("returns the stored level when it is a known log level", () => {
        const validLevel = LOG_LEVELS[0]
        localStorage.setItem(storageKeys.DEFAULT_LOG_LEVEL, validLevel)
        expect(defaultTaskRunLogLevel()).toBe(validLevel)
    })

    it("returns INFO when nothing is stored in localStorage", () => {
        expect(defaultTaskRunLogLevel()).toBe("INFO")
    })
})
