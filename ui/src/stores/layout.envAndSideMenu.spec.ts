import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import {useLayoutStore} from "./layout"

describe("layout store env and side menu", () => {
    beforeEach(() => {
        localStorage.clear()
        document.documentElement.className = ""
        vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({matches: false}))
        setActivePinia(createPinia())
    })

    afterAll(() => {
        vi.unstubAllGlobals()
    })

    it("shouldPersistEnvNameToLocalStorage", () => {
        const store = useLayoutStore()

        store.setEnvName("production")

        expect(store.envName).toBe("production")
        expect(localStorage.getItem("envName")).toBe("production")
    })

    it("shouldRemoveEnvNameKeyWhenPassedUndefined", () => {
        localStorage.setItem("envName", "staging")
        const store = useLayoutStore()

        store.setEnvName(undefined)

        expect(store.envName).toBeUndefined()
        expect(localStorage.getItem("envName")).toBeNull()
    })

    it("shouldPersistEnvColorToLocalStorage", () => {
        const store = useLayoutStore()

        store.setEnvColor("#ff0000")

        expect(store.envColor).toBe("#ff0000")
        expect(localStorage.getItem("envColor")).toBe("#ff0000")
    })

    it("shouldRemoveEnvColorKeyWhenPassedUndefined", () => {
        localStorage.setItem("envColor", "#00ff00")
        const store = useLayoutStore()

        store.setEnvColor(undefined)

        expect(store.envColor).toBeUndefined()
        expect(localStorage.getItem("envColor")).toBeNull()
    })

    it("shouldLeaveEnvColorUntouchedWhenChangingEnvName", () => {
        const store = useLayoutStore()
        store.setEnvColor("#123456")

        store.setEnvName("dev")

        expect(localStorage.getItem("envColor")).toBe("#123456")
    })

    it("shouldLeaveEnvNameUntouchedWhenChangingEnvColor", () => {
        const store = useLayoutStore()
        store.setEnvName("dev")

        store.setEnvColor("#abcdef")

        expect(localStorage.getItem("envName")).toBe("dev")
    })

    it("shouldPersistTrueAndAddMenuCollapsedClassWhenCollapsed", () => {
        const store = useLayoutStore()

        store.setSideMenuCollapsed(true)

        expect(localStorage.getItem("menuCollapsed")).toBe("true")
        expect(document.documentElement.classList.contains("menu-collapsed")).toBe(true)
        expect(document.documentElement.classList.contains("menu-not-collapsed")).toBe(false)
    })

    it("shouldPersistFalseAndAddMenuNotCollapsedClassWhenExpanded", () => {
        const store = useLayoutStore()

        store.setSideMenuCollapsed(false)

        expect(localStorage.getItem("menuCollapsed")).toBe("false")
        expect(document.documentElement.classList.contains("menu-not-collapsed")).toBe(true)
        expect(document.documentElement.classList.contains("menu-collapsed")).toBe(false)
    })

    it("shouldInitialiseSideMenuCollapsedTrueWhenStoredValueIsTrue", () => {
        localStorage.setItem("menuCollapsed", "true")

        const store = useLayoutStore()

        expect(store.sideMenuCollapsed).toBe(true)
    })

    it("shouldInitialiseSideMenuCollapsedTrueWhenMediaQueryMatches", () => {
        vi.stubGlobal("matchMedia", vi.fn().mockReturnValue({matches: true}))

        const store = useLayoutStore()

        expect(store.sideMenuCollapsed).toBe(true)
    })

    it("shouldInitialiseSideMenuCollapsedFalseWhenNeitherStoredNorMediaQueryMatches", () => {
        const store = useLayoutStore()

        expect(store.sideMenuCollapsed).toBe(false)
    })

    it("shouldMergeMenuSectionCollapsedAndPersistMap", () => {
        const store = useLayoutStore()

        store.setMenuSectionCollapsed("plugins", true)
        store.setMenuSectionCollapsed("flows", false)

        expect(store.menuSectionsCollapsed).toEqual({plugins: true, flows: false})
        expect(JSON.parse(localStorage.getItem("menuSectionsCollapsed")!)).toEqual({plugins: true, flows: false})
    })

    it("shouldStoreTopNavbarValue", () => {
        const store = useLayoutStore()
        const nav = {title: "Kestra"}

        store.setTopNavbar(nav)

        expect(store.topNavbar).toEqual(nav)
    })
})
