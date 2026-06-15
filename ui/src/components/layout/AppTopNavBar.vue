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
        :tabs="selectTabs"
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

    const selectTabs = computed(() =>
        routeTabsStore.displayMode === "select" ? routeTabsStore.visibleTabs : [],
    )

    const activeTabValue = computed(() => {
        const fromEmbed = routeTabsStore.embedActiveTab
        if (fromEmbed !== undefined) return fromEmbed
        const fromRoute = route?.params?.tab
        const explicit = typeof fromRoute === "string" ? fromRoute : undefined
        return explicit ?? selectTabs.value[0]?.name ?? "default"
    })

    function onTabChange(value: string) {
        const tab = routeTabsStore.tabs.find((t) => (t.name ?? "default") === value)
        if (!tab) return
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
