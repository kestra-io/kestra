import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {useLayoutStore} from "./layout"
import {storageKeys} from "../utils/constants"

const stubViewport = (mobile: boolean) => vi.stubGlobal("matchMedia", (query: string) => ({
    matches: mobile,
    media: query,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
}))

describe("layout store menu collapse preference", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        vi.unstubAllGlobals()
        localStorage.clear()
        document.documentElement.className = ""
    })

    it("shouldPersistTheCollapsedStateOnDesktop", () => {
        // Given
        stubViewport(false)
        const store = useLayoutStore()

        // When
        store.setSideMenuCollapsed(true)

        // Then
        expect(localStorage.getItem(storageKeys.MENU_COLLAPSED)).toBe("true")
    })

    it("shouldNotPersistTheCollapsedStateOnMobile", () => {
        // Given
        stubViewport(true)
        const store = useLayoutStore()

        // When
        store.setSideMenuCollapsed(false)
        store.setSideMenuCollapsed(true)

        // Then
        expect(store.sideMenuCollapsed).toBe(true)
        expect(localStorage.getItem(storageKeys.MENU_COLLAPSED)).toBeNull()
    })

    it("shouldStartCollapsedOnMobileWhateverWasStored", () => {
        // Given
        localStorage.setItem(storageKeys.MENU_COLLAPSED, "false")
        stubViewport(true)

        // When
        const store = useLayoutStore()

        // Then
        expect(store.sideMenuCollapsed).toBe(true)
    })
})
