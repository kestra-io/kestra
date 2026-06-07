import {defineStore} from "pinia"
import {markRaw} from "vue"
import type {Component} from "vue"
import type {RouteLocationRaw} from "vue-router"

export interface RouteTab {
    name?: string;
    title: string;
    hidden?: boolean;
    disabled?: boolean;
    count?: number;
    query?: Record<string, unknown>;
    component?: Component;
    props?: Record<string, any>;
    locked?: boolean;
    icon?: Component;
    excludeFromScope?: boolean;
    maximized?: boolean;
    noOverflow?: boolean;
    /**
     * Optional override for the navigation target. When set, RouteTabsSidebar
     * uses this directly instead of building one from the current route + tab.name.
     * Use this when the tabs span routes with different params (e.g. different
     * blueprint kinds).
     */
    route?: RouteLocationRaw;
    /**
     * When true, the entry is rendered as a non-interactive section header
     * inside RouteTabsSidebar (same typography as items, but no link/hover/active).
     */
    header?: boolean;
}

type RouteTabsDisplayMode = "sidebar" | "select";

interface SetTabsPayload {
    ownerId: symbol;
    tabs: RouteTab[];
    routeName?: string;
    embedActiveTab?: string;
    displayMode?: RouteTabsDisplayMode;
}

interface State {
    tabs: RouteTab[];
    routeName: string;
    embedActiveTab: string | undefined;
    ownerId: symbol | null;
    displayMode: RouteTabsDisplayMode;
}

export const useRouteTabsStore = defineStore("routeTabs", {
    state: (): State => ({
        tabs: [],
        routeName: "",
        embedActiveTab: undefined,
        ownerId: null,
        displayMode: "sidebar",
    }),
    getters: {
        hasTabs: (state): boolean => state.tabs.length > 0,
        visibleTabs: (state): RouteTab[] => state.tabs.filter(t => !t.hidden),
    },
    actions: {
        setTabs(payload: SetTabsPayload) {
            this.tabs = payload.tabs.map(t => (t.component ? {...t, component: markRaw(t.component)} : t))
            this.routeName = payload.routeName ?? ""
            this.embedActiveTab = payload.embedActiveTab
            this.ownerId = payload.ownerId
            this.displayMode = payload.displayMode ?? "sidebar"
        },
        clearTabsIfOwner(ownerId: symbol) {
            if (this.ownerId === ownerId) {
                this.tabs = []
                this.routeName = ""
                this.embedActiveTab = undefined
                this.ownerId = null
                this.displayMode = "sidebar"
            }
        },
    },
})
