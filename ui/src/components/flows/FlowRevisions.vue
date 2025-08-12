<template>
    <div class="flow-revision" v-if="revisions && revisions.length > 1">
        <el-select v-model="sideBySide" class="mb-3">
            <el-option
                v-for="item in displayTypes"
                :key="item.value"
                :label="item.text"
                :value="item.value"
            />
        </el-select>
        <el-row :gutter="15">
            <el-col :span="12" v-if="revisionLeftIndex !== undefined">
                <div class="revision-select mb-3">
                    <el-select v-model="revisionLeftIndex" @change="addQuery">
                        <el-option
                            v-for="item in options(revisionRightIndex)"
                            :key="item.value"
                            :label="item.text"
                            :value="item.value"
                        />
                    </el-select>
                    <el-button-group>
                        <el-button :icon="FileCode" @click="seeRevision(revisionLeftIndex, revisionLeftText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ t('see full revision') }}</span>
                        </el-button>
                        <el-button :icon="Restore" :disabled="revisionNumber(revisionLeftIndex) === flow.revision" @click="restoreRevision(revisionLeftIndex, revisionLeftText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ t('restore') }}</span>
                        </el-button>
                    </el-button-group>
                </div>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: route.params.namespace, flowId: route.params.id, revision: revisionNumber(revisionLeftIndex)}" />
            </el-col>
            <el-col :span="12" v-if="revisionRightIndex !== undefined">
                <div class="revision-select mb-3">
                    <el-select v-model="revisionRightIndex" @change="addQuery">
                        <el-option
                            v-for="item in options(revisionLeftIndex)"
                            :key="item.value"
                            :label="item.text"
                            :value="item.value"
                        />
                    </el-select>
                    <el-button-group>
                        <el-button :icon="FileCode" @click="seeRevision(revisionRightIndex, revisionRightText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ t('see full revision') }}</span>
                        </el-button>
                        <el-button :icon="Restore" :disabled="revisionNumber(revisionRightIndex) === flow.revision" @click="restoreRevision(revisionRightIndex, revisionRightText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ t('restore') }}</span>
                        </el-button>
                    </el-button-group>
                </div>

                <crud class="mt-3" permission="FLOW" :detail="{namespace: route.params.namespace, flowId: route.params.id, revision: revisionNumber(revisionRightIndex)}" />
            </el-col>
        </el-row>

        <editor
            class="mt-1"
            v-if="revisionLeftText && revisionRightText && !isLoadingRevisions"
            :diff-side-by-side="sideBySide"
            :model-value="revisionRightText"
            :original="revisionLeftText"
            read-only
            lang="yaml"
            :show-doc="false"
        />

        <div v-if="isLoadingRevisions" class="text-center p-4">
            <span class="ml-2">Loading revisions...</span>
        </div>

        <drawer v-if="isModalOpen" v-model="isModalOpen">
            <template #header>
                <h5>{{ t("revision") + `: ` + revision }}</h5>
            </template>

            <editor v-model="revisionYaml" lang="yaml" :full-height="false" :input="true" :navbar="false" :read-only="true" />
        </drawer>
    </div>
    <div v-else>
        <el-alert class="mb-0" show-icon :closable="false">
            {{ t('no revisions found') }}
        </el-alert>
    </div>
</template>

