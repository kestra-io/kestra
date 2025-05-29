<template>
    <Header v-if="header" :dashboard />

    <section id="filter">
        <KestraFilter
            :prefix="`dashboard__${dashboard.id}`"
            :domain
            :buttons="{
                refresh: {shown: true, callback: () => load()},
                settings: {shown: false},
            }"
            :dashboards="{shown: route.name === 'home'}"
            @dashboard="(value) => load(value)"
        />
    </section>

    <Sections :charts :show-default="dashboard.id === 'default'" padding />
</template>

<script setup>
    import {onBeforeMount, computed, ref} from "vue";

    import Header from "./components/Header.vue";
    import KestraFilter from "../filter/KestraFilter.vue";
    import Sections from "./sections/Sections.vue";

    import FILTER_LANGUAGE_NAMESPACE from "../../composables/monaco/languages/filters/impl/namespaceDashboardFilterLanguage.js";
    import FILTER_LANGUAGE_FLOW from "../../composables/monaco/languages/filters/impl/flowDashboardFilterLanguage.js";
    import FILTER_LANGUAGE_MAIN from "../../composables/monaco/languages/filters/impl/dashboardFilterLanguage.js";

    const domain = computed(() => {
        if (props.isNamespace) return FILTER_LANGUAGE_NAMESPACE.domain;
        if (props.isFlow) return FILTER_LANGUAGE_FLOW.domain;
        return FILTER_LANGUAGE_MAIN.domain;
    });

    import {stringify, parse} from "@kestra-io/ui-libs/flow-yaml-utils";

    import YAML_MAIN from "../../assets/dashboard/default_main_definition.yaml?raw";
    import YAML_FLOW from "../../assets/dashboard/default_flow_definition.yaml?raw";
    import YAML_NAMESPACE from "../../assets/dashboard/default_namespace_definition.yaml?raw";

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

    const loadCharts = async (allCharts) => {
        charts.value = [];

        for (const chart of allCharts) {
            charts.value.push({...chart, content: stringify(chart)});
        }
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
