<template>
    <Header v-if="header" :dashboard />

    <section id="filter">
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

    <Sections :key :charts :show-default="dashboard.id === 'default'" padding />
</template>

<script setup lang="ts">
    import {computed, onBeforeMount, ref} from "vue";

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

    import UTILS from "../../utils/utils.js";

    import {useRoute, useRouter} from "vue-router";
    const route = useRoute();
    const router = useRouter();

    import {useStore} from "vuex";
    const store = useStore();

    defineOptions({inheritAttrs: false});

    const props = defineProps({
        header: {type: Boolean, default: true},
        isFlow: {type: Boolean, default: false},
        isNamespace: {type: Boolean, default: false},
    });

    const dashboard = ref<Dashboard>({id: "", charts: []});
    const charts = ref<Chart[]>([]);

    // We use a key to force re-rendering of the Sections component
    let key = ref(UTILS.uid());

    const loadCharts = async (allCharts: Chart[] = []) => {
        charts.value = [];

        for (const chart of allCharts) {
            charts.value.push({...chart, content: stringify(chart)});
        }

        refreshCharts()
    };

    const refreshCharts = () => {
        key.value = UTILS.uid();
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

        dashboard.value = id === "default" ? {id, ...parse(defaultYAML)} : await store.dispatch("dashboard/load", id);
        loadCharts(dashboard.value.charts);
    };

    onBeforeMount(() => {
        const ID = getDashboard(route, "id");

        if (props.isFlow && ID === "default") load("default", processFlowYaml(YAML_FLOW, route.params.namespace, route.params.id));
        else if (props.isNamespace && ID === "default") load("default", YAML_NAMESPACE);
    });
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

section#filter {
    margin: 2rem 0.25rem 0;
    padding: 0 2rem;
}
</style>
