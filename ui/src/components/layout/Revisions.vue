<template>
    <div class="revision" v-if="revisions && revisions.length > 1">
        <div class="d-flex justify-content-end">
            <KsSelect v-model="sideBySide" class="mb-3 display-select">
                <KsOption
                    v-for="item in displayTypes"
                    :key="String(item.value)"
                    :label="item.text"
                    :value="item.value"
                />
            </KsSelect>
        </div>
        <div class="revision-grid mb-2">
            <div class="revision-grid-col" v-if="revisionLeft !== undefined">
                <div class="revision-select-row">
                    <div class="revision-select">
                        <KsSelect v-model="revisionLeft" @change="addQuery">
                            <KsOption
                                v-for="item in leftOptions"
                                :key="item.value"
                                :label="$t('revision') + ' '+ item.text"
                                :value="item.value"
                                class="revision-option"
                            >
                                <div class="revision-label">
                                    <span> {{ $t("revision") + " " + item.text }}</span>
                                    <KsTag v-if="item.isDraft" size="small">
                                        <CircleOpacity />
                                        {{ $t('draft') }}
                                    </KsTag>
                                    <span class="revision-timestamp">{{ item.timestamp }}</span>
                                </div>
                                <TrashCanOutline
                                    @mousedown.stop.prevent
                                    @click.stop.prevent="onDelete(item.value)"
                                    v-if="canDelete && item.value !== undefined && currentRevision !== item.value"
                                />
                            </KsOption>
                        </KsSelect>
                        <KsButtonGroup>
                            <KsButton
                                :icon="Restore"
                                :disabled="revisionLeft === currentRevision"
                                @click="restoreRevision(revisionLeft, revisionLeftText)"
                                data-testid="restore-left"
                            >
                                <span class="d-none d-lg-inline-block">&nbsp;{{ $t("restore") }}</span>
                            </KsButton>
                        </KsButtonGroup>
                    </div>
                    <div class="revision-crud-info">
                        <slot name="crud" :revision="revisionLeft" />
                    </div>
                </div>
            </div>
            <div class="revision-grid-col" v-if="revisionRight !== undefined">
                <div class="revision-select-row">
                    <div class="revision-select">
                        <KsSelect v-model="revisionRight" @change="addQuery">
                            <KsOption
                                v-for="item in rightOptions"
                                :key="item.value"
                                :label="$t('revision') + ' '+ item.text"
                                :value="item.value"
                                class="revision-option"
                            >
                                <div class="revision-label">
                                    <span> {{ $t("revision") + " " + item.text }}</span>
                                    <KsTag v-if="item.isDraft" size="small">
                                        <CircleOpacity />
                                        {{ $t('draft') }}
                                    </KsTag>
                                    <span class="revision-timestamp">{{ item.timestamp }}</span>
                                </div>
                                <TrashCanOutline
                                    @mousedown.stop.prevent
                                    @click.stop.prevent="onDelete(item.value)"
                                    v-if="canDelete && item.value !== undefined && currentRevision !== item.value"
                                />
                            </KsOption>
                        </KsSelect>
                        <KsButtonGroup>
                            <KsButton
                                :icon="Restore"
                                :disabled="revisionRight === currentRevision"
                                @click="restoreRevision(revisionRight, revisionRightText)"
                                data-testid="restore-right"
                            >
                                <span class="d-none d-lg-inline-block">&nbsp;{{ $t("restore") }}</span>
                            </KsButton>
                        </KsButtonGroup>
                    </div>
                    <div class="revision-crud-info">
                        <slot name="crud" :revision="revisionRight" />
                    </div>
                </div>
            </div>
        </div>

        <KsEditor
            v-bind="editorBindings"
            class="mt-1"
            v-if="revisionLeftText !== undefined && revisionRightText !== undefined && !isLoadingRevisions"
            :options="{diffSideBySide: sideBySide}"
            @editorMounted="revealHighlight"
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
    <KsNoData
        v-else
        :icon="History"
        :title="$t('no revisions')"
        :description="$t('no revisions found')"
    />
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import History from "vue-material-design-icons/History.vue"
    import Restore from "vue-material-design-icons/Restore.vue"
    import TrashCanOutline from "vue-material-design-icons/TrashCanOutline.vue"
    import CircleOpacity from "vue-material-design-icons/CircleOpacity.vue"
    import {KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {date as dateFilter} from "../../utils/filters"

    import {useToast} from "../../utils/toast"
    import {useFlowStore} from "../../stores/flow"

    const flowStore = useFlowStore()

    const editorBindings = useEditorBindings()

    export interface Revision {
        revision: number;
        updated?: string;  // ISO datetime string
        source?: string;
        draft?: boolean;
    }

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const toast = useToast()

    const revisionLeft = ref<number>()
    const revisionRight = ref<number>()
    const revisionLeftText = ref<string>()
    const revisionRightText = ref<string>()
    const sideBySide = ref(true)
    const isLoadingRevisions = ref(false)
    const displayTypes = [
        {value: true, text: t("side-by-side")},
        {value: false, text: t("line-by-line")},
    ]

    const emit = defineEmits<{
        restore: [source: string],
        deleted: [revision: number]
    }>()

    const props = withDefaults(defineProps<{
        lang: string,
        revisions: Revision[],
        revisionSource: (revisionNumber: number) => Promise<string | undefined>,
        editRouteQuery?: boolean,
        canDelete?: boolean,
        highlight?: string
    }>(), {editRouteQuery: true, canDelete: true})

    const revealHighlight =(editor: any) => {
        if (!props.highlight) return

        const modified = editor?.getModifiedEditor?.() ?? editor
        const lines: string[] | undefined = modified?.getModel?.()?.getLinesContent?.()
        if (!lines) return

        const index = lines.findIndex(line => line.includes(props.highlight!))
        if (index >= 0) modified.revealLineNearTop(index + 1)
    }

    const sortedRevisions = computed(() => {
        return props.revisions.toSorted((a, b) => a.revision - b.revision)
    })

    const currentRevisionWithSource = computed(() => {
        return sortedRevisions.value[sortedRevisions.value.length - 1]
    })

    const currentRevision = computed(() => {
        return currentRevisionWithSource.value?.revision ?? 1
    })

    function revisionExists(revision: number | undefined) {
        if (revision === undefined) return false
        return sortedRevisions.value.some(rev => rev.revision === revision)
    }

    function load() {
        const current = currentRevision.value

        const queryRight = route.query.revisionRight ? parseInt(route.query.revisionRight.toString()) : undefined
        const queryLeft = route.query.revisionLeft ? parseInt(route.query.revisionLeft.toString()) : undefined

        revisionRight.value = (queryRight !== undefined && revisionExists(queryRight))
            ? queryRight
            : current
        if (queryLeft !== undefined && revisionExists(queryLeft)) {
            revisionLeft.value = queryLeft
        } else {
            const rightIdx = sortedRevisions.value.findIndex(r => r.revision === revisionRight.value)
            revisionLeft.value = rightIdx > 0 ? sortedRevisions.value[rightIdx - 1].revision : undefined
        }

        addQuery()
    }

    function formatTimestamp(updatedDate?: string): string {
        if (!updatedDate) return ""

        return dateFilter(updatedDate, "YYYY-MM-DD HH:mm")
    }

    function formatRevisionText(revision: number): string {
        let text = revision.toString()

        if (currentRevisionWithSource.value.revision === revision) {
            text += ` (${t("current")})`
        }

        return text
    }

    function restoreRevision(revision: number | undefined, revisionSource: string | undefined) {
        if (revision === undefined || revisionSource === undefined) return

        toast.confirm(t("restore confirm", {revision}), () => {
            emit("restore", revisionSource)
            return Promise.resolve()
        })
    }

    function addQuery() {
        if (isLoadingRevisions.value) return

        if (props.editRouteQuery) {
            router.push({
                query: {
                    ...route.query,
                    revisionLeft: revisionLeft.value,
                    revisionRight: revisionRight.value,
                },
            })
        }
    }

    function options(excludeRevision: number | undefined) {
        return sortedRevisions.value
            .filter(rev => rev.revision !== excludeRevision)
            .map(({revision, updated, draft}) => {
                const isCurrent = currentRevisionWithSource.value.revision === revision
                return {
                    value: revision,
                    revision: revision,
                    timestamp: formatTimestamp(updated),
                    isCurrent: isCurrent,
                    isDraft: draft === true,
                    text: formatRevisionText(revision),
                }
            })
    }

    const leftOptions = computed(() => {
        return options(revisionRight.value)
    })

    const rightOptions = computed(() => {
        return options(revisionLeft.value)
    })

    async function loadRevisionContent(revision: number | undefined) {
        if (revision === undefined) {
            return undefined
        }

        const revisionObject = sortedRevisions.value.find(r => r.revision === revision)
        if (!revisionObject) {
            return undefined
        }

        let source = revisionObject.source

        if (!source) {
            source = await props.revisionSource(revisionObject.revision)
            revisionObject.source = source
        }

        return source
    }

    async function onDelete(revision: number | undefined) {
        if (revision === undefined) return

        toast.confirm(t("delete revision confirm", {revision}), async () => {
            try {
                await flowStore.deleteRevision({
                    namespace: route.params.namespace?.toString() || "",
                    id: route.params.id?.toString() || "",
                    revision: revision.toString(),
                })
                toast.deleted(t("revision deleted", {revision: revision.toString()}))
                emit("deleted", revision)
            } catch (error: any) {
                toast.error(t("delete revision error", {revision, error: error.message || error.toString()}))
            }
        })
    }

    watch(revisionLeft, async (newValue) => {
        isLoadingRevisions.value = true
        try {
            revisionLeftText.value = await loadRevisionContent(newValue)
        } finally {
            isLoadingRevisions.value = false
        }
    })

    watch(revisionRight, async (newValue) => {
        isLoadingRevisions.value = true
        try {
            revisionRightText.value = await loadRevisionContent(newValue)
        } finally {
            isLoadingRevisions.value = false
        }
    })

    watch(() => route.query.revisionLeft, async (newValue) => {
        if (newValue) {
            const rev = parseInt(newValue.toString())
            if (revisionExists(rev) && rev !== revisionLeft.value) {
                revisionLeft.value = rev
            }
        }
    })

    watch(() => route.query.revisionRight, async (newValue) => {
        if (newValue) {
            const rev = parseInt(newValue.toString())
            if (revisionExists(rev) && rev !== revisionRight.value) {
                revisionRight.value = rev
            }
        }
    })

    watch(
        () => sortedRevisions.value.map(r => r.revision).join(","),
        async (newKey, oldKey) => {
            if (newKey === oldKey) return

            if (!revisionExists(revisionLeft.value)) {
                const rightIdx = sortedRevisions.value.findIndex(r => r.revision === revisionRight.value)
                if (rightIdx > 0) {
                    revisionLeft.value = sortedRevisions.value[rightIdx - 1].revision
                } else if (sortedRevisions.value.length > 1) {
                    revisionLeft.value = sortedRevisions.value[0].revision
                } else {
                    revisionLeft.value = undefined
                }
            }

            if (!revisionExists(revisionRight.value)) {
                revisionRight.value = currentRevisionWithSource.value?.revision
            }

            const [leftText, rightText] = await Promise.all([
                loadRevisionContent(revisionLeft.value),
                loadRevisionContent(revisionRight.value),
            ])
            revisionLeftText.value = leftText
            revisionRightText.value = rightText

            addQuery()
        },
    )


    load()
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

    .revision-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        margin-right: var(--ks-spacing-6);
    }

    .revision-grid-col {
        min-width: 0;
    }

    .revision-select-row {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 0.25rem 0.5rem;
    }

    .revision-select {
        display: flex;
        gap: 0.5rem;
        align-items: center;
        flex-shrink: 0;

        > div {
            &:first-child {
                min-width: 150px;
                width: 100%
            }
        }
    }

    .revision-crud-info {
        width: calc(100% - var(--ks-spacing-4));
        margin-right: var(--ks-spacing-4);
    }


    .revision-option {
        min-width: 350px;
        display: flex;
        align-items: center;
        justify-content: space-between;

        .revision-label {
            display: flex;
            gap: var(--ks-spacing-2);
        }
    }

    .revision-number {
        font-weight: 500;
    }

    .display-select {
        width: 10%;
    }

    .revision-timestamp {
        color: var(--ks-text-muted);
        font-size: var(--ks-font-size-sm);
        text-align: right;
        flex-shrink: 0;
    }
</style>