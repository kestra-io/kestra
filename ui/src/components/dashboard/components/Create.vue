<template>
    <TopNavBar v-bind="header" />
    <section class="full-container">
        <MultiPanelDashboardEditorView @save="save" />
    </section>
</template>

<script setup lang="ts">
    import {onMounted, computed, ref} from "vue"
    import {RouteLocationGeneric, useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {useDashboardStore} from "../../../stores/dashboard"
    import {useBlueprintsStore} from "../../../stores/blueprints"
    import {useUnsavedChangesStore} from "../../../stores/unsavedChanges"
    import {useToast} from "../../../utils/toast"
    import {getRandomID} from "../../../../scripts/id"
    import {getDashboard, processFlowYaml} from "../../../components/dashboard/composables/useDashboards"
    import TopNavBar from "../../../components/layout/TopNavBar.vue"
    import useRouteContext from "../../../composables/useRouteContext"

    import YAML_MAIN from "../assets/default_main_definition.yaml?raw"
    import YAML_FLOW from "../assets/default_flow_definition.yaml?raw"
    import YAML_NAMESPACE from "../assets/default_namespace_definition.yaml?raw"
    import MultiPanelDashboardEditorView from "./MultiPanelDashboardEditorView.vue"

    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n({useScope: "global"})

    const toast = useToast()
    const dashboardStore = useDashboardStore()
    const blueprintsStore = useBlueprintsStore()
    const unsavedChangesStore = useUnsavedChangesStore()

    const context = ref({title: t("dashboards.creation.label")})

    const header = computed(() => ({
        title: t("dashboards.labels.singular"),
        breadcrumb: [{label: t("dashboards.creation.label"), link: undefined}],
    }))

    const save = async (source?: string) => {
        const response = await dashboardStore.create(source)

        toast.success(t("dashboards.creation.confirmation", {title: response.title}));
        unsavedChangesStore.unsavedChange = false;

        const name = route.query.name as string
        const params = route.query.params as string;

        const key = getDashboard({
            name,
            params: JSON.parse(params)
        } as RouteLocationGeneric, "key")
        if(key){
            localStorage.setItem(key, response.id)
        }

        router.push({name, params: {...JSON.parse(params), ...(name === "home" ? {dashboard: response.id!} : {})}, query: {created: String(true)}})
    }

    onMounted(async () => {
        dashboardStore.isCreating = true;
        
        const {blueprintId, name, params} = route.query;

        if (blueprintId) {
            dashboardStore.sourceCode = await blueprintsStore.getBlueprintSource({type: "community", kind: "dashboard", id: blueprintId as string});
            if (!/^id:.*$/m.test(dashboardStore.sourceCode ?? "")) {
                dashboardStore.sourceCode = "id: " + blueprintId + "\n" + dashboardStore.sourceCode;
            }
        } else {
            if (name === "flows/update") {
                const {namespace, id} = JSON.parse(params as string);
                dashboardStore.sourceCode = processFlowYaml(YAML_FLOW, namespace, id);
            } else {
                dashboardStore.sourceCode = name === "namespaces/update" ? YAML_NAMESPACE : YAML_MAIN;
            }

            dashboardStore.sourceCode = "id: " + getRandomID() + "\n" + dashboardStore.sourceCode;
        }
    })

    useRouteContext(context)
</script>
