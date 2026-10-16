import {defineStore} from "pinia"
import {ref} from "vue"
import {storageKeys} from "../utils/constants"

const MENU_SECTIONS_COLLAPSED_KEY = "menuSectionsCollapsed"
const MENU_ITEM_VISIBILITY_KEY = "menuItemVisibility"
const MENU_ITEM_ORDER_KEY = "menuItemOrder"

function readObject<T>(key: string): T {
    try {
        const parsed = JSON.parse(localStorage.getItem(key) ?? "{}")
        return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {} as T
    } catch {
        return {} as T
    }
}

export const useLayoutStore = defineStore("layout", () => {
    const topNavbar = ref<unknown>()
    const envName = ref<string | undefined>(localStorage.getItem(storageKeys.ENV_NAME) || undefined)
    const envColor = ref<string | undefined>(localStorage.getItem(storageKeys.ENV_COLOR) || undefined)
    const sideMenuCollapsed = ref<boolean>((() => {
        if (typeof window === "undefined") {
            return false
        }

        return localStorage.getItem(storageKeys.MENU_COLLAPSED) === "true" || window.matchMedia("(max-width: 768px)").matches
    })())
    const menuSectionsCollapsed = ref<Record<string, boolean>>(readObject<Record<string, boolean>>(MENU_SECTIONS_COLLAPSED_KEY))
    const menuItemVisibility = ref<Record<string, boolean>>(readObject<Record<string, boolean>>(MENU_ITEM_VISIBILITY_KEY))
    const menuItemOrder = ref<Record<string, string[]>>(readObject<Record<string, string[]>>(MENU_ITEM_ORDER_KEY))

    function setTopNavbar(value: unknown) {
        topNavbar.value = value
    }

    function setEnvName(value: string | undefined) {
        if (value) {
            localStorage.setItem(storageKeys.ENV_NAME, value)
        } else {
            localStorage.removeItem(storageKeys.ENV_NAME)
        }
        envName.value = value
    }

    function setEnvColor(value: string | undefined) {
        if (value) {
            localStorage.setItem(storageKeys.ENV_COLOR, value)
        } else {
            localStorage.removeItem(storageKeys.ENV_COLOR)
        }
        envColor.value = value
    }

    function setSideMenuCollapsed(value: boolean) {
        sideMenuCollapsed.value = value
        localStorage.setItem(storageKeys.MENU_COLLAPSED, value ? "true" : "false")

        const htmlElement = document.documentElement
        htmlElement.classList.toggle("menu-collapsed", value)
        htmlElement.classList.toggle("menu-not-collapsed", !value)
    }

    function setMenuSectionCollapsed(id: string, collapsed: boolean) {
        menuSectionsCollapsed.value = {...menuSectionsCollapsed.value, [id]: collapsed}
        localStorage.setItem(MENU_SECTIONS_COLLAPSED_KEY, JSON.stringify(menuSectionsCollapsed.value))
    }

    function setMenuItemVisibility(id: string, visible: boolean) {
        menuItemVisibility.value = {...menuItemVisibility.value, [id]: visible}
        localStorage.setItem(MENU_ITEM_VISIBILITY_KEY, JSON.stringify(menuItemVisibility.value))
    }

    function setMenuItemOrder(sectionId: string, orderedIds: string[]) {
        menuItemOrder.value = {...menuItemOrder.value, [sectionId]: orderedIds}
        localStorage.setItem(MENU_ITEM_ORDER_KEY, JSON.stringify(menuItemOrder.value))
    }

    function resetMenuCustomization() {
        menuItemVisibility.value = {}
        menuItemOrder.value = {}
        localStorage.removeItem(MENU_ITEM_VISIBILITY_KEY)
        localStorage.removeItem(MENU_ITEM_ORDER_KEY)
    }

    return {
        topNavbar,
        envName,
        envColor,
        sideMenuCollapsed,
        menuSectionsCollapsed,
        menuItemVisibility,
        menuItemOrder,
        setTopNavbar,
        setEnvName,
        setEnvColor,
        setSideMenuCollapsed,
        setMenuSectionCollapsed,
        setMenuItemVisibility,
        setMenuItemOrder,
        resetMenuCustomization,
    }
})
