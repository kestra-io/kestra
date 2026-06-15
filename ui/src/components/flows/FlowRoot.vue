<template>
    <template v-if="ready">
        <FlowRootTopBar
            :routeInfo="routeInfo"
            :activeTabName="activeTabName"
        />
        <section
            v-if="isEditTabActive && activeTab"
            :class="[containerClass, {maximized: activeTab.maximized, 'no-overflow': activeTab.noOverflow}, 'padding']"
        >
            <component
                :is="activeTab.component"
                v-bind="activeTab.props"
                :embed="activeTab.props?.embed ?? true"
                @expand-subflow="updateExpandedSubflows"
            />
        </section>
        <Tabs
            v-else
            routeName="flows/update"
            :tabs="tabs"
            @expand-subflow="updateExpandedSubflows"
        />
    </template>
</template>

<script lang="ts">
    import FlowRevisions from "./FlowRevisions.vue"
    import LogsWrapper from "../logs/LogsWrapper.vue"
    import FlowExecutions from "./FlowExecutions.vue"
    import {useFlowStore} from "../../stores/flow"
    import {useRouteTabsStore} from "../../stores/routeTabs"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import Overview from "./Overview.vue"
    import Dependencies from "../dependencies/Dependencies.vue"
    import FlowMetrics from "./FlowMetrics.vue"
    import MultiPanelFlowEditorView from "./MultiPanelFlowEditorView.vue"
    import FlowTriggers from "./FlowTriggers.vue"
    import FlowConcurrency from "./FlowConcurrency.vue"
    import DemoAuditLogs from "../demo/AuditLogs.vue"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"
    import {computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"

    export interface Tab {
        name: string
        title: string
        component: any
        props?: Record<string, any>
        maximized?: boolean
        noOverflow?: boolean
        disabled?: boolean
        locked?: boolean
        count?: number
    }

    export function useFlowRoot(options?: {
        extendTabs?: (baseTabs: Tab[]) => Tab[]
    }) {
        const {t} = useI18n()
        const route = useRoute()
        const router = useRouter()

        const flowStore = useFlowStore()
        const authStore = useAuthStore()
        const miscStore = useMiscStore()
        const routeTabsStore = useRouteTabsStore()

        const previousFlow = ref<string | undefined>(undefined)
        const dependenciesCount = ref<number | undefined>(undefined)
        const deleted = ref(false)
        const tabsOwnerId = Symbol("flow-root-tabs")

        const user = computed(() => authStore.user)

        function flowKey(): string {
            return route.params.namespace + "/" + route.params.id
        }

        function load() {
            if (flowStore.flow === undefined || previousFlow.value !== flowKey()) {
                const query = {...route.query, allowDeleted: true}
                return flowStore.loadFlow({
                    ...route.params,
                    ...query,
                } as any).then(() => {
                    if (flowStore.flow) {
                        deleted.value = Boolean(flowStore.flow.deleted)
                        previousFlow.value = flowKey()
                        flowStore.loadGraph({flow: flowStore.flow})
                    }
                })
            }
        }

        function getBaseTabs() {
            const tabs: Tab[] = []

            if (user.value?.hasAny(resource.EXECUTION)) {
                tabs.push({
                    name: "overview",
                    component: Overview,
                    title: t("overview"),
                })
            }

            if (user.value && flowStore.flow && user.value.isAllowed(resource.EXECUTION, action.VIEW, flowStore.flow.namespace)) {
                tabs.push({
                    name: "executions",
                    component: FlowExecutions,
                    title: t("executions"),
                })
            }

            if (user.value && flowStore.flow && user.value.isAllowed(resource.FLOW, action.VIEW, flowStore.flow.namespace)) {
                tabs.push({
                    name: "edit",
                    component: MultiPanelFlowEditorView,
                    title: t("edit"),
                    maximized: true,
                })
            }

            if (user.value && flowStore.flow && user.value.isAllowed(resource.FLOW, action.VIEW, flowStore.flow.namespace)) {
                tabs.push({
                    name: "revisions",
                    component: FlowRevisions,
                    title: t("revisions"),
                })
            }

            if (user.value && flowStore.flow && user.value.isAllowed(resource.FLOW, action.VIEW, flowStore.flow.namespace)) {
                tabs.push({
                    name: "triggers",
                    component: FlowTriggers,
                    title: t("triggers"),
                })
            }

            if (user.value && flowStore.flow && user.value.isAllowed(resource.EXECUTION, action.VIEW, flowStore.flow.namespace)) {
                tabs.push({
                    name: "logs",
                    component: LogsWrapper,
                    title: t("logs"),
                    props: {
                        showFilters: true,
                        restoreurl: false,
                    },
                })
            }

            if (user.value && flowStore.flow && user.value.isAllowed(resource.EXECUTION, action.VIEW, flowStore.flow.namespace)) {
                tabs.push({
                    name: "metrics",
                    component: FlowMetrics,
                    title: t("metrics"),
                })
            }

            if (user.value && flowStore.flow && user.value.isAllowed(resource.FLOW, action.VIEW, flowStore.flow.namespace)) {
                tabs.push({
                    name: "dependencies",
                    component: Dependencies,
                    title: t("dependencies"),
                    count: (dependenciesCount.value ?? 0) > 0 ? dependenciesCount.value : undefined,
                    disabled: !dependenciesCount.value,
                    maximized: true,
                })
            }

            tabs.push({
                name: "concurrency",
                title: t("concurrency"),
                component: FlowConcurrency,
            })

            tabs.push({
                name: "auditlogs",
                title: t("auditlogs"),
                component: DemoAuditLogs,
                props: {embed: true},
                locked: true,
            })

            return tabs
        }

        const tabs = computed(() => {
            const base = getBaseTabs()
            return options?.extendTabs ? options.extendTabs(base) : base
        })

        function syncTabsToStore() {
            if (isEditTabActive.value) {
                routeTabsStore.setTabs({
                    ownerId: tabsOwnerId,
                    tabs: tabs.value,
                    routeName: "flows/update",
                    displayMode: "select",
                })
            } else {
                routeTabsStore.clearTabsIfOwner(tabsOwnerId)
            }
        }

        function updateExpandedSubflows(expandedSubflows: any) {
            flowStore.expandedSubflows = expandedSubflows
        }

        const activeTab = computed(() => {
            const key = route?.params?.tab
            return tabs.value.find(ta => ta.name === key) ?? tabs.value[0]
        })

        const activeTabName = computed(() => activeTab.value?.name ?? "home")

        const isEditTabActive = computed(() => activeTab.value?.name === "edit")

        const containerClass = computed(() => {
            if (activeTab.value?.locked) return {"px-0": true, "full-container": true}
            return {"container": true, "tabs-flush-top": true}
        })

        const routeInfo = computed(() => ({
            title: route.params.id.toString(),
            breadcrumb: [
                {
                    label: t("flows"),
                    link: {name: "flows/list"},
                },
                {
                    label: route.params.namespace,
                    link: {
                        name: "namespaces/update",
                        params: {id: route.params.namespace, tab: "flows"},
                    },
                },
            ],
            beta: tabs.value.find(tab => tab.name === route.params.tab)?.props?.beta,
        }))

        const ready = computed(() => user.value && flowStore.flow)

        // RouteContext mixin: update document title on route change
        function handleTitle() {
            let baseTitle: string
            if (document.title.lastIndexOf("|") > 0) {
                baseTitle = document.title.substring(document.title.lastIndexOf("|") + 1)
            } else {
                baseTitle = document.title
            }
            document.title = routeInfo.value.title + " | " + baseTitle
        }

        watch(() => route.fullPath, () => handleTitle())

        watch([tabs, isEditTabActive], () => syncTabsToStore(), {immediate: true, deep: true})

        watch(route, (newValue, oldValue) => {
            if (oldValue.name === newValue.name) {
                load()
            }
        })

        watch(() => route.params.tab, (newTab) => {
            if (newTab === "overview" || newTab === "executions") {
                const dateTimeKeys = ["startDate", "endDate", "timeRange"]
                if (!Object.keys(route.query).some((key) => dateTimeKeys.some((dateTimeKey) => key.includes(dateTimeKey)))) {
                    const DEFAULT_DURATION = miscStore.configs?.chartDefaultDuration ?? "PT24H"
                    const newQuery = {...route.query, "filters[timeRange][EQUALS]": DEFAULT_DURATION}
                    router.replace({name: route.name, params: route.params, query: newQuery})
                }
            }
        }, {immediate: true})

        watch(() => flowStore.flow, (flow) => {
            if (flow && flow.id) {
                // https://github.com/kestra-io/kestra/issues/10484
                setTimeout(() => {
                    flowStore
                        .loadDependencies({subtype: "FLOW", namespace: flow.namespace, id: flow.id}, true)
                        .then(({count}: {count: number}) => dependenciesCount.value = count > 0 ? (count - 1) : 0)
                }, 1000)
            }
        }, {deep: true})

        // created logic
        if (!route.params.tab) {
            const tab = localStorage.getItem("flowDefaultTab") || "overview"
            router.replace({
                name: "flows/update",
                params: {...route.params, tab},
                query: {...route.query},
            })
        }
        // since this component is only used in edition
        // we need to set the flag as editing in the store.
        // Specifically, it would be a problem when saving a new flow
        // and moving to edit mode.
        // NOTE: Flow creation component is ./FlowCreate.vue
        flowStore.isCreating = false
        load()

        onMounted(() => handleTitle())

        onBeforeUnmount(() => {
            routeTabsStore.clearTabsIfOwner(tabsOwnerId)
        })

        onUnmounted(() => {
            flowStore.flow = undefined
            flowStore.flowGraph = undefined
        })

        return {
            tabs,
            activeTab,
            activeTabName,
            isEditTabActive,
            containerClass,
            routeInfo,
            ready,
            updateExpandedSubflows,
        }
    }
</script>

<script setup lang="ts">
    import FlowRootTopBar from "./FlowRootTopBar.vue"
    import Tabs from "../Tabs.vue"

    withDefaults(defineProps<{embed?: boolean}>(), {embed: false})

    const {tabs, activeTab, activeTabName, isEditTabActive, containerClass, routeInfo, ready, updateExpandedSubflows} = useFlowRoot()
</script>
<style scoped lang="scss">
    .gray-700 {
        color: var(--ks-text-secondary-color);
    }
    .body-color {
        color: var(--ks-text-primary);
    }

    section.maximized {
        margin: 0 !important;
        padding: 0;
        flex-grow: 1;
    }

    section.no-overflow {
        overflow: hidden;
    }
</style>
