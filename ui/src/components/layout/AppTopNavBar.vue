<template>
    <nav v-show="store.ownerId !== null" class="top-bar d-flex align-items-center w-100">
        <KsIconButton
            v-if="layoutStore.sideMenuCollapsed"
            class="icon"
            :ariaLabel="t('Toggle menu')"
            @click="layoutStore.setSideMenuCollapsed(false)"
        >
            <Menu />
        </KsIconButton>
        <div class="title-section">
            <div class="d-flex align-items-center gap-2">
                <KsBreadcrumb
                    :items="store.breadcrumb"
                    :title="store.title"
                    :mainIcon="activeMenuIcon"
                    showLeading
                >
                    <template #title>
                        <span id="topnav-title-slot">
                            <template v-if="!store.hasTitleSlot">{{ store.title }}</template>
                        </span>
                    </template>
                </KsBreadcrumb>
                <KsTooltip v-if="store.description" :content="store.description">
                    <Information class="ms-2 icon" />
                </KsTooltip>
                <Badge v-if="store.beta" label="Beta" />
                <KsIconButton
                    class="icon"
                    :class="{'active': bookmarked}"
                    :ariaLabel="t('bookmark')"
                    @click="onStarClick"
                >
                    <component :is="bookmarked ? StarIcon : StarOutlineIcon" />
                </KsIconButton>
                <KsSelect
                    v-if="routeTabsStore.hasTabs"
                    :modelValue="activeTabValue"
                    class="tab-select"
                    size="small"
                    @change="onTabChange"
                >
                    <KsOption
                        v-for="tab in routeTabsStore.visibleTabs"
                        :key="tab.name ?? 'default'"
                        :label="tab.title"
                        :value="tab.name ?? 'default'"
                        :disabled="tab.disabled"
                    />
                </KsSelect>
            </div>
            <div v-show="store.hasDescriptionSlot" class="description">
                <div id="topnav-description-slot" />
            </div>
        </div>
        <div class="d-flex side gap-2 flex-shrink-0 align-items-center">
            <GlobalSearch class="trigger-flow-guided-step" />
            <div id="topnav-actions-slot" class="d-flex gap-2 align-items-center" />
            <KsIconButton
                class="dock-toggle"
                :class="{'is-open': miscStore.contextInfoBarOpenTab}"
                :ariaLabel="t('Toggle panel')"
                @click="togglePanel"
            >
                <DockRight />
            </KsIconButton>
        </div>
    </nav>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import GlobalSearch from "./GlobalSearch.vue"
    import StarOutlineIcon from "vue-material-design-icons/StarOutline.vue"
    import StarIcon from "vue-material-design-icons/Star.vue"
    import Information from "vue-material-design-icons/InformationOutline.vue"
    import Menu from "vue-material-design-icons/Menu.vue"
    import Badge from "../global/Badge.vue"
    import DockRight from "vue-material-design-icons/DockRight.vue"
    import {useBookmarksStore} from "../../stores/bookmarks"
    import {useLayoutStore} from "../../stores/layout"
    import {useTopNavStore} from "../../stores/topNav"
    import {useRouteTabsStore} from "../../stores/routeTabs"
    import {useMiscStore} from "override/stores/misc"
    import {useLeftMenu, type MenuItem} from "override/components/useLeftMenu"

    const route = useRoute()
    const router = useRouter()
    const layoutStore = useLayoutStore()
    const bookmarksStore = useBookmarksStore()
    const store = useTopNavStore()
    const routeTabsStore = useRouteTabsStore()
    const miscStore = useMiscStore()
    const {menu} = useLeftMenu()
    const {t} = useI18n()

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

<style lang="scss" scoped>
    .top-bar {
        height: 60px;
        flex-shrink: 0;
        padding: 0 var(--ks-spacing-6);
        gap: var(--ks-spacing-4);
        border-bottom: var(--ks-border-block-primary);
        background: var(--ks-bg-surface);

        @media (max-width: 992px) {
            padding: 0 var(--ks-spacing-5);
        }

        @media (max-width: 768px) {
            padding: 0 var(--ks-spacing-3);
        }

        @media (max-width: 664px) {
            padding: 0 var(--ks-spacing-2);
        }
    }

    .title-section {
        flex: 1 1 auto;
        min-width: 0;
        overflow: hidden;
    }

    .description {
        font-size: var(--ks-font-size-sm);
        margin-top: var(--ks-spacing-1);
        color: var(--ks-text-secondary);
    }

    .tab-select {
        width: auto;
        min-width: 140px;
        max-width: 220px;
        flex: 0 0 auto;
    }

    .icon {
        border: none;
        color: var(--ks-text-dim);

        &:deep(svg) {
            fill: currentColor;
            stroke: currentColor;
        }

        &.active {
            color: var(--ks-text-link);
        }
    }

    .dock-toggle {
        border: none;
        color: var(--ks-text-dim);

        &:deep(svg) {
            fill: currentColor;
            stroke: currentColor;
        }

        &.is-open {
            color: var(--ks-icon-default);
        }

        @media (max-width: 767px) {
            display: none;
        }
    }

    .side {
        :deep(ul) {
            display: flex;
            list-style: none;
            padding: 0;
            margin: 0;
            gap: var(--ks-spacing-2);
            align-items: center;
        }
    }
</style>
