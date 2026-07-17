import {defineStore} from "pinia"
import {computed, markRaw, ref, shallowRef} from "vue"
import type {Component} from "vue"
import type {RouteLocationRaw, RouteLocationNormalizedLoaded, Router} from "vue-router"

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

const startsWithSegment = (value: string, prefix: string) =>
    value === prefix || value.startsWith(`${prefix}/`)

const SUBVIEW_PARAM = "tab"

const paramsMatchScope = (
    routeParams: RouteLocationNormalizedLoaded["params"],
    targetParams: RouteLocationNormalizedLoaded["params"],
): boolean =>
    Object.keys(targetParams).every(key =>
        key === SUBVIEW_PARAM
        || String(routeParams[key] ?? "") === String(targetParams[key] ?? ""))

export function activeScopeTab(
    route: RouteLocationNormalizedLoaded,
    tabs: RouteTab[],
    router: Router,
): RouteTab | undefined {
    const scoped = tabs
        .filter(tab => tab.route && !tab.header && !tab.excludeFromScope)
        .map(tab => {
            const target = router.resolve(tab.route!)
            return {tab, path: target.path, name: String(target.name ?? ""), params: target.params}
        })

    const nameCount = new Map<string, number>()
    for (const {name} of scoped) nameCount.set(name, (nameCount.get(name) ?? 0) + 1)

    const path = route.path
    const name = String(route.name ?? "")

    return scoped.reduce<{tab?: RouteTab; score: number}>((best, scope) => {
        const byPath = startsWithSegment(path, scope.path) ? scope.path.length : 0
        const byName = scope.name && nameCount.get(scope.name) === 1
            && startsWithSegment(name, scope.name)
            && paramsMatchScope(route.params, scope.params)
            ? scope.name.length
            : 0
        const score = Math.max(byPath, byName)
        return score > best.score ? {tab: scope.tab, score} : best
    }, {score: 0}).tab
}

type RouteTabsDisplayMode = "sidebar" | "select";

interface SetTabsPayload {
    ownerId: symbol;
    tabs: RouteTab[];
    routeName?: string;
    embedActiveTab?: string;
    displayMode?: RouteTabsDisplayMode;
}

export const useRouteTabsStore = defineStore("routeTabs", () => {
    const tabs = ref<RouteTab[]>([])
    const routeName = ref<string>("")
    const embedActiveTab = ref<string | undefined>(undefined)
    const ownerId = shallowRef<symbol | null>(null)
    const displayMode = ref<RouteTabsDisplayMode>("sidebar")

    const hasTabs = computed((): boolean => tabs.value.length > 0)
    const visibleTabs = computed((): RouteTab[] => tabs.value.filter(t => !t.hidden))

    function setTabs(payload: SetTabsPayload) {
        tabs.value = payload.tabs.map(t => (t.component ? {...t, component: markRaw(t.component)} : t))
        routeName.value = payload.routeName ?? ""
        embedActiveTab.value = payload.embedActiveTab
        ownerId.value = payload.ownerId
        displayMode.value = payload.displayMode ?? "sidebar"
    }
    function clearTabsIfOwner(owner: symbol) {
        if (ownerId.value === owner) {
            tabs.value = []
            routeName.value = ""
            embedActiveTab.value = undefined
            ownerId.value = null
            displayMode.value = "sidebar"
        }
    }

    return {
        tabs,
        routeName,
        embedActiveTab,
        ownerId,
        displayMode,
        hasTabs,
        visibleTabs,
        setTabs,
        clearTabsIfOwner,
    }
})
