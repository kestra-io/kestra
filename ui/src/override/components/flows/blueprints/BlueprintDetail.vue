<template>
    <TopNavBar v-if="!embed && blueprint" :title="blueprint?.title" :breadcrumb="breadcrumb" v-loading="!blueprint">
        <template #additional-right>
            <ul v-if="userCanCreate">
                <router-link :to="editorRoute">
                    <el-button type="primary" v-if="!embed">
                        {{ $t('use') }}
                    </el-button>
                </router-link>
            </ul>
        </template>
    </TopNavBar>
    <div v-else-if="blueprint" class="header-wrapper">
        <div class="header d-flex">
            <button class="back-button align-self-center">
                <el-icon size="medium" @click="goBack">
                    <ChevronLeft />
                </el-icon>
            </button>
            <span class="header-title align-self-center">
                {{ $t('blueprints.title') }}
            </span>
        </div>
        <div>
            <h2 class="blueprint-title align-self-center">
                {{ blueprint?.title }}
            </h2>
        </div>
    </div>

    <section v-bind="$attrs" :class="{'container': !embed}" class="blueprint-container" v-loading="!blueprint">
        <el-card v-if="blueprint && kind === 'flow'">
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
        </el-card>
        <el-row :gutter="30" v-if="blueprint">
            <el-col :md="24" :lg="embed ? 24 : 18">
                <h4>{{ $t("source") }}</h4>
                <el-card>
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
                </el-card>
                <template v-if="blueprint?.description">
                    <h4>{{ $t('about_this_blueprint') }}</h4>
                    <div class="tags text-uppercase">
                        <div v-for="tag in processedTags" :key="tag.original" class="tag-box">
                            <el-tag type="info" size="small">
                                {{ tag.display }}
                            </el-tag>
                        </div>
                    </div>
                    <Markdown :source="blueprint?.description" />
                </template>
            </el-col>
            <el-col :md="24" :lg="embed ? 24 : 6" v-if="blueprint?.includedTasks?.length > 0">
                <h4>{{ $t('plugins.names') }}</h4>
                <div class="plugins-container">
                    <div v-for="task in [...new Set(blueprint?.includedTasks)]" :key="String(task)">
                        <TaskIcon :cls="String(task)" :icons="pluginsStore.icons" />
                    </div>
                </div>
            </el-col>
        </el-row>
    </section>
