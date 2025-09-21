<template>
    <Header v-if="header" :dashboard />

    <section id="filter" :class="{filterPadding: padding}">
        <KestraFilter
            :prefix="`dashboard__${dashboard.id}`"
            :language
            :buttons="{
                refresh: {shown: true, callback: () => refreshCharts()},
                settings: {shown: false},
            }"
            :dashboards="{shown: ALLOWED_CREATION_ROUTES.includes(String(route.name))}"
            @dashboard="(value: Dashboard['id']) => load(value)"
        />
    </section>

    <Sections ref="dashboardComponent" :dashboard :charts :showDefault="dashboard.id === 'default'" :padding="padding" />
</template>

<script setup lang="ts">
    import {computed, onBeforeMount, ref, useTemplateRef} from "vue";

    import type {Dashboard, Chart} from "./composables/useDashboards";
    import {ALLOWED_CREATION_ROUTES, getDashboard, processFlowYaml} from "./composables/useDashboards";

    import Header from "./components/Header.vue";
    import KestraFilter from "../filter/KestraFilter.vue";
    import Sections from "./sections/Sections.vue";

    import FILTER_LANGUAGE_MAIN from "../../composables/monaco/languages/filters/impl/dashboardFilterLanguage.js";
    import FILTER_LANGUAGE_NAMESPACE from "../../composables/monaco/languages/filters/impl/namespaceDashboardFilterLanguage.js";
    import FILTER_LANGUAGE_FLOW from "../../composables/monaco/languages/filters/impl/flowDashboardFilterLanguage.js";

    const language = computed(() => {
        if (props.isNamespace) return FILTER_LANGUAGE_NAMESPACE;
        if (props.isFlow) return FILTER_LANGUAGE_FLOW;
        return FILTER_LANGUAGE_MAIN;
    });

    import {stringify, parse} from "@kestra-io/ui-libs/flow-yaml-utils";

    import YAML_MAIN from "./assets/default_main_definition.yaml?raw";
    import YAML_FLOW from "./assets/default_flow_definition.yaml?raw";
    import YAML_NAMESPACE from "./assets/default_namespace_definition.yaml?raw";

    import {useRoute, useRouter} from "vue-router";
    const route = useRoute();
    const router = useRouter();

    import {useDashboardStore} from "../../stores/dashboard";
    const dashboardStore = useDashboardStore();

    import {useFlowStore} from "../../stores/flow";
    import {useExecutionsStore} from "../../stores/executions";
    import {useApiStore} from "../../stores/api";
    
    const flowStore = useFlowStore();
    const executionsStore = useExecutionsStore();
    const apiStore = useApiStore();

    defineOptions({inheritAttrs: false});

    const props = defineProps({
        header: {type: Boolean, default: true},
        isFlow: {type: Boolean, default: false},
        isNamespace: {type: Boolean, default: false},
    });

    const padding = computed(() => !props.isFlow && !props.isNamespace);

    const dashboard = ref<Dashboard>({id: "", charts: []});
    const charts = ref<Chart[]>([]);
    const isLoading = ref(false);

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

    const checkWelcomeRedirect = async () => {
        // Only check for OSS instances and when not loading
        if (apiStore.isOSS && !isLoading.value) {
            try {
                await flowStore.findFlows({size: 10, sort: "id:asc"});
                const executionsResponse = await executionsStore.findExecutions({size: 10});
                const executions = executionsResponse?.total ?? 0;
                
                if (!executions && !flowStore.overallTotal) {
                    router.push({name: "welcome", params: {tenant: route.params.tenant}});
                }
            } catch (error) {
                console.warn("Failed to check welcome redirect:", error);
            }
        }
    };

    const load = async (id = "default", defaultYAML = YAML_MAIN) => {
        if (!ALLOWED_CREATION_ROUTES.includes(String(route.name))) {
            return;
        }

        isLoading.value = true;

        try {
            if (!props.isFlow && !props.isNamespace) {
                router.replace({
                    params: {...route.params, dashboard: id},
                    query: route.params.dashboard !== id ? {} : {...route.query},
                });
            }

            dashboard.value = id === "default" ? {id, ...parse(defaultYAML)} : await dashboardStore.load(id);
            loadCharts(dashboard.value.charts);
        } finally {
            isLoading.value = false;
            // Check for welcome redirect after loading is complete
            await checkWelcomeRedirect();
        }
    };

    onBeforeMount(() => {
        const ID = getDashboard(route, "id");

        if (props.isFlow && ID === "default") load("default", processFlowYaml(YAML_FLOW, route.params.namespace as string, route.params.id as string));
        else if (props.isNamespace && ID === "default") load("default", YAML_NAMESPACE);
    });

    // Expose loading state for parent components
    defineExpose({
        isLoading
    });
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.filterPadding {
    margin: 2rem 0.25rem 0;
    padding: 0 2rem;
}
</style>
