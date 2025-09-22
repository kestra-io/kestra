<template>
    <TopNavBar :title="header.title" :breadcrumb="header.breadcrumb" />
    <section class="full-container">
        <Editor
            v-if="dashboard.sourceCode"
            :initialSource="dashboard.sourceCode"
            allowSaveUnchanged
            @save="save"
        />
    </section>
</template>

<script setup lang="ts">
    import {onMounted, computed, ref} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {useDashboardStore} from "../../../stores/dashboard"
    import {useCoreStore} from "../../../stores/core"
    import {useBlueprintsStore} from "../../../stores/blueprints"
    import {useToast} from "../../../utils/toast"
    import {getRandomID} from "../../../../scripts/id"
    import type {Dashboard} from "../../../components/dashboard/composables/useDashboards"
    import {getDashboard, processFlowYaml} from "../../../components/dashboard/composables/useDashboards"
    import TopNavBar from "../../../components/layout/TopNavBar.vue"
    import Editor from "../../../components/dashboard/components/Editor.vue"
    import useRouteContext from "../../../composables/useRouteContext"

    import YAML_MAIN from "../assets/default_main_definition.yaml?raw"
    import YAML_FLOW from "../assets/default_flow_definition.yaml?raw"
    import YAML_NAMESPACE from "../assets/default_namespace_definition.yaml?raw"

    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n({useScope: "global"})

    const toast = useToast()
    const coreStore = useCoreStore()
    const dashboardStore = useDashboardStore()
    const blueprintsStore = useBlueprintsStore()

    const dashboard = ref<Dashboard>({id: "", charts: []})
    const context = ref({title: t("dashboards.creation.label")})

    const header = computed(() => ({
        title: t("dashboards.labels.singular"),
        breadcrumb: [{label: t("dashboards.creation.label"), link: {}}],
    }))

    const save = async (source: string) => {
        const response = await dashboardStore.create(source)

        toast.success(t("dashboards.creation.confirmation", {title: response.title}));
        coreStore.unsavedChange = false;

        const {name, params} = route.query;

        const key = getDashboard({name, params: JSON.parse(params)}, "key")
        localStorage.setItem(key, response.id)

        router.push({name, params: {...JSON.parse(params), ...(name === "home" ? {dashboard: response.id!} : {})}, query: {created: String(true)}})
    }

    onMounted(async () => {
        const {blueprintId, name, params} = route.query;

        if (blueprintId) {
            dashboard.value.sourceCode = await blueprintsStore.getBlueprintSource({type: "community", kind: "dashboard", id: blueprintId});
            if (!/^id:.*$/m.test(dashboard.value.sourceCode)) {
                dashboard.value.sourceCode = "id: " + blueprintId + "\n" + dashboard.value.sourceCode;
            }
        } else {
            if (name === "flows/update") {
                const {namespace, id} = JSON.parse(params)
                dashboard.value.sourceCode = processFlowYaml(YAML_FLOW, namespace, id);
            } else {
                dashboard.value.sourceCode = name === "namespaces/update" ? YAML_NAMESPACE : YAML_MAIN;
            }

            dashboard.value.sourceCode = "id: " + getRandomID() + "\n" + dashboard.value.sourceCode;
        }
    })

    useRouteContext(context)
</script>
