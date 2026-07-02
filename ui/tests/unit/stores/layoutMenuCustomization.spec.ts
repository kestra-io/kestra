import {describe, it, expect, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import {useLayoutStore} from "../../../src/stores/layout"

describe("layout store menu customization", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    it("shouldDefaultToEmptyVisibilityAndOrder", () => {
        const store = useLayoutStore()

        expect(store.menuItemVisibility).toEqual({})
        expect(store.menuItemOrder).toEqual({})
    })

    it("shouldPersistVisibilityToLocalStorage", () => {
        const store = useLayoutStore()

        store.setMenuItemVisibility("flows", false)

        expect(store.menuItemVisibility.flows).toBe(false)
        expect(JSON.parse(localStorage.getItem("menuItemVisibility")!)).toEqual({flows: false})
    })

    it("shouldPersistOrderToLocalStorage", () => {
        const store = useLayoutStore()

        store.setMenuItemOrder("workspace", ["flows", "executions", "logs"])

        expect(store.menuItemOrder.workspace).toEqual(["flows", "executions", "logs"])
        expect(JSON.parse(localStorage.getItem("menuItemOrder")!)).toEqual({
            workspace: ["flows", "executions", "logs"],
        })
    })

    it("shouldDistinguishEmptySectionFromUntouchedSection", () => {
        const store = useLayoutStore()

        store.setMenuItemOrder("resources", [])

        expect(store.menuItemOrder.resources).toEqual([])
        expect(store.menuItemOrder.workspace).toBeUndefined()
    })

    it("shouldRehydrateFromLocalStorageOnInit", () => {
        localStorage.setItem("menuItemVisibility", JSON.stringify({secrets: false}))
        localStorage.setItem("menuItemOrder", JSON.stringify({workspace: ["logs", "flows"]}))

        const store = useLayoutStore()

        expect(store.menuItemVisibility.secrets).toBe(false)
        expect(store.menuItemOrder.workspace).toEqual(["logs", "flows"])
    })

    it("shouldClearAllCustomizationOnReset", () => {
        const store = useLayoutStore()
        store.setMenuItemVisibility("flows", false)
        store.setMenuItemOrder("workspace", ["logs", "flows"])

        store.resetMenuCustomization()

        expect(store.menuItemVisibility).toEqual({})
        expect(store.menuItemOrder).toEqual({})
        expect(localStorage.getItem("menuItemVisibility")).toBeNull()
        expect(localStorage.getItem("menuItemOrder")).toBeNull()
    })
})
