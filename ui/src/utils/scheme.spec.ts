import {afterEach, beforeEach, describe, expect, it} from "vitest"
import {LOG_LEVELS, STATES} from "@kestra-io/design-system"
import {getSchemes, getSchemeValue} from "./scheme"

const executionColors = Object.fromEntries(Object.values(STATES).map(({name}, index) => [
    name,
    `#${(index + 1).toString(16).padStart(6, "0")}`,
]))
const logColors = Object.fromEntries(LOG_LEVELS.map((level, index) => [
    level,
    `#${(index + 100).toString(16).padStart(6, "0")}`,
]))

let originalStyle: string

beforeEach(() => {
    originalStyle = document.documentElement.style.cssText
    for (const [state, color] of Object.entries(executionColors)) {
        document.documentElement.style.setProperty(`--ks-status-${state.toLowerCase()}`, ` ${color} `)
    }
    for (const [level, color] of Object.entries(logColors)) {
        document.documentElement.style.setProperty(`--ks-log-${level.toLowerCase()}`, ` ${color} `)
    }
})

afterEach(() => {
    document.documentElement.style.cssText = originalStyle
})

describe("getSchemes", () => {
    it("resolves the CSS custom property for every execution state and log level", () => {
        expect(getSchemes()).toEqual({executions: executionColors, logs: logColors})
    })

    it("falls back to transparent when an execution or log token is undefined", () => {
        document.documentElement.style.removeProperty("--ks-status-success")
        document.documentElement.style.removeProperty("--ks-log-error")

        expect(getSchemes()).toEqual({
            executions: {...executionColors, SUCCESS: "transparent"},
            logs: {...logColors, ERROR: "transparent"},
        })
    })
})

describe("getSchemeValue", () => {
    it("reads from the executions map by default", () => {
        expect(getSchemeValue("SUCCESS")).toBe(executionColors.SUCCESS)
    })

    it("reads from the logs map when requested", () => {
        expect(getSchemeValue("WARN", "logs")).toBe(logColors.WARN)
    })

    it("returns transparent for an unknown execution state or log level", () => {
        expect(getSchemeValue("UNKNOWN")).toBe("transparent")
        expect(getSchemeValue("UNKNOWN", "logs")).toBe("transparent")
    })
})
