import {beforeEach, describe, expect, it} from "vitest"
import {setActivePinia} from "pinia"
import {routedPinia} from "../../tests/unit/memoryRouter"

import {useDrillDownStore, type DrillDownTarget} from "./drillDown"

describe("useDrillDownStore", () => {
    beforeEach(() => {
        setActivePinia(routedPinia())
    })

    it("starts closed without a target", () => {
        const store = useDrillDownStore()

        expect(store.isOpen).toBe(false)
        expect(store.target).toBeNull()
    })

    it("opens the drawer with the supplied target", () => {
        const store = useDrillDownStore()
        const target: DrillDownTarget = {name: "flows/list", query: {}, timeFiltered: false}

        store.open(target)

        expect(store.isOpen).toBe(true)
        expect(store.target).toEqual(target)
    })

    it("closes the drawer without clearing its target", () => {
        const store = useDrillDownStore()
        const target: DrillDownTarget = {
            name: "executions/list",
            query: {"filters[state][IN]": "FAILED"},
            timeFiltered: true,
            timeWindow: {"filters[timeRange][EQUALS]": "PT24H"},
        }
        store.open(target)

        store.close()

        expect(store.isOpen).toBe(false)
        expect(store.target).toEqual(target)
    })

    it("replaces the previous target when opened again", () => {
        const store = useDrillDownStore()
        store.open({
            name: "executions/list",
            query: {"filters[state][IN]": "FAILED"},
            timeFiltered: true,
            timeWindow: {"filters[timeRange][EQUALS]": "PT24H"},
        })
        const nextTarget: DrillDownTarget = {
            name: "flows/list",
            query: {"filters[namespace][IN]": "company.team"},
            timeFiltered: false,
        }

        store.open(nextTarget)

        expect(store.isOpen).toBe(true)
        expect(store.target).toEqual(nextTarget)
    })
})
