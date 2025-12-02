<template>
    <TopNavBar v-bind="header" />
    <section class="full-container">
        <MultiPanelDashboardEditorView @save="save" />
    </section>
</template>

<script setup lang="ts">
    import {onMounted, computed, ref} from "vue";
    import MultiPanelDashboardEditorView from "../../../components/dashboard/components/MultiPanelDashboardEditorView.vue";

    import {useRoute} from "vue-router";

    const route = useRoute();

    import {useUnsavedChangesStore} from "../../../stores/unsavedChanges";
    const unsavedChangesStore = useUnsavedChangesStore();

    import {useDashboardStore} from "../../../stores/dashboard";
    const dashboardStore = useDashboardStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useToast} from "../../../utils/toast";
    const toast = useToast();

    import TopNavBar from "../../../components/layout/TopNavBar.vue";

    import type {Dashboard} from "../../../components/dashboard/composables/useDashboards";

    const dashboard = ref<Dashboard>({id: "", charts: []});
    const save = async (source?: string) => {
        const response = await dashboardStore.update({id: route.params.dashboard.toString(), source});

        dashboard.value.sourceCode = source;

        toast.success(t("dashboards.edition.confirmation", {title: response.title}));
        unsavedChangesStore.unsavedChange = false;
    };

    onMounted(() => {
        dashboardStore.isCreating = false;
        
        dashboardStore.load(route.params.dashboard as string).then((response) => {
            dashboard.value = response;
        });
    });

    import type {Breadcrumb} from "../../../components/namespaces/utils/useHelpers";
    const header = computed(() => ({
        title: dashboard.value?.title || route.params.dashboard.toString(),
        breadcrumb: [{label: t("dashboards.edition.label")} satisfies Breadcrumb],
    }));

    const routeInfo = computed(() => ({title: t("dashboards.edition.label")}));

    import useRouteContext from "../../../composables/useRouteContext";


    useRouteContext(routeInfo);
</script>
