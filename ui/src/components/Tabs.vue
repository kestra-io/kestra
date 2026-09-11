<template>
    <!-- Vertical mode: the horizontal bar is rendered by RouteTabsSidebar, here we only render the active tab content. -->
    <section
        v-if="vertical && activeTab"
        v-bind="attrsWithoutClass"
        :class="[containerClass, {maximized: (activeTab as Tab).maximized, 'no-overflow': (activeTab as Tab).noOverflow}]"
    >
        <TabBody />
    </section>

    <template v-else>
        <KsTabs class="ks-tabs-bar" :class="{top}" v-model="activeName" type="box">
            <KsTabPane
                v-for="tab in visibleTabs"
                :key="tab.name ?? 'default'"
                :label="tab.title"
                :name="tab.name ?? 'default'"
                :disabled="tab.disabled"
            >
                <template #label>
                    <component
                        :is="isEmbedded || tab.disabled ? 'a' : 'router-link'"
                        :to="isEmbedded ? undefined : toRoute(tab)"
                        @click="handleTabClick(tab)"
                    >
                        <KsTooltip
                            v-if="tab.disabled && (tab as Tab).props?.showTooltip"
                            :content="$t('add-trigger-in-editor')"
                            placement="top"
                        >
                            <span><strong>{{ tab.title }}</strong></span>
                        </KsTooltip>
                        <EnterpriseBadge :enable="(tab as Tab).locked">
                            <span class="tab-label-wrapper">
                                {{ tab.title }}
                                <KsBadge v-if="tab.count !== undefined" :value="tab.count" type="primary" inline />
                            </span>
                        </EnterpriseBadge>
                    </component>
                </template>
            </KsTabPane>
        </KsTabs>

        <!-- Routed pages migrated to child routes: vue-router picks the component.
             Events flow through Pinia stores, so the wrapper injects no handlers. -->
        <router-view v-if="useRouterView" v-slot="{Component, route: childRoute}">
            <section
                :class="[containerClass, {maximized: childRoute.meta.maximized, 'no-overflow': childRoute.meta.noOverflow}]"
            >
                <component :is="Component" :embed="childRoute.meta.embed ?? true" />
            </section>
        </router-view>

        <!-- Embedded mode, blueprint modal, and pages not yet migrated to child routes:
             keep the dynamic component path (no URL segment to drive a router-view).
             Skipped entirely when the tab has no body: a decorative tab bar (e.g. detail
             pages passing embedActiveTab) must not emit an empty flex-growing section. -->
        <section
            v-else-if="activeTab && hasTabBody"
            v-bind="attrsWithoutClass"
            :class="[containerClass, {maximized: (activeTab as Tab).maximized, 'no-overflow': (activeTab as Tab).noOverflow}]"
        >
            <TabBody />
        </section>
    </template>
</template>

