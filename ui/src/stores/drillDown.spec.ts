import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {useDrillDownStore, type DrillDownTarget} from "./drillDown"

describe("drillDown store", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("starts with isOpen as false and target as null", () => {
        const store = useDrillDownStore()

        expect(store.isOpen).toBe(false)
        expect(store.target).toBeNull()
    })

    it("stores the target and sets isOpen when open is called", () => {
        const store = useDrillDownStore()
        const target: DrillDownTarget = {
            name: "executions",
            query: {state: "FAILED"},
            timeFiltered: true,
        }

        store.open(target)

        expect(store.isOpen).toBe(true)
        expect(store.target).toEqual(target)
    })

    it("clears isOpen when close is called while keeping target intact", () => {
        const store = useDrillDownStore()
        const target: DrillDownTarget = {
            name: "logs",
            query: {level: "ERROR"},
            timeFiltered: false,
        }

        store.open(target)
        expect(store.isOpen).toBe(true)

        store.close()
        expect(store.isOpen).toBe(false)
        // target survives a close so reopening does not flash empty
        expect(store.target).toEqual(target)
    })

    it("replaces the target when opened twice", () => {
        const store = useDrillDownStore()
        const firstTarget: DrillDownTarget = {
            name: "executions",
            query: {namespace: "company.team"},
            timeFiltered: true,
        }
        const secondTarget: DrillDownTarget = {
            name: "metrics",
            query: {name: "duration"},
            timeFiltered: false,
        }

        store.open(firstTarget)
        expect(store.target).toEqual(firstTarget)

        store.open(secondTarget)
        expect(store.isOpen).toBe(true)
        expect(store.target).toEqual(secondTarget)
    })

    it("keeps query, timeFiltered and timeWindow fields intact in the target", () => {
        const store = useDrillDownStore()
        const targetWithTimeWindow: DrillDownTarget = {
            name: "executions",
            query: {
                namespace: "prod.billing",
                flowId: "process-invoices",
                page: "1",
            },
            timeFiltered: true,
            timeWindow: {
                startDate: "2026-10-01T00:00:00Z",
                endDate: "2026-10-18T23:59:59Z",
            },
        }

        store.open(targetWithTimeWindow)

        expect(store.target).toEqual(targetWithTimeWindow)
        expect(store.target?.query).toEqual({
            namespace: "prod.billing",
            flowId: "process-invoices",
            page: "1",
        })
        expect(store.target?.timeFiltered).toBe(true)
        expect(store.target?.timeWindow).toEqual({
            startDate: "2026-10-01T00:00:00Z",
            endDate: "2026-10-18T23:59:59Z",
        })
    })
})
