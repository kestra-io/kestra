<template>
    <section id="header" v-if="!embed">
        <Header
            :title="dashboard.title ?? t('overview')"
            :description="dashboard.description"
            :breadcrumb="[{label: t('dashboard_label'), link: {}}]"
            :id="dashboard.id"
        />
    </section>

    <section id="filter">
        <KestraFilter
            prefix="dashboard"
            :domain="filterDomain"
            :buttons="{
                refresh: {
                    shown: true,
                    callback: () => load(),
                },
                settings: {shown: false},
            }"
            :dashboards="{shown: route.name === 'home'}"
            @dashboard="(value) => load(value)"
        />
    </section>

    <ChartsSection :charts :show-default="dashboard.id === 'default'" />
</template>

<script setup>
    import {computed, onBeforeMount, ref} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import Header from "./components/Header.vue";
    import KestraFilter from "../filter/KestraFilter.vue";
    import ChartsSection from "./components/ChartsSection.vue";

    import DashboardFilterLanguage from "../../composables/monaco/languages/filters/impl/dashboardFilterLanguage.js";
    import NamespaceDashboardFilterLanguage from "../../composables/monaco/languages/filters/impl/namespaceDashboardFilterLanguage.js";
    import FlowDashboardFilterLanguage from "../../composables/monaco/languages/filters/impl/flowDashboardFilterLanguage.js";

    import yaml from "yaml";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import YAML_MAIN from "../../assets/dashboard/default_main_definition.yaml?raw";
    import YAML_FLOW from "../../assets/dashboard/default_flow_definition.yaml?raw";
    import YAML_NAMESPACE from "../../assets/dashboard/default_namespace_definition.yaml?raw";

    const router = useRouter();
    const route = useRoute();
    const store = useStore();
    const {t} = useI18n({useScope: "global"});

    const props = defineProps({
        embed: {type: Boolean, default: false},
        isFlow: {type: Boolean, default: false},
        isNamespace: {type: Boolean, default: false},
    });

    const filterDomain = computed(() => {
        if (props.isNamespace) {
            return NamespaceDashboardFilterLanguage.domain;
        }

        if (props.isFlow) {
            return FlowDashboardFilterLanguage.domain;
        }

        return DashboardFilterLanguage.domain;
    })

    const initial = (dashboard) => ({id: "default", ...YAML_UTILS.parse(dashboard)});

    const dashboard = ref({});
    const charts = ref([]);

    const loadCharts = async (allCharts) => {
        charts.value = [];

        for (const chart of allCharts) {
            charts.value.push({...chart, content: yaml.stringify(chart), raw: chart});
        }
    };

    const load = async (id = "default", defaultYAML = YAML_MAIN) => {
        if (!["home", "flows/update", "namespaces/update"].includes(route.name)) return;

        if(!props.isFlow && !props.isNamespace) {
            router.replace({
                params: {...route.params, id},
                query: route.params.id !== id ? {} : {...route.query},
            });
        }

        dashboard.value = id === "default" ? initial(defaultYAML) : await store.dispatch("dashboard/load", id);

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