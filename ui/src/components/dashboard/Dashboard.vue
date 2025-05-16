<template>
    <section id="header" v-if="!embed">
        <Header
            :title="dashboard.title ?? t('overview')"
            :breadcrumb="[{label: t('dashboard_label'), link: {}}]"
            :id="dashboard.id"
        />
    </section>

    <section id="filter">
        <KestraFilter
            :prefix="dashboard.id"
            :include="['relative_date', 'absolute_date', 'namespace', 'labels']"
            :buttons="{
                refresh: {shown: true, callback: () => load()},
                settings: {shown: false},
            }"
            :dashboards="{shown: route.name === 'home'}"
            @dashboard="(value) => load(value)"
        />
    </section>

    <section id="description" v-if="dashboard.description">
        <small>{{ dashboard.description }}</small>
    </section>

    <ChartsSection :charts />
</template>

<script setup>
    import {onBeforeMount, ref} from "vue";

    import Header from "./components/Header.vue";
    import KestraFilter from "../filter/KestraFilter.vue";
    import ChartsSection from "./components/ChartsSection.vue";

    import {useRoute, useRouter} from "vue-router";
    const router = useRouter();
    const route = useRoute();

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const props = defineProps({
        embed: {type: Boolean, default: false},
        isFlow: {type: Boolean, default: false},
        isNamespace: {type: Boolean, default: false},
    });

    import yaml from "yaml";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import DEFAULT_DASHBOARD from "../../assets/dashboard/default_dashboard_definition.yaml?raw";

    const dashboard = ref({});
    const charts = ref([]);

    const loadCharts = async (allCharts) => {
        charts.value = [];

        for (const chart of allCharts) {
            charts.value.push({...chart, content: yaml.stringify(chart)});
        }
    };

    const load = async (id = "default") => {
        if (route.name !== "home") return;

        router.replace({
            params: {...route.params, id},
            query: route.params.id !== id ? {} : {...route.query},
        });

        const initial = {id: "default", ...YAML_UTILS.parse(DEFAULT_DASHBOARD)};
        dashboard.value = id === "default" ? initial : await store.dispatch("dashboard/load", id);

        loadCharts(dashboard.value.charts);
    };

    onBeforeMount(() => {
        if (props.isFlow) {
        // TODO: Load dashboard for flow
        } else if (props.isNamespace) {
        // TODO: Load dashboard for namespace
        }
    });
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

section#filter {
    margin: 2rem 0.25rem 0;
    padding: 0 2rem;
}

section#description {
    margin: 0 0.25rem;
    padding: 0 2rem 1rem;
    color: var(--ks-content-secondary);
}
</style>
