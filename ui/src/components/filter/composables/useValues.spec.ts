import {describe, it, expect, vi, beforeEach} from "vitest"
import {ALL_TIME_RANGE, State} from "@kestra-io/design-system"

const miscStore = vi.hoisted(() => ({
    configs: {edition: "OSS"} as {edition?: string} | undefined,
}))

const globalT = vi.hoisted(() => vi.fn((key: string, params?: Record<string, unknown>) => {
    if (params?.label) {
        return `${key}:${params.label}`
    }
    return key
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => miscStore,
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: globalT}),
}))

import {useValues} from "./useValues"

describe("useValues", () => {
    beforeEach(() => {
        miscStore.configs = {edition: "OSS"}
        globalT.mockClear()
    })

    it("EXECUTION_STATES, LEVELS and STATUSES each build one option per value, with the value echoed", () => {
        const {VALUES} = useValues(undefined)

        const allStateNames = State.arrayAllStates().map((state: {name: string}) => state.name)
        expect(VALUES.EXECUTION_STATES).toHaveLength(allStateNames.length)
        expect(VALUES.EXECUTION_STATES.map((option) => option.value)).toEqual(allStateNames)
        for (const option of VALUES.EXECUTION_STATES) {
            expect(option.label).toBe(option.value)
        }

        const expectedLevels = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
        expect(VALUES.LEVELS).toHaveLength(expectedLevels.length)
        expect(VALUES.LEVELS.map((option) => option.value)).toEqual(expectedLevels)
        for (const option of VALUES.LEVELS) {
            expect(option.label).toBe(option.value)
        }

        const expectedStatuses = ["PENDING", "ACCEPTED", "EXPIRED"]
        expect(VALUES.STATUSES).toHaveLength(expectedStatuses.length)
        expect(VALUES.STATUSES.map((option) => option.value)).toEqual(expectedStatuses)
        for (const option of VALUES.STATUSES) {
            expect(option.label).toBe(option.value)
        }
    })

    it("LEVELS carries a per-level colour token", () => {
        const {VALUES} = useValues(undefined)

        for (const level of VALUES.LEVELS) {
            expect(level.color).toBe(`var(--ks-log-${level.value.toLowerCase()})`)
        }
    })

    it("KINDS omits TEST in OSS and includes it otherwise", () => {
        miscStore.configs = {edition: "OSS"}
        const ossValues = useValues(undefined).VALUES
        expect(ossValues.KINDS.map((kind) => kind.value)).toEqual(["NORMAL", "PLAYGROUND", "LOOP"])

        miscStore.configs = {edition: "EE"}
        const eeValues = useValues(undefined).VALUES
        expect(eeValues.KINDS.map((kind) => kind.value)).toEqual(["NORMAL", "PLAYGROUND", "LOOP", "TEST"])
    })

    it.each([
        {label: undefined, expectedLabel: "executions"},
        {label: "dashboard", expectedLabel: "executions"},
        {label: "custom_dashboard", expectedLabel: "executions"},
        {label: "flows", expectedLabel: "flows"},
    ])("SCOPES uses the executions label for undefined, dashboard and custom_dashboard, and the given label for anything else ($label)", ({label, expectedLabel}) => {
        const {VALUES} = useValues(label)

        const userScope = VALUES.SCOPES.find((scope) => scope.value === "USER")
        const systemScope = VALUES.SCOPES.find((scope) => scope.value === "SYSTEM")

        expect(userScope?.label).toBe(`scope_filter.user:${expectedLabel}`)
        expect(userScope?.description).toBe(`scope_filter.user_description:${expectedLabel}`)
        expect(systemScope?.label).toBe(`scope_filter.system:${expectedLabel}`)
        expect(systemScope?.description).toBe(`scope_filter.system_description:${expectedLabel}`)
    })

    it("RELATIVE_DATE_OR_ALL_TIME is RELATIVE_DATE plus the all-time entry, and RELATIVE_DATE_NEXT has the same values with future-facing labels", () => {
        const {VALUES} = useValues(undefined)

        expect(VALUES.RELATIVE_DATE_OR_ALL_TIME).toEqual([
            ...VALUES.RELATIVE_DATE,
            {label: "datepicker.allTime", value: ALL_TIME_RANGE},
        ])

        expect(VALUES.RELATIVE_DATE_NEXT.map((item) => item.value)).toEqual(
            VALUES.RELATIVE_DATE.map((item) => item.value),
        )
        for (const item of VALUES.RELATIVE_DATE_NEXT) {
            expect(item.label).toMatch(/^datepicker\.next/)
        }
        for (const item of VALUES.RELATIVE_DATE) {
            expect(item.label).toMatch(/^datepicker\.last/)
        }
    })

    it("getRelativeDateLabel returns the matching label and echoes an unknown value back", () => {
        const {getRelativeDateLabel} = useValues(undefined)

        expect(getRelativeDateLabel("PT1H")).toBe("datepicker.last1hour")
        expect(getRelativeDateLabel("PT24H")).toBe("datepicker.last24hours")
        expect(getRelativeDateLabel("CUSTOM_INTERVAL")).toBe("CUSTOM_INTERVAL")
    })

    it("TRIGGER_LOCK_STATES values are the strings true and false, not booleans", () => {
        const {VALUES} = useValues(undefined)

        expect(VALUES.TRIGGER_LOCK_STATES.map((state) => state.value)).toEqual(["true", "false"])
    })

    it("an explicitly passed translator is used instead of the global one", () => {
        const customT = vi.fn((key: string, params?: Record<string, unknown>) => {
            if (params?.label) {
                return `custom:${key}:${params.label}`
            }
            return `custom:${key}`
        })

        const {VALUES} = useValues(undefined, customT as never)

        expect(customT).toHaveBeenCalled()
        expect(globalT).not.toHaveBeenCalled()
        expect(VALUES.TRIGGER_STATES).toEqual([
            {label: "custom:filter.triggerState.enabled", value: "enabled"},
            {label: "custom:filter.triggerState.disabled", value: "disabled"},
        ])
    })
})
