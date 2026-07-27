<template>
    <KsSideBar v-if="hasTabs && displayMode === 'sidebar'" class="route-tabs-sidebar" aria-label="Tabs">
        <div class="tabs-list">
            <template v-for="(tab, index) in visibleTabs" :key="tab.name ?? `header-${index}`">
                <div v-if="tab.header" class="tab-header">{{ tab.title }}</div>
                <KsTooltip
                    v-else
                    :content="tooltipFor(tab)"
                    :disabled="!tooltipFor(tab)"
                    placement="right"
                >
                    <router-link :to="routeFor(tab)" custom v-slot="{href, navigate}">
                        <KsSideBarItem
                            :title="tab.title"
                            :icon="tab.icon"
                            :href="href"
                            :active="isActive(tab)"
                            :locked="tab.locked"
                            :disabled="tab.disabled"
                            :class="{indented: hasHeader}"
                            @click="navigate"
                        >
                            <template v-if="tab.count !== undefined" #suffix>
                                <KsBadge :value="tab.count" type="primary" class="count" />
                            </template>
                        </KsSideBarItem>
                    </router-link>
                </KsTooltip>
            </template>
        </div>

        <!-- Only on the Blueprints page, where the rail has room and the readers are new. -->
        <ProductTourNudge />
    </KsSideBar>
</template>

<script setup lang="ts">
    import {computed} from "vue"

    import ProductTourNudge from "../onboarding/tour/ProductTourNudge.vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import type {RouteLocationRaw} from "vue-router"
    import {storeToRefs} from "pinia"
    import {KsSideBar, KsSideBarItem} from "@kestra-io/design-system"
    import {useRouteTabsStore, activeScopeTab, type RouteTab} from "../../stores/routeTabs"
    import {useActiveTab} from "../../composables/useActiveTab"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const routeTabsStore = useRouteTabsStore()
    const {hasTabs, visibleTabs, routeName, embedActiveTab, displayMode} = storeToRefs(routeTabsStore)
    const routeActiveTab = useActiveTab()

    const hasHeader = computed(() => visibleTabs.value.some((tab) => tab.header))

    const activeTabName = computed<string | undefined>(() => {
        if (embedActiveTab.value !== undefined) return embedActiveTab.value
        return routeActiveTab.value
    })

    /**
     * Mirrors Tabs.vue's `isRouterDriven`: once the active route exposes a
     * `meta.tab`, tab identity lives in the matched child route name rather than
     * the legacy `:tab` route param — building a link with `params: {tab}` here
     * would be silently discarded since the parent route no longer declares it.
     */
    const isRouterDriven = computed(() => route?.meta?.tab !== undefined)

    const scopeTab = computed(() => activeScopeTab(route, visibleTabs.value, router))

    function isActive(tab: RouteTab): boolean {
        if (tab.route) return tab === scopeTab.value
        const current = activeTabName.value ?? visibleTabs.value[0]?.name
        return (tab.name ?? "default") === (current ?? "default")
    }

    function routeFor(tab: RouteTab): RouteLocationRaw {
        if (tab.route) return tab.route
        const base = routeName.value || (route.name as string)
        if (isRouterDriven.value) {
            return {
                name: `${base}/${tab.name}`,
                params: {...route.params},
                query: {...tab.query} as Record<string, string>,
            }
        }
        return {
            name: base,
            params: {...route.params, tab: tab.name},
            query: {...tab.query} as Record<string, string>,
        }
    }

    function tooltipFor(tab: RouteTab): string {
        if (typeof tab.props?.tooltip === "string") {
            return tab.props.tooltip
        }
        if (tab.disabled && tab.props?.showTooltip) {
            return t("add-trigger-in-editor")
        }
        return ""
    }
</script>

<style scoped lang="scss">
    .route-tabs-sidebar {
        width: 200px;
        flex-shrink: 0;
        --ks-sidebar-item-font-weight: normal;
        --ks-sidebar-item-title-color: currentColor;
    }

    .tabs-list {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: 0 var(--ks-spacing-4);
    }

    .tab-header {
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-regular);
        color: var(--ks-text-dim);

        & ~ .tab-header {
            margin-top: var(--ks-spacing-3);
        }
    }

    .indented {
        margin-left: var(--ks-spacing-3);
    }

    .count {
        flex-shrink: 0;
        :deep(.kel-badge__content) {
            position: static;
            border: none;
            margin-top: 0;
        }
    }
</style>