<script lang="ts" setup>
    import {ref, computed, watch} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import FileCode from "vue-material-design-icons/FileCode.vue";
    import Restore from "vue-material-design-icons/Restore.vue";
    import Editor from "../../components/inputs/Editor.vue";
    import Crud from "override/components/auth/Crud.vue";
    import Drawer from "../Drawer.vue";

    import {saveFlowTemplate} from "../../utils/flowTemplate";
    import {useToast} from "../../utils/toast";
    import {useFlowStore} from "../../stores/flow";

    interface Revision {
        revision: number;
        source?: string;
    }

    const {t} = useI18n();
    const store = useStore();
    const route = useRoute();
    const router = useRouter();
    const toast = useToast();

    const revisionLeftIndex = ref();
    const revisionRightIndex = ref();
    const revisionLeftText = ref();
    const revisionRightText = ref();
    const revision = ref();
    const revisions = ref<(Revision)[]>([]);
    const revisionId = ref();
    const revisionYaml = ref();
    const sideBySide = ref(true);
    const isLoadingRevisions = ref(false);
    const displayTypes = [
        {value: true, text: t("side-by-side")},
        {value: false, text:  t("line-by-line")},
    ];
    const isModalOpen = ref(false);

    const flowStore = useFlowStore();
    const flow = computed(() => flowStore.flow);

    function load() {
        if (!flow.value) {
            return;
        }
        const currentRevision = flow.value.revision;

        revisions.value = [...Array(currentRevision).keys()].map(((_, i) => {
            if (currentRevision === revisionNumber(i) && flow.value?.revision !== undefined && flow.value?.source) {
                const val = flow.value as Revision
                return val;
            }

            if(revisions.value[i] && revisions.value[i].revision === i + 1) {
                return revisions.value[i];
            }

            return {revision: i + 1};
        }));

        if (route.query.revisionRight) {
            revisionRightIndex.value = revisionIndex(
                route.query.revisionRight.toString()
            );
            if (
                !route.query.revisionLeft &&
                revisionRightIndex.value > 0
            ) {
                revisionLeftIndex.value = revisionRightIndex.value - 1;
            }
        } else if (currentRevision > 0) {
            revisionRightIndex.value = currentRevision - 1;
        }

        if (route.query.revisionLeft) {
            revisionLeftIndex.value = revisionIndex(
                route.query.revisionLeft.toString()
            );
        } else if (currentRevision > 1) {
            revisionLeftIndex.value = currentRevision - 2;
        }
    }

    function revisionIndex(revision: string) {
        const revisionInt = parseInt(revision);

        if (revisionInt < 1 || revisionInt > revisions.value?.length) {
            return -1;
        }

        return revisionInt - 1;
    }

    function revisionNumber(index: number) {
        return index + 1;
    }

    function seeRevision(index: number, revisionParam: Revision) {
        revisionId.value = index
        revisionYaml.value = revisionParam
        revision.value = revisionNumber(index)
        isModalOpen.value = true;
    }

    function restoreRevision(index: number, revisionSource: string) {
        toast.confirm(t("restore confirm", {revision: revisionNumber(index)}), () => {
            return saveFlowTemplate({
                $store: store,
                $toast: () => toast,
            }, revisionSource, "flow")
                .then((response:any) => {
                    flowStore.flowYaml = response.source;
                    flowStore.flowYamlBeforeAdd = response.source;
                    load()
                })
                .then(() => {
                    router.push({query: {}});
                });
        })
    }

    function addQuery() {
        if (isLoadingRevisions.value) {
            return;
        }

        router.push({query: {
            ...route.query,
            revisionLeft: revisionLeftIndex.value + 1,
            revisionRight: revisionRightIndex.value + 1
        }});
    }

    async function fetchRevision(revision: string) {
        const revisionFetched = await flowStore.loadFlow({
            namespace: flow.value?.namespace ?? "",
            id: flow.value?.id ?? "",
            revision,
            allowDeleted: true,
            store: false
        });
        revisions.value[revisionIndex(revision)] = revisionFetched;

        return revisionFetched;
    }

    function options(excludeRevisionIndex: number | undefined) {
        return revisions.value
            .filter((_, index) => index !== excludeRevisionIndex)
            .map(({revision}) => ({value: revisionIndex(revision.toString()), text: revision}));
    }

    async function loadRevisionContent(index: number | undefined) {
        if (index === undefined) {
            return undefined;
        }

        const revisionObject = revisions.value[index];
        let source = revisionObject.source;

        if (!source) {
            source = (await fetchRevision(revisionObject.revision.toString())).source;
        }

        return source;
    }

    watch(revisionLeftIndex, async (newValue) => {
        isLoadingRevisions.value = true;
        try {
            revisionLeftText.value = await loadRevisionContent(newValue);
        } finally {
            isLoadingRevisions.value = false;
        }
    });

    watch(revisionRightIndex, async (newValue) => {
        isLoadingRevisions.value = true;
        try {
            revisionRightText.value = await loadRevisionContent(newValue);
        } finally {
            isLoadingRevisions.value = false;
        }
    });

    watch(() => route.query,
          (newQuery, oldQuery) => {
              if (newQuery.revisionLeft !== oldQuery.revisionLeft && newQuery.revisionLeft) {
                  const newLeftIndex = revisionIndex(newQuery.revisionLeft.toString());
                  if (newLeftIndex !== revisionLeftIndex.value) {
                      revisionLeftIndex.value = newLeftIndex;
                  }
              }

              if (newQuery.revisionRight !== oldQuery.revisionRight && newQuery.revisionRight) {
                  const newRightIndex = revisionIndex(newQuery.revisionRight.toString());
                  if (newRightIndex !== revisionRightIndex.value) {
                      revisionRightIndex.value = newRightIndex;
                  }
              }
          },
          {deep: true}
    )

    load();
</script>

<style scoped lang="scss">
    .flow-revision {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 100vh;
    }

    .ks-editor {
        flex: 1;
        padding-bottom: 1rem;
    }

    .revision-select {
        display: flex;

        > div {
            &:first-child {
                flex: 2;
            }
        }
    }
</style>
