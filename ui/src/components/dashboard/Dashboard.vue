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

    <Sections ref="dashboardComponent" :dashboard :charts :showDefault="dashboard.id === 'default'" :padding="padding" />
</template>

<script setup lang="ts">
    import {computed, onBeforeMount, ref, useTemplateRef} from "vue";
    import {stringify, parse} from "@kestra-io/ui-libs/flow-yaml-utils";

    import type {Dashboard, Chart} from "./composables/useDashboards";
    import {ALLOWED_CREATION_ROUTES, getDashboard, processFlowYaml} from "./composables/useDashboards";

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

    const padding = computed(() => !props.isFlow && !props.isNamespace);

    const dashboard = ref<Dashboard>({id: "", charts: []});
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

    const load = async (id = "default", defaultYAML = YAML_MAIN) => {
        if (!ALLOWED_CREATION_ROUTES.includes(String(route.name))) {
            return;
        }

        if (!props.isFlow && !props.isNamespace) {
            router.replace({
                params: {...route.params, dashboard: id},
                query: route.params.dashboard !== id ? {} : {...route.query},
            });
        }

        dashboard.value = id === "default" ? {id, charts: [], ...parse(defaultYAML)} : await dashboardStore.load(id);
        loadCharts(dashboard.value.charts);
    };

    onBeforeMount(() => {
        const ID = getDashboard(route, "id");

        if (props.isFlow && ID === "default") load("default", processFlowYaml(YAML_FLOW, route.params.namespace as string, route.params.id as string));
        else if (props.isNamespace && ID === "default") load("default", YAML_NAMESPACE);
    });
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.filterPadding {
    margin: 2rem 0.25rem 0;
    padding: 0 2rem;
}
</style>
