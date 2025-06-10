<template>
    <Header v-if="header" :dashboard />

    <section id="filter">
        <KestraFilter
            :prefix="`dashboard__${dashboard.id}`"
            :language
            :buttons="{
                refresh: {shown: true, callback: () => refresh()},
                settings: {shown: false},
            }"
            :dashboards="{shown: route.name === 'home'}"
            @dashboard="(value) => load(value)"
            :key
        />
    </section>

    <Sections :charts :show-default="dashboard.id === 'default'" padding />
</template>

<script setup>
    import {computed, onBeforeMount, ref} from "vue";

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
    const router = useRouter();
    const route = useRoute();

    import {useStore} from "vuex";
    const store = useStore();

    const props = defineProps({
        header: {type: Boolean, default: true},
        isFlow: {type: Boolean, default: false},
        isNamespace: {type: Boolean, default: false},
    });

    const dashboard = ref({});
    const charts = ref([]);

    // We use a key to force re-rendering of the Sections component when the refresh button is clicked
    const key = ref(UTILS.uid());

    const loadCharts = async (allCharts) => {
        charts.value = [];
        for (const chart of allCharts) {
            charts.value.push({...chart, content: stringify(chart)});
        }
    };

    const refresh = () => {
        key.value = UTILS.uid();
        loadCharts();
    };

    const load = async (id = "default", defaultYAML = YAML_MAIN) => {
        // Only load if the route is one of the ones below
        if (!["home", "flows/update", "namespaces/update"].includes(route.name)) {
            return;
        }

        if (!props.isFlow && !props.isNamespace) {
            router.replace({
                params: {...route.params, id},
                query: route.params.id !== id ? {} : {...route.query},
            });
        }

        dashboard.value = id === "default" ? {id, ...parse(defaultYAML)} : await store.dispatch("dashboard/load", id);
        loadCharts(dashboard.value.charts);
    };

    onBeforeMount(() => {
        if (props.isFlow) load("default", YAML_FLOW.replace(/--NAMESPACE--/g, route.params.namespace).replace(/--FLOW--/g, route.params.id));
        else if (props.isNamespace) load("default", YAML_NAMESPACE);
    });
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

section#filter {
    margin: 2rem 0.25rem 0;
    padding: 0 2rem;
}
</style>
