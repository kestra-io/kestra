<template>
    <template v-if="!embed || system">
        <TopNavBar v-if="!embed && blueprint" :title="blueprint?.title" :breadcrumb="breadcrumb" />
        <BlueprintDetailView
            v-if="blueprint"
            class="container"
            :blueprint
            :flowGraph
            :tags
            :icons="pluginsStore.icons"
            @back="goBack"
        >
            <template #actions>
                <router-link v-if="userCanCreate" :to="editorRoute">
                    <KsButton type="primary" @click="trackBlueprintUse('detail')">
                        {{ $t("blueprints.detail.openInEditor") }}
                    </KsButton>
                </router-link>
            </template>
        </BlueprintDetailView>
    </template>

    <BlueprintEmbedView
        v-else
        :blueprint
        :tags
        :icons="pluginsStore.icons"
        :kind
        @back="goBack"
    >
        <template #actions>
            <router-link v-if="userCanCreate" :to="editorRoute">
                <KsButton type="primary" @click="trackBlueprintUse('detail')">
                    {{ $t("blueprints.detail.openInEditor") }}
                </KsButton>
            </router-link>
        </template>
    </BlueprintEmbedView>
</template>
<script setup lang="ts">
    import {ref, computed, onMounted} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"

    import TopNavBar from "../../../../components/layout/TopNavBar.vue"
    import BlueprintDetailView from "../../../../components/flows/blueprints/BlueprintDetailView.vue"
    import BlueprintEmbedView from "../../../../components/flows/blueprints/BlueprintEmbedView.vue"

    import {useFlowStore} from "../../../../stores/flow"
    import {usePluginsStore} from "../../../../stores/plugins"
    import {useBlueprintsStore} from "../../../../stores/blueprints"
    import {useApiStore} from "../../../../stores/api"

    import {canCreate} from "override/composables/blueprintsPermissions"

    const props = withDefaults(defineProps<{
        blueprintId: string;
        embed?: boolean;
        system?: boolean;
        blueprintType?: string;
        kind?: string;
        combinedView?: boolean;
    }>(), {
        embed: false,
        system: false,
        blueprintType: "community",
        kind: "flow",
        combinedView: false,
    })

    const emit = defineEmits<{
        back: [];
    }>()

    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()

    const pluginsStore = usePluginsStore()
    const blueprintsStore = useBlueprintsStore()
    const flowStore = useFlowStore()
    const apiStore = useApiStore()

    const flowGraph = ref()
    const blueprint = ref()
    const tags = ref()

    const userCanCreate = computed(() => canCreate(props.kind))

    const breadcrumb = computed(() => [
        {
            label: t("blueprints.title"),
            link: {
                name: "blueprints",
                params: {
                    tenant: route.params?.tenant,
                    kind: props.kind,
                    tab: route.params?.tab || props.blueprintType,
                },
            },
        },
    ])

    const editorRoute = computed(() => {
        let additionalQuery: Record<string, any> = {}
        if (props.kind === "flow") {
            additionalQuery.blueprintSource = props.combinedView ? props.blueprintType : route.params?.tab
        } else if (props.kind === "dashboard") {
            additionalQuery = {
                name: "home",
                params: route.params?.tenant === undefined
                    ? undefined
                    : JSON.stringify({tenant: route.params.tenant}),
            }
        }

        return {name: `${props.kind}s/create`, params: {tenant: route.params?.tenant}, query: {blueprintId: props.blueprintId, ...additionalQuery}}
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

    const trackBlueprintUse = (source: "detail" | "browser") => {
        apiStore.posthogEvents({
            type: "BLUEPRINT",
            action: "use_click",
            blueprint: {
                blueprint_id: props.blueprintId,
                blueprint_kind: props.kind,
                blueprint_type: props.combinedView ? props.blueprintType : route.params?.tab,
                source,
            },
        })
    }

    const loadTags = async () => {
        const data = await blueprintsStore.getBlueprintTags({
            type: (props.combinedView ? props.blueprintType : route.params?.tab) as any,
            kind: props.kind as any,
        })
        tags.value = Object.fromEntries(data?.map((tag: any) => [tag.id, tag]) ?? [])
    }

    onMounted(async () => {
        const blueprintData = await blueprintsStore.getBlueprint({
            type: (props.combinedView ? props.blueprintType : route.params?.tab) as any,
            kind: props.kind as any,
            id: props.blueprintId,
        })
        blueprint.value = blueprintData

        await loadTags()

        const blueprintTab = props.combinedView ? props.blueprintType : route.params?.tab
        if (props.kind === "flow") {
            flowGraph.value = blueprintTab === "community"
                ? await blueprintsStore.getBlueprintGraph({
                    type: blueprintTab as any,
                    kind: props.kind as any,
                    id: props.blueprintId,
                })
                : await flowStore.getGraphFromSourceResponse({
                    flow: blueprint.value?.source,
                })
        }
    })
</script>
