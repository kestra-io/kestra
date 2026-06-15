<template>
    <section
        v-if="vertical && activeTab"
        v-bind="attrsWithoutClass"
        :class="[containerClass, {maximized: (activeTab as Tab).maximized, 'no-overflow': (activeTab as Tab).noOverflow}]"
    >
        <TabBody />
    </section>

    <KsRouterTab
        v-else
        :tabs="tabs"
        :routeName="routeName"
        :top="top"
        :embedActiveTab="embedActiveTab"
        :class="containerClass"
        @changed="emit('changed', $event)"
    >
        <template #tab-label="{tab}">
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
                    <KsBadge v-if="tab.count !== undefined" :value="tab.count" type="primary" class="inline-badge" />
                </span>
            </EnterpriseBadge>
        </template>
        <template #content>
            <TabBody />
        </template>
    </KsRouterTab>
</template>

<script setup lang="ts">
    import {ref, computed, useAttrs, onMounted, onBeforeUnmount, watch, h, defineComponent, toHandlers, type Component} from "vue"
    import {useRoute} from "vue-router"
    import EnterpriseBadge from "./EnterpriseBadge.vue"
    import BlueprintDetail from "override/components/flows/blueprints/BlueprintDetail.vue"
    import type {RouterTab} from "@kestra-io/design-system"
    import {useRouteTabsStore} from "../stores/routeTabs"

    export interface Tab extends RouterTab {
        locked?: boolean;
        props?: any;
    }

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
    const routeTabsStore = useRouteTabsStore()
    const tabsOwnerId = Symbol("route-tabs-owner")

    const selectedBlueprintId = ref<string | undefined>(undefined)

    const activeTab = computed<Tab>(() => {
        const key = props.embedActiveTab ?? (route?.params?.tab as string | undefined)
        return props.tabs.find(t => t.name === key) ?? props.tabs[0]
    })

    const isEditorActiveTab = (tab: Tab): boolean => {
        const TAB = tab.name
        const ROUTE = route?.name as string

        if (["flows/update", "flows/create"].includes(ROUTE)) {
            return TAB === "edit"
        } else if (["namespaces/update", "namespaces/create"].includes(ROUTE)) {
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
        if (activeTab.value?.locked) return {"px-0": true, "full-container": true}
        return {"container": true, "tabs-flush-top": true}
    })

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

    watch(activeTab, () => {
        selectedBlueprintId.value = undefined
    })

    onMounted(syncStore)
    onBeforeUnmount(() => routeTabsStore.clearTabsIfOwner(tabsOwnerId))

    const TabBody = defineComponent({
        name: "TabBody",
        inheritAttrs: false,
        setup() {
            return () => {
                const tab = activeTab.value as Tab | undefined
                if (selectedBlueprintId.value) {
                    return h(BlueprintDetail, {
                        blueprintId: selectedBlueprintId.value,
                        blueprintType: "community",
                        onBack: () => (selectedBlueprintId.value = undefined),
                        combinedView: true,
                        kind: tab?.props?.blueprintKind,
                        embed: tab?.props?.embed ?? true,
                    })
                }
                if (!tab || !(isEditorActiveTab(tab) || tab.component)) return null
                return h(tab.component as Component, {
                    ...tab.props,
                    ...attrsWithoutClass.value,
                    ...toHandlers(tab["v-on"] ?? {}),
                    namespace: getNamespaceToForward(tab),
                    embed: tab.props?.embed ?? true,
                    onGoToDetail: (id: string) => (selectedBlueprintId.value = id),
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
    }

    .inline-badge {
        :deep(.kel-badge__content) {
            transform: translateY(-1px);
            position: static;
            border: none;
            margin-top: 0;
            vertical-align: middle;
        }
    }
</style>
