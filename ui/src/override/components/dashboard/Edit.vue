<template>
    <TopNavBar :title="header.title" :breadcrumb="header.breadcrumb" />
    <section class="full-container">
        <Editor
            v-if="dashboard?.sourceCode"
            :initialSource="dashboard.sourceCode"
            @save="save"
        />
    </section>
</template>

<script setup lang="ts">
    import {onMounted, computed, ref} from "vue";

    import {useRoute} from "vue-router";

    const route = useRoute();

    import {useCoreStore} from "../../../stores/core";
    const coreStore = useCoreStore();

    import {useDashboardStore} from "../../../stores/dashboard";
    const dashboardStore = useDashboardStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useToast} from "../../../utils/toast";
    const toast = useToast();

    import TopNavBar from "../../../components/layout/TopNavBar.vue";
    // @ts-expect-error - Component not typed
    import Editor from "../../../components/dashboard/components/Editor.vue";

    import type {Dashboard} from "../../../components/dashboard/composables/useDashboards";

    const dashboard = ref<Dashboard>({id: "", charts: []});
    const save = async (source: string) => {
        const response = await dashboardStore.update({id: route.params.dashboard.toString(), source});

        dashboard.value.sourceCode = source;

        toast.success(t("dashboards.edition.confirmation", {title: response.title}));
        coreStore.unsavedChange = false;
    };

    onMounted(() => {
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