</template>
<script setup lang="ts">
    import {ref, computed, onMounted} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {useI18n} from "vue-i18n";

    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";

    import Editor from "../../../../components/inputs/Editor.vue";
    import Markdown from "../../../../components/layout/Markdown.vue";
    import TopNavBar from "../../../../components/layout/TopNavBar.vue";
    import LowCodeEditor from "../../../../components/inputs/LowCodeEditor.vue";
    import CopyToClipboard from "../../../../components/layout/CopyToClipboard.vue";
    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";

    import {useFlowStore} from "../../../../stores/flow";
    import {usePluginsStore} from "../../../../stores/plugins";
    import {useBlueprintsStore} from "../../../../stores/blueprints";

    import {canCreate} from "override/composables/blueprintsPermissions";
    import {parse as parseFlow} from "@kestra-io/ui-libs/flow-yaml-utils";

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
        combinedView: false
    });

    const emit = defineEmits<{
        back: [];
    }>();

    const route = useRoute();
    const router = useRouter();
    const {t} = useI18n();

    const pluginsStore = usePluginsStore();
    const blueprintsStore = useBlueprintsStore();
    const flowStore = useFlowStore();

    const flowGraph = ref();
    const blueprint = ref();
    const tab = ref("");
    const tags = ref();

    const userCanCreate = computed(() => canCreate(props.kind));

    const parsedFlow = computed(() => {
        return blueprint.value?.source ? {
            ...parseFlow(blueprint.value.source),
            source: blueprint.value.source
        } : {};
    });

    const processedTags = computed(() => {
        return blueprint.value?.tags?.map((tag: string) => ({
            original: tag,
            display: tags.value?.[tag]?.name ?? tag
        }));
    });

    const breadcrumb = computed(() => [
        {
            label: t("blueprints.title"),
            link: {
                name: "blueprints",
                params: {
                    tenant: route.params?.tenant,
                    tab: route.params?.tab || tab.value
                }
            }
        }
    ]);

    const editorRoute = computed(() => {
        let additionalQuery: Record<string, any> = {};
        if (props.kind === "flow") {
            additionalQuery.blueprintSource = route.params?.tab;
        } else if (props.kind === "dashboard") {
            additionalQuery = {
                name: "home",
                params: route.params?.tenant === undefined
                    ? undefined
                    : JSON.stringify({tenant: route.params.tenant}),
            };
        }

        return {name: `${props.kind}s/create`, params: {tenant: route.params?.tenant}, query: {blueprintId: props.blueprintId, ...additionalQuery}};
    });

    const goBack = () => {
        if (props.embed) {
            emit("back");
        } else {
            router.push({
                name: "blueprints",
                params: {
                    tenant: route.params?.tenant,
                    tab: tab.value
                }
            });
        }
    };

    const loadTags = async () => {
        const data = await blueprintsStore.getBlueprintTags({
            type: (props.combinedView ? props.blueprintType : route.params?.tab) as any,
            kind: props.kind as any
        });
        tags.value = Object.fromEntries(data?.map((tag: any) => [tag.id, tag]) ?? []);
    };

    onMounted(async () => {
        const blueprintData = await blueprintsStore.getBlueprint({
            type: (props.combinedView ? props.blueprintType : route.params?.tab) as any,
            kind: props.kind as any,
            id: props.blueprintId
        });
        blueprint.value = blueprintData;

        await loadTags();

        if (props.kind === "flow") {
            flowGraph.value = route.params?.tab === "community"
                ? await blueprintsStore.getBlueprintGraph({
                    type: route.params?.tab as any,
                    kind: props.kind as any,
                    id: props.blueprintId
                })
                : await flowStore.getGraphFromSourceResponse({
                    flow: blueprint.value?.source
                });
        }
    });
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .header-wrapper {
        margin-top: calc($spacer * 2);
        margin-bottom: $spacer;

        .el-card & {
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
                margin-right: calc($spacer);
                cursor: pointer;
                border: none;
                background: var(--ks-background-card);
                display: flex;
                align-items: center;
                border-radius: 5px;
                padding: 4px 10px;
                border: 1px solid var(--ks-border-primary);
            }

            .blueprint-title {
                font-weight: 600;
                font-size: 20px;
                line-height: 30px;
                text-overflow: ellipsis;
                overflow: hidden;
            }
        }
    }

    .blueprint-container {
        height: 100%;

        :deep(.el-card) {
            .el-card__body {
                padding: 0;
            }
        }

        h4 {
            margin-top: calc($spacer * 2);
            font-weight: 600;
            font-size: 18.4px;
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
                background: var(--ks-background-card);
                border-radius: var(--bs-border-radius);
                min-width : 100px;
                width: 100px;
                height : 100px;
                padding: $spacer;
                margin-right: $spacer;
                margin-bottom: $spacer;
                display: flex;
                flex-wrap: wrap;
                border: 1px solid var(--ks-border-primary);

                :deep(.wrapper) {
                    .icon {
                        height: 100%;
                        margin: 0;
                    }

                    .hover {
                        position: static;
                        background: none;
                        border-top: 0;
                        font-size: var(--font-size-sm);
                    }

                }
            }
        }
    }

    .tags {
        margin: 10px 0;
        display: flex;

        .el-tag.el-tag--info {
            background-color: var(--ks-background-card);
            padding: 15px 10px;
            color: var(--ks-content-primary);
            text-transform: capitalize;
            font-size: var(--el-font-size-small);
            border: 1px solid var(--ks-border-primary);
        }

        .tag-box {
            margin-right: calc($spacer / 3);
        }
    }
</style>