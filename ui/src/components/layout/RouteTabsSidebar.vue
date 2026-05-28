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
    </KsSideBar>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import type {RouteLocationRaw} from "vue-router"
    import {storeToRefs} from "pinia"
    import {KsSideBar, KsSideBarItem} from "@kestra-io/design-system"
    import {useRouteTabsStore, type RouteTab} from "../../stores/routeTabs"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const routeTabsStore = useRouteTabsStore()
    const {hasTabs, visibleTabs, routeName, embedActiveTab, displayMode} = storeToRefs(routeTabsStore)

    const hasHeader = computed(() => visibleTabs.value.some((tab) => tab.header))

    const activeTabName = computed<string | undefined>(() => {
        if (embedActiveTab.value !== undefined) return embedActiveTab.value
        const fromRoute = route.params?.tab
        return typeof fromRoute === "string" ? fromRoute : undefined
    })

    function isActive(tab: RouteTab): boolean {
        if (tab.route) {
            return router.resolve(tab.route).fullPath === route.fullPath
        }
        const current = activeTabName.value ?? visibleTabs.value[0]?.name
        return (tab.name ?? "default") === (current ?? "default")
    }

    function routeFor(tab: RouteTab): RouteLocationRaw {
        if (tab.route) return tab.route
        return {
            name: routeName.value || route.name,
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
        font-weight: 600;
        color: var(--ks-text-primary);
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
