<template>
    <div class="revision" v-if="revisions && revisions.length > 1">
        <div class="d-flex justify-content-end">
            <el-select v-model="sideBySide" class="mb-3 display-select">
                <el-option
                    v-for="item in displayTypes"
                    :key="item.value"
                    :label="item.text"
                    :value="item.value"
                />
            </el-select>
        </div>
        <el-row :gutter="15" class="mb-2">
            <el-col :span="12" v-if="revisionLeftIndex !== undefined">
                <div class="revision-select-row">
                    <div class="revision-select">
                        <el-select v-model="revisionLeftIndex" @change="addQuery">
                            <el-option
                                v-for="item in leftOptions"
                                :key="item.value"
                                :label="$t('revision') + ' '+ item.text"
                                :value="item.value"
                                class="revision-option"
                            >
                                <div class="d-flex justify-content-between align-items-center">
                                    <span> {{ $t("revision") + " " + item.text }}</span>
                                    <span class="revision-timestamp">{{ item.timestamp }}</span>
                                    <TrashCanOutline
                                        @mousedown.stop.prevent
                                        @click.stop.prevent="onDelete(item.value)"
                                        v-if="item.value !== undefined && currentRevision != item.value"
                                    />
                                </div>
                            </el-option>
                        </el-select>
                        <el-button-group>
                            <el-button
                                :icon="Restore"
                                :disabled="revisionLeftText === currentRevisionWithSource.source"
                                @click="restoreRevision(revisionLeftIndex, revisionLeftText)"
                                data-testid="restore-left"
                            >
                                <span class="d-none d-lg-inline-block">&nbsp;{{ $t("restore") }}</span>
                            </el-button>
                        </el-button-group>
                    </div>
                    <slot name="crud" :revision="revisionNumber(revisionLeftIndex)" />
                </div>
            </el-col>
            <el-col :span="12" v-if="revisionRightIndex !== undefined">
                <div class="revision-select-row m">
                    <div class="revision-select">
                        <el-select v-model="revisionRightIndex" @change="addQuery">
                            <el-option
                                v-for="item in rightOptions"
                                :key="item.value"
                                :label="$t('revision') + ' '+ item.text"
                                :value="item.value"
                                class="revision-option"
                            >
                                <div class="d-flex justify-content-between align-items-center">
                                    <span> {{ $t("revision") + " " + item.text }}</span>
                                    <span class="revision-timestamp">{{ item.timestamp }}</span>
                                    <TrashCanOutline
                                        @mousedown.stop.prevent
                                        @click.stop.prevent="onDelete(item.value)"
                                        v-if="item.value !== undefined && currentRevision != revisionNumber(item.value)"
                                    />
                                </div>
                            </el-option>
                        </el-select>
                        <el-button-group>
                            <el-button
                                :icon="Restore"
                                :disabled="revisionRightText === currentRevisionWithSource.source"
                                @click="restoreRevision(revisionRightIndex, revisionRightText)"
                                data-testid="restore-right"
                            >
                                <span class="d-none d-lg-inline-block">&nbsp;{{ $t("restore") }}</span>
                            </el-button>
                        </el-button-group>
                    </div>
                    <slot name="crud" :revision="revisionNumber(revisionRightIndex)" />
                </div>
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
    </div>
    <div v-else>
        <el-alert class="mb-0" showIcon :closable="false">
            {{ $t("no revisions found") }}
        </el-alert>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import Restore from "vue-material-design-icons/Restore.vue";
    import TrashCanOutline from "vue-material-design-icons/TrashCanOutline.vue";
    import Editor from "../../components/inputs/Editor.vue";
    import moment from "moment";

    import {useToast} from "../../utils/toast";
    import {useFlowStore} from "../../stores/flow";

    const flowStore = useFlowStore();

    export interface Revision {
        revision: number;
        updated?: string;  // ISO datetime string
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
    const sideBySide = ref(true);
    const isLoadingRevisions = ref(false);
    const displayTypes = [
        {value: true, text: t("side-by-side")},
        {value: false, text: t("line-by-line")}
    ];

    const emit = defineEmits<{
        restore: [source: string],
        deleted: [revision: number]
    }>();

    const props = withDefaults(defineProps<{
        lang: string,
        revisions: Revision[],
        revisionSource: (revisionNumber: number) => Promise<string | undefined>,
        editRouteQuery?: boolean
    }>(), {editRouteQuery: true});

    const sortedRevisions = computed(() => {
        return props.revisions.toSorted((a, b) => a.revision - b.revision);
    });

    const currentRevisionWithSource = computed(() => {
        return sortedRevisions.value[sortedRevisions.value.length - 1];
    });

    function load() {
        const currentRevision = currentRevisionWithSource.value?.revision ?? 1;

        if (route.query.revisionRight) {
            revisionRightIndex.value = revisionIndex(
                route.query.revisionRight.toString()
            );
            if (
                !route.query.revisionLeft &&
                revisionRightIndex.value !== undefined &&
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
        } else if (revisionRightIndex.value !== undefined && revisionRightIndex.value > 0) {
            revisionLeftIndex.value = revisionRightIndex.value - 1;
        }
    }

    function revisionIndex(revision: string) {
        const revisionInt = parseInt(revision);
        const idx = sortedRevisions.value.findIndex(rev => rev.revision === revisionInt);
        return idx === -1 ? undefined : idx;
    }

    function revisionNumber(index: number) {
        return sortedRevisions.value[index].revision;
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

    function formatTimestamp(updatedDate?: string): string {
        if (!updatedDate) return "";

        return moment(updatedDate).format("YYYY-MM-DD HH:mm");
    }

    function formatRevisionText(revision: number): string {
        let text = revision.toString();

        if (currentRevisionWithSource.value.revision === revision) {
            text += ` (${t("current")})`;
        }

        return text;
    }

    function options(excludeRevisionIndex: number | undefined) {
        return sortedRevisions.value
            .filter((_, index) => index !== excludeRevisionIndex)
            .map(({revision, updated}) => {
                const isCurrent = currentRevisionWithSource.value.revision === revision;
                return {
                    value: revisionIndex(revision.toString()),
                    revision: revision,
                    timestamp: formatTimestamp(updated),
                    isCurrent: isCurrent,
                    text: formatRevisionText(revision)
                };
            });
    }

    const leftOptions = computed(() => {
        return options(revisionRightIndex.value);
    });

    const rightOptions = computed(() => {
        return options(revisionLeftIndex.value);
    });

    const currentRevision = computed(() => {
        return currentRevisionWithSource.value?.revision ?? 1
    })

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

    async function onDelete(index: number) {
        const revisionToDelete = revisionNumber(index);
        toast.confirm(t("delete revision confirm", {revision: revisionToDelete}), async () => {
            try {
                await flowStore.deleteRevision({
                    namespace: route.params.namespace?.toString() || "",
                    id: route.params.id?.toString() || "",
                    revision: revisionToDelete.toString()
                });
                toast.deleted(t("revision deleted", {revision: revisionToDelete.toString()}));
                emit("deleted", revisionToDelete);
                load();
            } catch (error: any) {
                toast.error(t("delete revision error", {revision: revisionToDelete, error: error.message || error.toString()}));
            }
        });
    };

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

    watch(() => route.query.revisionLeft, async (newValue) => {
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
    });

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

    .revision-select-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }

    .revision-select {
        display: flex;
        gap: 0.5rem;
        align-items: center;

        > div {
            &:first-child {
                min-width: 150px;
                width: 100%
            }
        }
    }

    .revision-option {
        padding-right: 0.5rem;
        min-width: 350px;
    }

    .revision-number {
        font-weight: 500;
    }

    .revision-timestamp {
        color: #888;
        font-size: 0.85em;
    }

    .display-select {
        width: 10%;
    }

    .revision-timestamp {
        color: #888;
        font-size: 0.85em;
        text-align: right;
        flex-shrink: 0;
    }

</style>
