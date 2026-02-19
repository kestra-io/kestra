<template>
    <Header v-if="header" :dashboard :load />

    <section id="filter" :class="{filterPadding: padding}">
        <KSFilter
            :prefix="`dashboard__${dashboard.id}`"
            :configuration="filterConfiguration"
            :tableOptions="{
                chart: {shown: false},
                columns: {shown: false},
                refresh: {shown: true, callback: () => refreshCharts()}
            }"
            :showSearchInput="false"
        />
    </section>

    <Sections ref="dashboardComponent" :dashboard :charts :showDefault="isDashboardBundledWithUI" :padding="padding" />
</template>

<script setup lang="ts">
    import {computed, onBeforeMount, ref, useTemplateRef, watch} from "vue";
    import {stringify, parse} from "@kestra-io/ui-libs/flow-yaml-utils";

    import {Dashboard, Chart, ALLOWED_CREATION_ROUTES} from "./composables/useDashboards";
    import {processFlowYaml} from "./composables/useDashboards";

    import Header from "./components/Header.vue";
    import KSFilter from "../filter/components/KSFilter.vue";
    import Sections from "./sections/Sections.vue";

    import {
        useDashboardFilter,
        useNamespaceDashboardFilter,
        useFlowDashboardFilter
    } from "../filter/configurations";

    const dashboardFilter = useDashboardFilter();
    const flowDashboardFilter = useFlowDashboardFilter();
    const namespaceDashboardFilter = useNamespaceDashboardFilter();

    const filterConfiguration = computed(() => {
        if (props.isNamespace) return namespaceDashboardFilter.value;
        if (props.isFlow) return flowDashboardFilter.value;
        return dashboardFilter.value;
    });


    import YAML_MAIN from "./assets/default_main_definition.yaml?raw";
    import YAML_FLOW from "./assets/default_flow_definition.yaml?raw";
    import YAML_NAMESPACE from "./assets/default_namespace_definition.yaml?raw";

    import {useRoute, useRouter} from "vue-router";
    const route = useRoute();
    const router = useRouter();

    import {useDashboardStore} from "../../stores/dashboard";
    const dashboardStore = useDashboardStore();

    defineOptions({inheritAttrs: false});

    const props = defineProps({
        header: {type: Boolean, default: true},
        isFlow: {type: Boolean, default: false},
        isNamespace: {type: Boolean, default: false},
    });

    const dashboardLocation = computed(() => {
        if(props.isFlow){
            return "flow_overview"
        } else if (props.isNamespace){
            return "namespace_overview"
        } else {
            return "home"
        }
    })

    const padding = computed(() => dashboardLocation.value === "home");

    const dashboard = ref<Dashboard>({id: "", charts: []});
    const isDashboardBundledWithUI = ref<boolean>(false);
    const charts = ref<Chart[]>([]);

    const loadCharts = async (allCharts: Chart[] = []) => {
        charts.value = [];

        for (const chart of allCharts) {
            charts.value.push({...chart, content: stringify(chart)});
        }
    };

    const dashboardComponent = useTemplateRef("dashboardComponent");

    const refreshCharts = () => {
        dashboardComponent.value?.refreshCharts?.();
    };
    const getDefaultDashboardBundledInUI = () => {
        if(props.isFlow){
            return processFlowYaml(YAML_FLOW, route.params.namespace as string, route.params.id as string)
        } else if(props.isNamespace){
            return YAML_NAMESPACE;
        } else {
            return YAML_MAIN
        }
    }
    const useDefaultDashboardBundledInUI = () => {
        dashboard.value = {id: "default", charts: [], ...parse(getDefaultDashboardBundledInUI())}
        isDashboardBundledWithUI.value = true;
    }

    const load = async (id = "default") => {
        if (!ALLOWED_CREATION_ROUTES.includes(String(route.name))) {
            return;
        }

        if (dashboardLocation.value === "home") {
            // Preserve timeRange filter when switching dashboards
            const preservedQuery = Object.fromEntries(
                Object.entries(route.query).filter(([key]) =>
                    key.includes("timeRange")
                )
            );

            if (route.params.dashboard !== id) {
                await router.replace({
                    params: {...route.params, dashboard: id},
                    query: preservedQuery,
                });
                return;
            }
        }
        isDashboardBundledWithUI.value = false;
        if (id === "default") {
            // if requested dashboard is the default one, we first try to find if there is any configured in the DB by an admin
            const defaults = await dashboardStore.loadDefaults();
            switch (dashboardLocation.value){
            case "home": id = defaults?.defaultHomeDashboard ?? id; break;
            case "namespace_overview": id = defaults?.defaultNamespaceOverviewDashboard ?? id; break;
            case "flow_overview": id = defaults?.defaultFlowOverviewDashboard ?? id; break;
            }
        }
        if (id === "default") {
            // we are in the case we will load the defaults bundled in the UI
            useDefaultDashboardBundledInUI();
        } else {
            // case a default dashboard exists in the DB, try to load it
            const maybeDashboard = await dashboardStore.load(id);
            if(maybeDashboard){
                dashboard.value = maybeDashboard
            } else {
                console.warn(`default dashboard ${id} configured in the DB was not found`)
                useDefaultDashboardBundledInUI();
            }

        }
        loadCharts(dashboard.value.charts);
    };

    onBeforeMount(() => {
        const ID = dashboardStore.getDashboardRelatedToThisRoute(route);
        load(ID)
    });

    watch(() => dashboardStore.getDashboardRelatedToThisRoute(route), (newId, oldId) => {
        if (newId !== oldId) {
            load(newId);
        }
    });
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.filterPadding {
    margin-top: 1.5rem;
    padding: 0 2rem;
}
</style>
