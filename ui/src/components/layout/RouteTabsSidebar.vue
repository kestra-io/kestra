<template>
    <aside v-if="hasTabs" class="route-tabs-sidebar">
        <nav>
            <template v-for="(tab, index) in visibleTabs" :key="tab.name ?? `header-${index}`">
                <div v-if="tab.header" class="tab-header">
                    {{ tab.title }}
                </div>
                <KsTooltip
                    v-else
                    :content="tooltipFor(tab)"
                    :disabled="!tooltipFor(tab)"
                    placement="right"
                >
                    <component
                        :is="tab.disabled ? 'span' : 'router-link'"
                        :to="routeFor(tab)"
                        class="tab-link"
                        :class="{
                            active: isActive(tab),
                            disabled: tab.disabled,
                            locked: tab.locked,
                            indented: hasHeader,
                        }"
                    >
                        <span class="label">{{ tab.title }}</span>
                        <KsBadge
                            v-if="tab.count !== undefined"
                            :value="tab.count"
                            type="primary"
                            class="count"
                        />
                        <LockOutline v-if="tab.locked" class="lock-icon" />
                    </component>
                </KsTooltip>
            </template>
        </nav>
    </aside>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {storeToRefs} from "pinia"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import {useRouteTabsStore, type RouteTab} from "../../stores/routeTabs"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const routeTabsStore = useRouteTabsStore()
    const {hasTabs, visibleTabs, routeName, embedActiveTab} = storeToRefs(routeTabsStore)

    const hasHeader = computed(() => visibleTabs.value.some((t) => t.header))

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

    function routeFor(tab: RouteTab) {
        if (tab.route) return tab.route
        return {
            name: routeName.value || route.name,
            params: {...route.params, tab: tab.name},
            query: {...tab.query},
        }
    }

    function tooltipFor(tab: RouteTab): string {
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
    display: flex;
    flex-direction: column;
    background-color: var(--ks-bg-sidebar);
    border-right: 1px solid var(--ks-border-default);
    padding: 32px 18px 16px;
    overflow-y: auto;
    overflow-x: hidden;
}

nav {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.tab-header {
    padding: 6px 10px;
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    color: var(--ks-text-primary);
}

.tab-link {
    display: flex;
    align-items: center;
    padding: 6px 10px;
    gap: var(--ks-spacing-4);
    border-radius: 6px;

    &.indented {
        margin-left: 10px;
    }
    color: var(--ks-text-secondary);
    text-decoration: none;
    font-size: var(--ks-font-size-xs);
    cursor: pointer;
    transition: background-color 0.15s ease, color 0.15s ease;

    &:hover:not(.disabled):not(.active) {
        background-color: var(--ks-bg-hover);
        color: var(--ks-text-secondary);
    }

    &.active {
        background-color: var(--ks-bg-active);
        color: var(--ks-text-link);
    }

    &.locked {
        color: var(--ks-text-inactive) !important;
    }

    &.disabled {
        opacity: 0.5;
        cursor: not-allowed;
    }
}

.label {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.count {
    flex-shrink: 0;
    :deep(.kel-badge__content) {
        position: static;
        border: none;
        margin-top: 0;
    }
}

.lock-icon {
    flex-shrink: 0;
    opacity: 0.5;
    font-size: 0.875rem;
}
</style>
