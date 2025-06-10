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

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useToast} from "../../../utils/toast";
    const toast = useToast();

    import TopNavBar from "../../../components/layout/TopNavBar.vue";
    import Editor from "../../../components/dashboard/components/Editor.vue";

    import type {Dashboard} from "../../../components/dashboard/composables/useDashboards";

    const dashboard = ref<Dashboard>({id: ""});
    const save = async (source: string) => {
        const response = await store.dispatch("dashboard/create", source);

        toast.success(t("dashboards.creation.confirmation", {title: response.title}));
        store.dispatch("core/isUnsaved", false);

        router.push({name: "home", params: {id: response.id}, query: {created: String(true)}});
    };

    import YAML_MAIN from "../assets/default_main_definition.yaml?raw";
    onMounted(async () => {
        const blueprintID = route.query.blueprintId;

        dashboard.value.sourceCode = blueprintID
            ? await store.dispatch("blueprints/getBlueprintSource", {type: "community", kind: "dashboard", id: blueprintID})
            : YAML_MAIN;
    });

    const header = computed(() => ({
        title: t("dashboards.labels.singular"),
        breadcrumb: [{label: t("dashboards.creation.label"), link: {}}],
    }));

    const context = ref({title: t("dashboards.creation.label")});

    import useRouteContext from "../../../mixins/useRouteContext";
    useRouteContext(context);
</script>