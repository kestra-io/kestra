<template>
    <TopNavBar v-if="!embed && blueprint" :title="blueprint?.title" :breadcrumb="breadcrumb" v-ks-loading="!blueprint">
        <template #actions>
            <ul v-if="userCanCreate">
                <router-link :to="editorRoute">
                    <KsButton type="primary" v-if="!embed" @click="trackBlueprintUse('detail')">
                        {{ $t('use') }}
                    </KsButton>
                </router-link>
            </ul>
        </template>
    </TopNavBar>
    <div v-else-if="blueprint" class="header-wrapper">
        <div class="header d-flex">
            <button class="back-button align-self-center">
                <KsIcon size="sm" @click="goBack">
                    <ChevronLeft />
                </KsIcon>
            </button>
            <span class="header-title align-self-center">
                {{ $t('blueprints.title') }}
            </span>
            <router-link v-if="userCanCreate" :to="editorRoute" class="ms-auto">
                <KsButton type="primary" @click="trackBlueprintUse('detail')">
                    {{ $t('use') }}
                </KsButton>
            </router-link>
        </div>
        <div>
            <h2 class="blueprint-title align-self-center">
                {{ blueprint?.title }}
            </h2>
        </div>
    </div>

    <section v-bind="$attrs" :class="{'container': !embed}" class="blueprint-container" v-ks-loading="!blueprint">
        <KsCard v-if="blueprint && kind === 'flow'">
            <div class="embedded-topology" v-if="flowGraph">
                <LowCodeEditor
                    v-if="flowGraph"
                    :flowId="parsedFlow.id"
                    :namespace="parsedFlow.namespace"
                    :flowGraph="flowGraph"
                    :source="blueprint?.source"
                    :viewType="embed ? 'source-blueprints' : 'blueprints'"
                    isReadOnly
                />
            </div>
        </KsCard>
        <KsRow :gutter="30" v-if="blueprint">
            <KsCol :md="24" :lg="embed ? 24 : 18">
                <h4>{{ $t("source") }}</h4>
                <KsCard>
                    <Editor
                        class="position-relative"
                        :readOnly="true"
                        :input="true"
                        :fullHeight="false"
                        :modelValue="blueprint?.source"
                        lang="yaml"
                        :navbar="false"
                    >
                        <template #absolute>
                            <CopyToClipboard :text="blueprint?.source" />
                        </template>
                    </Editor>
                </KsCard>
                <template v-if="blueprint?.description">
                    <h4>{{ $t('about_this_blueprint') }}</h4>
                    <div class="tags text-uppercase">
                        <div v-for="tag in processedTags" :key="tag.original" class="tag-box">
                            <KsTag type="info" size="small">
                                {{ tag.display }}
                            </KsTag>
                        </div>
                    </div>
                    <KsMarkdown :content="blueprint?.description" />
                </template>
            </KsCol>
            <KsCol :md="24" :lg="embed ? 24 : 6" v-if="blueprint?.includedTasks?.length > 0">
                <h4>{{ $t('plugins.names') }}</h4>
                <div class="plugins-container">
                    <div v-for="task in [...new Set(blueprint?.includedTasks)]" :key="String(task)">
                        <KsTaskIcon :cls="String(task)" :icons="pluginsStore.icons" />
                    </div>
                </div>
            </KsCol>
        </KsRow>
    </section>
</template>
<script setup lang="ts">
    import {ref, computed, onMounted} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"

    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"

    import Editor from "../../../../components/inputs/Editor.vue"
    import TopNavBar from "../../../../components/layout/TopNavBar.vue"
    import LowCodeEditor from "../../../../components/inputs/LowCodeEditor.vue"
    import CopyToClipboard from "../../../../components/layout/CopyToClipboard.vue"
    import {KsTaskIcon, KsMarkdown} from "@kestra-io/design-system"

    import {useFlowStore} from "../../../../stores/flow"
    import {usePluginsStore} from "../../../../stores/plugins"
    import {useBlueprintsStore} from "../../../../stores/blueprints"
    import {useApiStore} from "../../../../stores/api"

    import {canCreate} from "override/composables/blueprintsPermissions"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"

    const props = withDefaults(defineProps<{
        blueprintId: string;
        embed?: boolean;
        blueprintType?: string;
        kind?: string;
        combinedView?: boolean;
    }>(), {
        embed: false,
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
    const tab = ref("")
    const tags = ref()

    const userCanCreate = computed(() => canCreate(props.kind))

    const parsedFlow = computed(() => {
        return blueprint.value?.source ? {
            ...YAML_UTILS.parse(blueprint.value.source),
            source: blueprint.value.source,
        } : {}
    })

    const processedTags = computed(() => {
        return blueprint.value?.tags?.map((tag: string) => ({
            original: tag,
            display: tags.value?.[tag]?.name ?? tag,
        }))
    })

    const breadcrumb = computed(() => [
        {
            label: t("blueprints.title"),
            link: {
                name: "blueprints",
                params: {
                    tenant: route.params?.tenant,
                    tab: route.params?.tab || tab.value,
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
                    tab: tab.value,
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
<style scoped lang="scss">

    .header-wrapper {
        margin-top: calc(1rem * 2);
        margin-bottom: 1rem;

        .kel-card & {
            margin-top: 2.5rem;
        }

        .header {
            margin-bottom: .5rem;

            > * {
                margin: 0;
            }

            .back-button {
                height: 32px;
                margin-left: 0;
                margin-right: calc(1rem);
                cursor: pointer;
                border: none;
                background: var(--ks-bg-surface);
                display: flex;
                align-items: center;
                border-radius: 5px;
                padding: 4px 10px;
                border: 1px solid var(--ks-border-default);
            }

            .blueprint-title {
                font-weight: 600;
                font-size: var(--ks-font-size-lg);
                line-height: 30px;
                text-overflow: ellipsis;
                overflow: hidden;
            }
        }
    }

    .blueprint-container {
        height: 100%;

        :deep(.kel-card) {
            .kel-card__body {
                padding: 0;
            }
        }

        h4 {
            margin-top: calc(1rem * 2);
            font-weight: 600;
            font-size: var(--ks-font-size-md);
            line-height: 28px;
        }

        .embedded-topology {
            max-height: 50%;
            height: 30vh;
            width: 100%;
        }

        .plugins-container {
            display: flex;
            flex-wrap: wrap;
            > div {
                background: var(--ks-bg-surface);
                border-radius: var(--kel-border-radius-base);
                min-width : 100px;
                width: 100px;
                height : 100px;
                padding: 1rem;
                margin-right: 1rem;
                margin-bottom: 1rem;
                display: flex;
                flex-wrap: wrap;
                border: 1px solid var(--ks-border-default);

                :deep(.wrapper) {
                    .icon {
                        height: 100%;
                        margin: 0;
                    }

                    .hover {
                        position: static;
                        background: none;
                        border-top: 0;
                        font-size: var(--ks-font-size-sm);
                    }

                }
            }
        }
    }

    .tags {
        margin: 10px 0;
        display: flex;

        .kel-tag.kel-tag--info {
            background-color: var(--ks-bg-surface);
            padding: 15px 10px;
            color: var(--ks-text-primary);
            text-transform: capitalize;
            font-size: var(--ks-font-size-sm);
            border: 1px solid var(--ks-border-default);
        }

        .tag-box {
            margin-right: calc(1rem / 3);
        }
    }
</style>
