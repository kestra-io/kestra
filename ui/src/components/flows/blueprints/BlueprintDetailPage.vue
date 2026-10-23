<template>
    <template v-if="!embed || system">
        <TopNavBar v-if="!embed && blueprint" :title="blueprint.title ?? ''" :breadcrumb>
            <template v-if="$slots.title" #title>
                <slot name="title" :blueprint />
            </template>
        </TopNavBar>
        <BlueprintDetailView
            v-if="blueprint"
            class="container"
            :blueprint
            :flowGraph
            :tags
            :icons="pluginsStore.icons"
            :loadIcon="pluginsStore.loadIcon"
            @back="goBack"
        >
            <template #actions="{hasMissingPlugins, missingTasks}">
                <slot
                    name="actions"
                    :blueprint
                    :blueprintType
                    :hasMissingPlugins
                    :missingTasks
                    :openInEditorKey
                >
                    <template v-if="userCanCreate">
                        <KsTooltip
                            v-if="hasMissingPlugins"
                            :content="$t('blueprints.missingPlugins.card', {tasks: missingTasks.join(', ')})"
                        >
                            <KsButton type="primary" disabled>
                                {{ $t(openInEditorKey) }}
                            </KsButton>
                        </KsTooltip>
                        <router-link v-else :to="editorRoute">
                            <KsButton type="primary" @click="trackBlueprintUse">
                                {{ $t(openInEditorKey) }}
                            </KsButton>
                        </router-link>
                    </template>
                </slot>
            </template>
            <template v-if="$slots['missing-plugins-action']" #missing-plugins-action="slotProps">
                <slot name="missing-plugins-action" v-bind="slotProps" />
            </template>
        </BlueprintDetailView>
    </template>

    <BlueprintEmbedView
        v-else
        :blueprint
        :tags
        :icons="pluginsStore.icons"
        :loadIcon="pluginsStore.loadIcon"
        :kind
        @back="goBack"
    >
        <template v-if="$slots['missing-plugins-action']" #missing-plugins-action="slotProps">
            <slot name="missing-plugins-action" v-bind="slotProps" />
        </template>
    </BlueprintEmbedView>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import type {FlowGraph} from "@kestra-io/topology/vue-flow-utils"
    import TopNavBar from "../../layout/TopNavBar.vue"
    import BlueprintDetailView from "./BlueprintDetailView.vue"
    import BlueprintEmbedView from "./BlueprintEmbedView.vue"
    import {useApiStore} from "../../../stores/api"
    import {
        useBlueprintsStore,
        type BlueprintKind,
        type BlueprintTag,
        type BlueprintType,
        type FlowBlueprint,
    } from "../../../stores/blueprints"
    import {useFlowStore} from "../../../stores/flow"
    import {usePluginsStore} from "../../../stores/plugins"
    import useRouteContext from "../../../composables/useRouteContext"
    import {canCreate} from "override/composables/blueprintsPermissions"

    // Route params arrive as props, and the undeclared ones (tab, tenant) must not fall through to the fragment root.
    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        blueprintId: string;
        embed?: boolean;
        system?: boolean;
        blueprintType?: BlueprintType;
        kind?: BlueprintKind;
        combinedView?: boolean;
    }>(), {
        embed: false,
        system: false,
        blueprintType: "community",
        kind: "flow",
        combinedView: false,
    })

    const emit = defineEmits<{back: []}>()

    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()

    const apiStore = useApiStore()
    const blueprintsStore = useBlueprintsStore()
    const flowStore = useFlowStore()
    const pluginsStore = usePluginsStore()

    const blueprint = ref<FlowBlueprint>()
    const flowGraph = ref<FlowGraph>()
    const tags = ref<Record<string, BlueprintTag>>()

    const blueprintType = computed(
        () => (props.combinedView ? props.blueprintType : route.params.tab) as BlueprintType,
    )
    const userCanCreate = computed(() => canCreate(props.kind))

    const openInEditorKey = computed(() => {
        const byKind: Partial<Record<BlueprintKind, string>> = {
            app: "blueprints.detail.openInAppsEditor",
            dashboard: "blueprints.detail.openInDashboardEditor",
        }
        return byKind[props.kind] ?? "blueprints.detail.openInEditor"
    })

    const breadcrumb = computed(() => [
        {
            label: t("blueprints.title"),
            link: {
                name: "blueprints",
                params: {
                    tenant: route.params?.tenant,
                    kind: route.params?.kind || props.kind,
                    tab: route.params?.tab || props.blueprintType,
                },
            },
        },
    ])

    const routeInfo = computed(() => ({title: blueprint.value?.title || t("blueprints.title")}))

    useRouteContext(routeInfo, props.embed)

    const editorRoute = computed(() => {
        let additionalQuery: Record<string, unknown> = {}
        if (props.kind === "flow") {
            additionalQuery.blueprintSource = blueprintType.value
        } else if (props.kind === "dashboard") {
            additionalQuery = {
                name: "home",
                params: route.params?.tenant === undefined
                    ? undefined
                    : JSON.stringify({tenant: route.params.tenant}),
            }
        }

        return {
            name: `${props.kind}s/create`,
            params: {tenant: route.params?.tenant},
            query: {blueprintId: props.blueprintId, ...additionalQuery},
        }
    })

    const goBack = () => {
        if (props.embed) {
            emit("back")
        } else {
            router.push({
                name: "blueprints",
                params: {
                    tenant: route.params?.tenant,
                    kind: route.params?.kind || props.kind,
                    tab: route.params?.tab || props.blueprintType,
                },
            })
        }
    }

    const trackBlueprintUse = () => {
        apiStore.posthogEvents({
            type: "BLUEPRINT",
            action: "use_click",
            blueprint: {
                blueprint_id: props.blueprintId,
                blueprint_kind: props.kind,
                blueprint_type: blueprintType.value,
                source: "detail",
            },
        })
    }

    const loadTags = async () => {
        const data = await blueprintsStore.getBlueprintTags({
            type: blueprintType.value,
            kind: props.kind,
        })
        tags.value = Object.fromEntries(data?.map(tag => [tag.id, tag]) ?? [])
    }

    onMounted(async () => {
        pluginsStore.fetchIcons()

        blueprint.value = await blueprintsStore.getBlueprint({
            type: blueprintType.value,
            kind: props.kind,
            id: props.blueprintId,
        })

        await loadTags()

        if (props.kind !== "flow") return

        if (blueprintType.value === "community") {
            flowGraph.value = await blueprintsStore.getBlueprintGraph({
                type: "community",
                kind: props.kind,
                id: props.blueprintId,
            })
        } else if (blueprint.value?.source && !blueprint.value.template) {
            // A template's source still holds its arguments as placeholders, so no topology can be computed from it.
            const graph = await flowStore.getGraphFromSourceResponse({flow: blueprint.value.source})
            flowGraph.value = graph as unknown as FlowGraph
        }
    })
</script>
