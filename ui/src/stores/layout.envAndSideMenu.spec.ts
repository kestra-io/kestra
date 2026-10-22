import { describe, it, expect, beforeEach, afterEach, vi } from "vitest"
import { setActivePinia, createPinia } from "pinia"
import { useLayoutStore } from "./layout"
import { storageKeys } from "../utils/constants"

const stubMatchMedia = (matches: boolean | ((query: string) => boolean) = false) => {
    vi.stubGlobal("matchMedia", vi.fn((query: string) => ({
        matches: typeof matches === "function" ? matches(query) : Boolean(matches),
        media: query,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
    })))
}

describe("layout store environment banner and side menu state", () => {
    beforeEach(() => {
        localStorage.clear()
        document.documentElement.className = ""
        stubMatchMedia(false)
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.unstubAllGlobals()
        localStorage.clear()
        document.documentElement.className = ""
    })

    it("shouldPersistEnvNameAndRemoveKeyWhenUndefined", () => {
        const store = useLayoutStore()

        store.setEnvName("production")
        expect(store.envName).toBe("production")
        expect(localStorage.getItem(storageKeys.ENV_NAME)).toBe("production")

        store.setEnvName(undefined)
        expect(store.envName).toBeUndefined()
        expect(localStorage.getItem(storageKeys.ENV_NAME)).toBeNull()
    })

    it("shouldPersistEnvColorAndRemoveKeyWhenUndefined", () => {
        const store = useLayoutStore()

        store.setEnvColor("#123456")
        expect(store.envColor).toBe("#123456")
        expect(localStorage.getItem(storageKeys.ENV_COLOR)).toBe("#123456")

        store.setEnvColor(undefined)
        expect(store.envColor).toBeUndefined()
        expect(localStorage.getItem(storageKeys.ENV_COLOR)).toBeNull()
    })

    it("shouldLeaveOtherKeyUntouchedWhenSettingEnvNameOrColor", () => {
        const store = useLayoutStore()

        store.setEnvName("production")
        store.setEnvColor("#ff0000")

        store.setEnvName(undefined)
        expect(store.envColor).toBe("#ff0000")
        expect(localStorage.getItem(storageKeys.ENV_COLOR)).toBe("#ff0000")

        store.setEnvName("staging")
        store.setEnvColor(undefined)
        expect(store.envName).toBe("staging")
        expect(localStorage.getItem(storageKeys.ENV_NAME)).toBe("staging")
    })

    it("shouldPersistTrueAndToggleDocumentClassesWhenCollapsingSideMenu", () => {
        document.documentElement.classList.add("menu-not-collapsed")
        const store = useLayoutStore()

        store.setSideMenuCollapsed(true)

        expect(store.sideMenuCollapsed).toBe(true)
        expect(localStorage.getItem(storageKeys.MENU_COLLAPSED)).toBe("true")
        expect(document.documentElement.classList.contains("menu-collapsed")).toBe(true)
        expect(document.documentElement.classList.contains("menu-not-collapsed")).toBe(false)
    })

    it("shouldPersistFalseAndToggleDocumentClassesWhenExpandingSideMenu", () => {
        document.documentElement.classList.add("menu-collapsed")
        const store = useLayoutStore()

        store.setSideMenuCollapsed(false)

        expect(store.sideMenuCollapsed).toBe(false)
        expect(localStorage.getItem(storageKeys.MENU_COLLAPSED)).toBe("false")
        expect(document.documentElement.classList.contains("menu-collapsed")).toBe(false)
        expect(document.documentElement.classList.contains("menu-not-collapsed")).toBe(true)
    })

    it("shouldInitializeSideMenuCollapsedToTrueWhenStoredValueIsTrue", () => {
        localStorage.setItem(storageKeys.MENU_COLLAPSED, "true")
        stubMatchMedia(false)
        const store = useLayoutStore()

        expect(store.sideMenuCollapsed).toBe(true)
    })

    it("shouldInitializeSideMenuCollapsedToFalseWhenNeitherHolds", () => {
        stubMatchMedia(false)
        const store = useLayoutStore()

        expect(store.sideMenuCollapsed).toBe(false)
    })

    it("shouldMergeAndPersistMenuSectionsCollapsedReplacingObject", () => {
        const store = useLayoutStore()
        const initialSections = store.menuSectionsCollapsed

        store.setMenuSectionCollapsed("flows", true)

        expect(store.menuSectionsCollapsed).toEqual({ flows: true })
        expect(store.menuSectionsCollapsed).not.toBe(initialSections)
        expect(JSON.parse(localStorage.getItem("menuSectionsCollapsed")!)).toEqual({ flows: true })

        const previousSections = store.menuSectionsCollapsed
        store.setMenuSectionCollapsed("admin", false)

        expect(store.menuSectionsCollapsed).toEqual({ flows: true, admin: false })
        expect(store.menuSectionsCollapsed).not.toBe(previousSections)
        expect(JSON.parse(localStorage.getItem("menuSectionsCollapsed")!)).toEqual({ flows: true, admin: false })
    })
})
