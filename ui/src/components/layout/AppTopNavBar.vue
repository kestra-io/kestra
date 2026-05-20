<template>
    <KsTopNavBar
        v-show="store.ownerId !== null"
        :class="{playgroundMode: isPlaygroundActive}"
        :title="store.title"
        :description="store.description"
        :breadcrumb="store.breadcrumb"
        :mainIcon="activeMenuIcon"
        :beta="store.beta"
        :isBookmarked="bookmarked"
        :sidebarCollapsed="layoutStore.sideMenuCollapsed"
        :tabs="routeTabsStore.visibleTabs"
        :activeTab="activeTabValue"
        :showDescription="store.hasDescriptionSlot"
        :showDockToggle="true"
        :isDockOpen="!!miscStore.contextInfoBarOpenTab"
        @sidebar-toggle="layoutStore.setSideMenuCollapsed(false)"
        @star-click="onStarClick"
        @tab-change="onTabChange"
        @dock-toggle="togglePanel"
    >
        <template #title>
            <span id="topnav-title-slot">
                <template v-if="!store.hasTitleSlot">{{ store.title }}</template>
            </span>
        </template>
        <template #description>
            <div id="topnav-description-slot" />
        </template>
        <template #search>
            <GlobalSearch class="trigger-flow-guided-step" />
        </template>
        <template #actions>
            <div id="topnav-actions-slot" class="d-flex gap-2 align-items-center" />
        </template>
    </KsTopNavBar>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import GlobalSearch from "./GlobalSearch.vue"
    import {useBookmarksStore} from "../../stores/bookmarks"
    import {useLayoutStore} from "../../stores/layout"
    import {useTopNavStore} from "../../stores/topNav"
    import {useRouteTabsStore} from "../../stores/routeTabs"
    import {useMiscStore} from "override/stores/misc"
    import {useLeftMenu, type MenuItem} from "override/components/useLeftMenu"
    import {usePlaygroundStore} from "../../stores/playground"

    const route = useRoute()
    const router = useRouter()
    const layoutStore = useLayoutStore()
    const bookmarksStore = useBookmarksStore()
    const store = useTopNavStore()
    const routeTabsStore = useRouteTabsStore()
    const miscStore = useMiscStore()
    const playgroundStore = usePlaygroundStore()
    const {menu} = useLeftMenu()

    const isPlaygroundActive = computed(() => playgroundStore.enabled)

    function togglePanel() {
        miscStore.contextInfoBarOpenTab = miscStore.contextInfoBarOpenTab ? "" : miscStore.lastContextTab
    }

    const activeTabValue = computed(() => {
        // Tabs that bring their own `route` override (e.g. blueprints sub-pages
        // that share a route name but differ in params) must be matched by the
        // resolved full path, not by `route.params.tab`.
        const matchedByRoute = routeTabsStore.visibleTabs.find((t) => {
            if (!t.route) return false
            const resolved = router.resolve(t.route)
            if (resolved.fullPath === route?.fullPath) return true
            if (resolved.name && resolved.name === route?.name) return true
            return false
        })
        if (matchedByRoute) return matchedByRoute.name ?? "default"

        const fromEmbed = routeTabsStore.embedActiveTab
        if (fromEmbed !== undefined) return fromEmbed
        const fromRoute = route?.params?.tab
        const explicit = typeof fromRoute === "string" ? fromRoute : undefined
        return explicit ?? routeTabsStore.visibleTabs[0]?.name ?? "default"
    })

    function onTabChange(value: string) {
        const tab = routeTabsStore.tabs.find((t) => (t.name ?? "default") === value)
        if (!tab) return
        if (tab.route) {
            router.push(tab.route)
            return
        }
        router.push({
            name: routeTabsStore.routeName || (route?.name as string),
            params: {...route?.params, tab: tab.name},
            query: {...tab.query} as Record<string, string>,
        })
    }

    const flattenMenu = (items: MenuItem[]): MenuItem[] =>
        items.flatMap((item) => (item.child ? [item, ...flattenMenu(item.child)] : [item]))

    const activeMenuItem = computed<MenuItem | undefined>(() => {
        const currentName = route.name as string | undefined
        const currentPath = route.path
        return flattenMenu(menu.value).find((item) => {
            if (item.child) return false
            if (currentName && item.routes?.includes(currentName)) return true
            if (typeof item.href === "string" && item.href !== "/" && currentPath.startsWith(item.href)) return true
            return false
        })
    })

    const activeMenuIcon = computed(() => activeMenuItem.value?.icon?.element)

    const currentFavURI = computed(() =>
        route.fullPath
            .replace(/[&?]page=[^&]*/gi, "")
            .replace(/\?&/, "?")
            .replace(/\?$/, ""),
    )

    const bookmarked = computed(() =>
        bookmarksStore.pages.some((page) => page.path === currentFavURI.value),
    )

    const onStarClick = () => {
        if (bookmarked.value) {
            bookmarksStore.remove({path: currentFavURI.value})
        } else {
            bookmarksStore.add({
                path: currentFavURI.value,
                label: store.breadcrumb.length
                    ? `${store.breadcrumb[store.breadcrumb.length - 1].label}: ${store.title}`
                    : store.title,
            })
        }
    }
</script>

<style scoped lang="scss">
    .playgroundMode {
        background:
            linear-gradient(
                to right,
                rgba(23, 97, 253, 0.22) 0%,
                rgba(23, 97, 253, 0.08) 45%,
                transparent 80%
            ),
            var(--ks-bg-overlay);

        .dark & {
            background:
                linear-gradient(0deg, rgba(23, 97, 253, 0.15) 0%, rgba(23, 97, 253, 0.15) 100%),
                var(--ks-bg-overlay, #1A1C22);
        }
    }
</style>