<script setup lang="ts">
    import {ref, computed, useAttrs, onMounted, onBeforeUnmount, watch, nextTick, h, defineComponent, toHandlers, type Component} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import EnterpriseBadge from "./EnterpriseBadge.vue"
    import {useRouteTabsStore, type RouteTab} from "../stores/routeTabs"
    import {useActiveTab} from "../composables/useActiveTab"
    import {routeFamily} from "../utils/routeFamily"

    export interface Tab extends RouteTab {
        "v-on"?: Record<string, unknown>;
        /**
         * When true, the tab's content section gets the full-container layout
         * (bounded flex height) instead of the default scrolling container —
         * for tabs hosting a full-page listing (e.g. KsDataTable with fitHeight).
         */
        fullContainer?: boolean;
    }

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        tabs: Tab[];
        routeName?: string;
        top?: boolean;
        /**
         * The active embedded tab. If this component is not embedded, keep it undefined.
         */
        embedActiveTab?: string;
        namespace?: string | null;
        /**
         * When true, push the tab list into the routeTabsStore so it surfaces in
         * the vertical RouteTabsSidebar; this component then only renders the
         * active tab's content (no horizontal tab bar).
         */
        vertical?: boolean;
    }>(), {
        routeName: "",
        top: true,
        embedActiveTab: undefined,
        namespace: null,
        vertical: false,
    })

    const emit = defineEmits<{
        /**
         * Especially useful when embedded since you need to handle the embedActiveTab prop change on the parent component.
         * @property {Object} newTab the new active tab
         */
        changed: [tab: Tab];
    }>()

    const attrs = useAttrs()
    const route = useRoute()
    const router = useRouter()
    const routeTabsStore = useRouteTabsStore()
    const activeTabName = useActiveTab()
    const tabsOwnerId = Symbol("route-tabs-owner")

    const isEmbedded = computed(() => props.embedActiveTab !== undefined)

    const visibleTabs = computed(() => props.tabs.filter(t => !t.hidden))

    const activeTab = computed<Tab>(() => {
        const key = props.embedActiveTab ?? activeTabName.value
        return props.tabs.find(t => t.name === key) ?? props.tabs[0]
    })

    /**
     * A page is router-driven when its active route exposes a `meta.tab`, i.e. tab
     * identity lives in a matched child route and `<router-view>` owns the content.
     * Embedded mode and not-yet-migrated pages keep the dynamic
     * `<component :is>` path below.
     */
    const isRouterDriven = computed(() => route?.meta?.tab !== undefined)

    const useRouterView = computed(() => !props.vertical && !isEmbedded.value && isRouterDriven.value)

    /** Whether TabBody would render anything — mirrors the null cases in TabBody's render. */
    const hasTabBody = computed(() => {
        const tab = activeTab.value as Tab | undefined
        return tab !== undefined && (isEditorActiveTab(tab) || Boolean(tab.component))
    })

    const isEditorActiveTab = (tab: Tab): boolean => {
        const TAB = tab.name
        const ROUTE = route?.name as string

        if (ROUTE === "flows/create" || ROUTE?.startsWith("flows/update")) {
            return TAB === "edit"
        } else if (routeFamily(ROUTE) === "namespaces/update" || ROUTE === "namespaces/create") {
            if (TAB === "files") return true
        }

        return false
    }

    const attrsWithoutClass = computed(() => {
        return Object.fromEntries(
            Object.entries(attrs).filter(([key]) => key !== "class"),
        )
    })

    const getNamespaceToForward = (tab: Tab) => {
        return tab.props?.namespace ?? props.namespace
        // in the special case of Namespace creation on Namespaces page, the tabs are loaded before the namespace creation
        // in this case this.props.namespace will be used
    }

    const containerClass = computed(() => {
        if (activeTab.value?.locked || activeTab.value?.fullContainer) return {"px-0": true, "full-container": true}
        return {"container": true, "tabs-flush-top": true}
    })

    // --- Horizontal bar (ported from the removed KsRouterTab) ---
    const activeName = ref<string | undefined>(undefined)

    const setActiveName = () => {
        activeName.value = activeTab.value?.name ?? "default"
    }

    const handleTabClick = (tab: Tab) => {
        if (isEmbedded.value) {
            emit("changed", tab)
        }
    }

    const toRoute = (tab: Tab) => {
        if (activeTab.value === tab) {
            setActiveName()
            return route
        }
        const base = props.routeName || (route?.name as string)
        // Router-driven pages link straight to the matching child route so each tab
        // gets its own href (`<base>/<tab>`). `routeName` is the parent route name.
        // Legacy pages keep tab identity in the `:tab` route param.
        if (isRouterDriven.value) {
            return {
                name: `${base}/${tab.name}`,
                params: {...route?.params},
                query: {...tab.query},
            }
        }
        return {
            name: base,
            params: {...route?.params, tab: tab.name},
            query: {...tab.query},
        }
    }

    if (route) {
        watch(route, () => setActiveName())
    }

    watch(activeTab, () => nextTick(() => setActiveName()))

    function syncStore() {
        if (props.vertical) {
            routeTabsStore.setTabs({
                ownerId: tabsOwnerId,
                tabs: props.tabs,
                routeName: props.routeName,
                embedActiveTab: props.embedActiveTab,
            })
        } else {
            routeTabsStore.clearTabsIfOwner(tabsOwnerId)
        }
    }

    watch(
        () => [props.vertical, props.tabs, props.routeName, props.embedActiveTab],
        syncStore,
        {deep: true},
    )

    /**
     * Each tab's route component is code-split (`() => import(...)`), and vue-router
     * doesn't commit a navigation until that chunk resolves — so the first visit to
     * any given tab pays a real network-fetch delay, during which the previous tab
     * stays fully rendered. Warm every sibling tab's chunk once so switching feels
     * instant, matching the pre-migration (single eagerly-bundled) experience.
     */
    let prefetched = false
    function prefetchTabs() {
        if (prefetched || !isRouterDriven.value || !visibleTabs.value.length) return
        prefetched = true
        const base = props.routeName || (route?.name as string)
        for (const tab of visibleTabs.value) {
            if (!tab.name) continue
            try {
                const resolved = router.resolve({name: `${base}/${tab.name}`, params: {...route?.params}})
                for (const record of resolved.matched) {
                    const loader = record.components?.default
                    if (typeof loader === "function") (loader as () => Promise<unknown>)()
                }
            } catch {
                // Tab has no matching child route for this base (e.g. embedded/legacy pages); skip it.
            }
        }
    }

    onMounted(() => {
        syncStore()
        setActiveName()
        nextTick(() => prefetchTabs())
    })
    watch(visibleTabs, () => prefetchTabs())
    onBeforeUnmount(() => routeTabsStore.clearTabsIfOwner(tabsOwnerId))

    const TabBody = defineComponent({
        name: "TabBody",
        inheritAttrs: false,
        setup() {
            return () => {
                const tab = activeTab.value as Tab | undefined
                if (!tab || !(isEditorActiveTab(tab) || tab.component)) return null
                return h(tab.component as Component, {
                    ...tab.props,
                    ...attrsWithoutClass.value,
                    ...toHandlers(tab["v-on"] ?? {}),
                    namespace: getNamespaceToForward(tab),
                })
            }
        },
    })
</script>

<style scoped lang="scss">
    section.maximized {
        margin: 0 !important;
        padding: 0;
        flex-grow: 1;
        min-height: 0;
        overflow: hidden;
    }

    section.no-overflow {
        overflow: hidden;
    }

    .editor-splitter {
        height: 100%;

        :deep(.kel-splitter-panel) {
            display: flex;
            flex-direction: column;
        }
    }

    .sidebar {
        height: 100%;
        width: 100%;
    }

    .tab-label-wrapper {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        font-weight: var(--ks-font-weight-regular);
    }

    .ks-tabs-bar {
        :deep(.kel-tabs__item.is-disabled) {
            &:after {
                top: 0;
                content: "";
                position: absolute;
                display: block;
                width: 100%;
                height: 100%;
                z-index: 1000;
            }

            a {
                color: var(--ks-text-inactive);
            }
        }

        :deep(.kel-tabs__nav-next.is-disabled),
        :deep(.kel-tabs__nav-prev.is-disabled) {
            display: none;
        }
    }
</style>
