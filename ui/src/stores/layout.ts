import {defineStore} from "pinia"

interface State {
    topNavbar: any | undefined;
    envName: string | undefined;
    envColor: string | undefined;
    sideMenuCollapsed: boolean;
    menuSectionsCollapsed: Record<string, boolean>;
    menuItemVisibility: Record<string, boolean>;
    menuItemOrder: Record<string, string[]>;
}

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

export const useLayoutStore = defineStore("layout", {
    state: (): State => ({
        topNavbar: undefined,
        envName: localStorage.getItem("envName") || undefined,
        envColor: localStorage.getItem("envColor") || undefined,
        sideMenuCollapsed: (() => {
            if (typeof window === "undefined") {
                return false
            }

            return localStorage.getItem("menuCollapsed") === "true" || window.matchMedia("(max-width: 768px)").matches
        })(),
        menuSectionsCollapsed: readObject<Record<string, boolean>>(MENU_SECTIONS_COLLAPSED_KEY),
        menuItemVisibility: readObject<Record<string, boolean>>(MENU_ITEM_VISIBILITY_KEY),
        menuItemOrder: readObject<Record<string, string[]>>(MENU_ITEM_ORDER_KEY),
    }),
    getters: {},
    actions: {
        setTopNavbar(value: any) {
            this.topNavbar = value
        },

        setEnvName(value: string | undefined) {
            if (value) {
                localStorage.setItem("envName", value)
            } else {
                localStorage.removeItem("envName")
            }
            this.envName = value
        },

        setEnvColor(value: string | undefined) {
            if (value) {
                localStorage.setItem("envColor", value)
            } else {
                localStorage.removeItem("envColor")
            }
            this.envColor = value
        },

        setSideMenuCollapsed(value: boolean) {
            this.sideMenuCollapsed = value
            localStorage.setItem("menuCollapsed", value ? "true" : "false")

            const htmlElement = document.documentElement
            htmlElement.classList.toggle("menu-collapsed", value)
            htmlElement.classList.toggle("menu-not-collapsed", !value)
        },

        setMenuSectionCollapsed(id: string, collapsed: boolean) {
            this.menuSectionsCollapsed = {...this.menuSectionsCollapsed, [id]: collapsed}
            localStorage.setItem(MENU_SECTIONS_COLLAPSED_KEY, JSON.stringify(this.menuSectionsCollapsed))
        },

        setMenuItemVisibility(id: string, visible: boolean) {
            this.menuItemVisibility = {...this.menuItemVisibility, [id]: visible}
            localStorage.setItem(MENU_ITEM_VISIBILITY_KEY, JSON.stringify(this.menuItemVisibility))
        },

        setMenuItemOrder(sectionId: string, orderedIds: string[]) {
            this.menuItemOrder = {...this.menuItemOrder, [sectionId]: orderedIds}
            localStorage.setItem(MENU_ITEM_ORDER_KEY, JSON.stringify(this.menuItemOrder))
        },

        resetMenuCustomization() {
            this.menuItemVisibility = {}
            this.menuItemOrder = {}
            localStorage.removeItem(MENU_ITEM_VISIBILITY_KEY)
            localStorage.removeItem(MENU_ITEM_ORDER_KEY)
        },
    },
})
