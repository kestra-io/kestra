<template>
    <TopNavBar :title="header.title" :breadcrumb="header.breadcrumb" />
    <section class="full-container">
        <Editor
            v-if="dashboard.sourceCode"
            :initial-source="dashboard.sourceCode"
            allow-save-unchanged
            @save="save"
        />
    </section>
</template>

<script setup lang="ts">
    import {onMounted, computed, ref} from "vue";

    import {useRoute, useRouter} from "vue-router";
    const route = useRoute();
    const router = useRouter();

    import {useDashboardStore} from "../../../stores/dashboard";
    const dashboardStore = useDashboardStore();

    import {useCoreStore} from "../../../stores/core";
    const coreStore = useCoreStore();

    import {useBlueprintsStore} from "../../../stores/blueprints";
    const blueprintsStore = useBlueprintsStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useToast} from "../../../utils/toast";
    const toast = useToast();

    import TopNavBar from "../../../components/layout/TopNavBar.vue";
    import Editor from "../../../components/dashboard/components/Editor.vue";

    import type {Dashboard} from "../../../components/dashboard/composables/useDashboards";
    import {getDashboard, processFlowYaml} from "../../../components/dashboard/composables/useDashboards";

    import {getRandomID} from "../../../../scripts/id";

    const dashboard = ref<Dashboard>({id: "", charts: []});
    const save = async (source: string) => {
        const response = await dashboardStore.create(source)

        toast.success(t("dashboards.creation.confirmation", {title: response.title}));
        coreStore.unsavedChange = false;

        const {name, params} = route.query;

        const key = getDashboard({name, params: JSON.parse(params)}, "key")
        localStorage.setItem(key, response.id)

        router.push({name, params: {...JSON.parse(params), ...(name === "home" ? {dashboard: response.id} : {})}, query: {created: String(true)}});
    };

    import YAML_MAIN from "../assets/default_main_definition.yaml?raw";
    import YAML_FLOW from "../assets/default_flow_definition.yaml?raw";
    import YAML_NAMESPACE from "../assets/default_namespace_definition.yaml?raw";

    onMounted(async () => {
        const {blueprintId, name, params} = route.query;

        if (blueprintId) {
            dashboard.value.sourceCode = await blueprintsStore.getBlueprintSource({type: "community", kind: "dashboard", id: blueprintId});
        } else {
            if (name === "flows/update") {
                const {namespace, id} = JSON.parse(params);
                dashboard.value.sourceCode = processFlowYaml(YAML_FLOW, namespace, id);
            } else {
                dashboard.value.sourceCode = name === "namespaces/update" ? YAML_NAMESPACE : YAML_MAIN;
            }

            dashboard.value.sourceCode = "id: " + getRandomID() + "\n" + dashboard.value.sourceCode;
        }
    });

    const header = computed(() => ({
        title: t("dashboards.labels.singular"),
        breadcrumb: [{label: t("dashboards.creation.label"), link: {}}],
    }));

    const context = ref({title: t("dashboards.creation.label")});

    import useRouteContext from "../../../mixins/useRouteContext";
    useRouteContext(context);
</script>
