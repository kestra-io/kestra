<template>
    <div class="revision" v-if="revisions && revisions.length > 1">
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
                            v-for="item in leftOptions"
                            :key="item.value"
                            :label="item.text"
                            :value="item.value"
                        />
                    </el-select>
                    <el-button-group>
                        <el-button :icon="FileCode" @click="seeRevision(revisionLeftIndex, revisionLeftText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ t('see full revision') }}</span>
                        </el-button>
                        <el-button
                            :icon="Restore"
                            :disabled="revisionLeftText === currentRevisionWithSource.source"
                            @click="restoreRevision(revisionLeftIndex, revisionLeftText)"
                            data-testid="restore-left"
                        >
                            <span class="d-none d-lg-inline-block">&nbsp;{{ t('restore') }}</span>
                        </el-button>
                    </el-button-group>
                </div>

                <slot name="crud" :revision="revisionNumber(revisionLeftIndex)" />
            </el-col>
            <el-col :span="12" v-if="revisionRightIndex !== undefined">
                <div class="revision-select mb-3">
                    <el-select v-model="revisionRightIndex" @change="addQuery">
                        <el-option
                            v-for="item in rightOptions"
                            :key="item.value"
                            :label="item.text"
                            :value="item.value"
                        />
                    </el-select>
                    <el-button-group>
                        <el-button :icon="FileCode" @click="seeRevision(revisionRightIndex, revisionRightText)">
                            <span class="d-none d-lg-inline-block">&nbsp;{{ t('see full revision') }}</span>
                        </el-button>
                        <el-button
                            :icon="Restore"
                            :disabled="revisionRightText === currentRevisionWithSource.source"
                            @click="restoreRevision(revisionRightIndex, revisionRightText)"
                            data-testid="restore-right"
                        >
                            <span class="d-none d-lg-inline-block">&nbsp;{{ t('restore') }}</span>
                        </el-button>
                    </el-button-group>
                </div>

                <slot name="crud" :revision="revisionNumber(revisionRightIndex)" />
            </el-col>
        </el-row>

        <Editor
            class="mt-1"
            v-if="revisionLeftText !== undefined && revisionRightText !== undefined && !isLoadingRevisions"
            :diffSideBySide="sideBySide"
            :modelValue="revisionRightText"
            :original="revisionLeftText"
            readOnly
            :lang
            :showDoc="false"
        />

        <div v-if="isLoadingRevisions" class="text-center p-4">
            <span class="ml-2">Loading revisions...</span>
        </div>

        <Drawer v-if="isModalOpen" v-model="isModalOpen">
            <template #header>
                <h5>{{ t("revision") + `: ` + revision }}</h5>
            </template>

            <Editor v-model="revisionContent" :lang :fullHeight="false" :input="true" :navbar="false" :readOnly="true" />
        </Drawer>
    </div>
    <div v-else>
        <el-alert class="mb-0" showIcon :closable="false">
            {{ t('no revisions found') }}
        </el-alert>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import FileCode from "vue-material-design-icons/FileCode.vue";
    import Restore from "vue-material-design-icons/Restore.vue";
    import Editor from "../../components/inputs/Editor.vue";
    import Drawer from "../Drawer.vue";

    import {useToast} from "../../utils/toast";

    export interface Revision {
        revision: number;
        source?: string;
    }

    const {t} = useI18n();
    const route = useRoute();
    const router = useRouter();
    const toast = useToast();

    const revisionLeftIndex = ref();
    const revisionRightIndex = ref();
    const revisionLeftText = ref();
    const revisionRightText = ref();
    const revision = ref();
    const revisionId = ref();
    const revisionContent = ref();
    const sideBySide = ref(true);
    const isLoadingRevisions = ref(false);
    const displayTypes = [
        {value: true, text: t("side-by-side")},
        {value: false, text:  t("line-by-line")},
    ];
    const isModalOpen = ref(false);

    const emit = defineEmits<{
        restore: [source: string]
    }>();

    const props = withDefaults(defineProps<{
        lang: string,
        revisions: Revision[],
        revisionSource: (revisionNumber: number) => Promise<string>,
        editRouteQuery?: boolean
    }>(), {editRouteQuery: true});

    const sortedRevisions = computed(() => {
        return props.revisions.toSorted((a, b) => a.revision - b.revision)
    });

    const currentRevisionWithSource = computed(() => {
        return sortedRevisions.value[sortedRevisions.value.length - 1];
    })

    function load() {
        const currentRevision = currentRevisionWithSource.value?.revision ?? 1;

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
        } else if (currentRevision && currentRevision > 0) {
            revisionRightIndex.value = revisionIndex(currentRevision.toString());
        }

        if (route.query.revisionLeft) {
            revisionLeftIndex.value = revisionIndex(
                route.query.revisionLeft.toString()
            );
        } else if (revisionRightIndex.value && revisionRightIndex.value > 0) {
            revisionLeftIndex.value = revisionRightIndex.value - 1;
        }
    }

    function revisionIndex(revision: string) {
        const revisionInt = parseInt(revision);

        return sortedRevisions.value.findIndex(rev => rev.revision === revisionInt)
    }

    function revisionNumber(index: number) {
        return sortedRevisions.value[index].revision;
    }

    function seeRevision(index: number, revisionParam: Revision) {
        revisionId.value = index
        revisionContent.value = revisionParam
        revision.value = revisionNumber(index)
        isModalOpen.value = true;
    }

    function restoreRevision(index: number, revisionSource: string) {
        toast.confirm(t("restore confirm", {revision: revisionNumber(index)}), () => {
            emit("restore", revisionSource);
            return Promise.resolve();
        });
    }

    function addQuery() {
        if (isLoadingRevisions.value) {
            return;
        }

        if (props.editRouteQuery) {
            router.push({
                query: {
                    ...route.query,
                    revisionLeft: sortedRevisions.value[revisionLeftIndex.value].revision,
                    revisionRight: sortedRevisions.value[revisionRightIndex.value].revision
                }
            });
        }
    }

    function options(excludeRevisionIndex: number | undefined) {
        return sortedRevisions.value
            .filter((_, index) => index !== excludeRevisionIndex)
            .map(({revision}) => ({value: revisionIndex(revision.toString()), text: revision + (currentRevisionWithSource.value.revision === revision ? ` (${t("current")})` : "")}));
    }

    const leftOptions = computed(() => {
        return options(revisionRightIndex.value);
    });

    const rightOptions = computed(() => {
        return options(revisionLeftIndex.value);
    });

    async function loadRevisionContent(index: number | undefined) {
        if (index === undefined) {
            return undefined;
        }

        const revisionObject = sortedRevisions.value[index];
        let source = revisionObject.source;

        if (!source) {
            source = await props.revisionSource(revisionObject.revision);
            revisionObject.source = source;
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

    watch (() => route.query.revisionLeft, async (newValue) => {
        if (newValue) {
            const newLeftIndex = revisionIndex(newValue.toString());
            if (newLeftIndex !== revisionLeftIndex.value) {
                revisionLeftIndex.value = newLeftIndex;
            }
        }
    });

    watch(() => route.query.revisionRight, async (newValue) => {
        if (newValue) {
            const newRightIndex = revisionIndex(newValue.toString());
            if (newRightIndex !== revisionRightIndex.value) {
                revisionRightIndex.value = newRightIndex;
            }
        }
    });

    watch(() => currentRevisionWithSource.value.revision, (newRevision, oldRevision) => {
        if (revisionNumber(revisionRightIndex.value) === oldRevision) {
            revisionRightIndex.value = revisionIndex(newRevision.toString());
        }
    })

    load();
</script>

<style scoped lang="scss">
    .revision {
        display: flex;
        flex-direction: column;
        height: 100%;
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
