<template>
    <Header v-if="header && dashboard" :dashboard :load />

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
    import {computed, ref, useTemplateRef, watch} from "vue";
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
    import {useDashboardStore} from "../../stores/dashboard";
    import {useCoreStore} from "../../stores/core.ts";
    import {useI18n} from "vue-i18n";

    const route = useRoute();
    const router = useRouter();
    const coreStore = useCoreStore();
    const dashboardStore = useDashboardStore();
    const {t} = useI18n();

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

    const dashboard = computed<Dashboard>(() => dashboardStore.activeDashboard ?? {id: "__default", charts: []});
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
        dashboardStore.activeDashboard = {id: "default", charts: [], ...parse(getDefaultDashboardBundledInUI()), title: t("dashboards.default")}
        isDashboardBundledWithUI.value = true;
    }

    const load = async (id = "default") => {
        if (!ALLOWED_CREATION_ROUTES.includes(String(route.name))) {
            return;
        }

        const doesRouteHaveSpecificDashboard = route.params?.dashboard && typeof route.params?.dashboard === "string" && route.params?.dashboard;
        // handle navigating on /ui/dashboards
        if(route.name === "home" && !doesRouteHaveSpecificDashboard && id){
            await router.push({
                name: route.name,
                query: route.query,
                params: {...route.params, dashboard: id}
            })
            return
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
                dashboardStore.activeDashboard = maybeDashboard
            } else {

                console.warn(`default dashboard ${id} configured in the DB was not found`)
                const err = `Dashboard with id '${id}' could not be found`;
                coreStore.message = {
                    variant: "error",
                    title: err,
                    message: err,
                };
            }
        }

        await loadCharts(dashboard.value.charts);
    };

    watch([() => route.params.dashboard, () => route.params.tenant], async () => {
        if(route.params.tenant){
            // at initial load after login tenant is not yet immediately available
            const dashboardId = await dashboardStore.getDashboardId(route);
            await load(dashboardId);
        }
    }, {immediate: true});
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.filterPadding {
    margin-top: 1.5rem;
    padding: 0 2rem;
}
</style>
