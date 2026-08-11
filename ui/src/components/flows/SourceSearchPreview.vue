<template>
    <div class="source-search-preview" data-test="source-search-preview">
        <div v-if="!selected" class="source-search-preview__empty">
            <KsEmpty :description="$t('source_search.preview_empty')" :background="false" />
        </div>

        <div v-else-if="isLoading" class="source-search-preview__loading" v-ks-loading="true" />

        <KsAlert
            v-else-if="error"
            type="error"
            :title="$t('source_search.preview_error')"
            class="source-search-preview__error"
        />

        <template v-else-if="source !== null">
            <div class="source-search-preview__head">
                <div class="source-search-preview__title">
                    <span class="source-search-preview__namespace">{{ selected.namespace }} /</span>
                    <span class="source-search-preview__id">{{ selected.id }}.yaml</span>
                </div>
                <div class="source-search-preview__actions">
                    <span v-if="replaceMode" class="source-search-preview__summary">{{ $t('source_search.diff_preview_label') }}</span>
                    <span v-else class="source-search-preview__summary">{{ $t('source_search.line_label', {line: selected.line}) }}</span>
                    <KsButton
                        v-if="!replaceMode"
                        type="text"
                        tag="router-link"
                        :to="{path: `/flows/edit/${selected.namespace}/${selected.id}/source`}"
                    >
                        {{ $t('source_search.open_in_editor') }}
                    </KsButton>
                </div>
            </div>

            <template v-if="replaceMode">
                <div class="source-search-preview__diff" role="group" :aria-label="$t('source_search.diff_preview_aria')">
                    <div
                        v-for="(line, index) in diffLines"
                        :key="`${line.kind}-${line.line}-${index}`"
                        class="source-search-preview__eline"
                        :class="`source-search-preview__eline--${line.kind}`"
                    >
                        <span class="source-search-preview__sign">{{ line.kind === "removed" ? "−" : line.kind === "added" ? "+" : "" }}</span>
                        <span class="source-search-preview__gutter">{{ line.line }}</span>
                        <span class="source-search-preview__code">{{ line.text }}</span>
                    </div>
                </div>

                <div v-if="showConfirmBar" class="source-search-preview__confirm-bar">
                    <i18n-t
                        keypath="source_search.confirm_bar_message"
                        tag="span"
                        class="source-search-preview__confirm-msg"
                    >
                        <template #matches>
                            <b>{{ $t('source_search.match_count', {count: selectionSummary?.selectedMatchCount ?? 0}) }}</b>
                        </template>
                        <template #flows>
                            <b>{{ selectionSummary?.selectedFlowCount ?? 0 }}</b>
                        </template>
                        <template #skipped>
                            <b>{{ readOnlyExcludedCount }}</b>
                        </template>
                    </i18n-t>
                    <div class="source-search-preview__spacer" />
                    <KsButton type="text" @click="emit('cancel')">
                        {{ $t('cancel') }}
                    </KsButton>
                    <KsButton
                        type="primary"
                        :disabled="!selectionSummary || selectionSummary.selectedMatchCount === 0"
                        @click="emit('replace-all')"
                    >
                        {{ $t('source_search.replace_all') }}
                    </KsButton>
                </div>
            </template>

            <KsEditor
                v-else
                :key="editorKey"
                class="source-search-preview__editor"
                ref="editorRef"
                :modelValue="source"
                lang="yaml"
                :readOnly="true"
                :navbar="false"
                @editorMounted="applyHighlight"
            />
        </template>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {KsEditor} from "@kestra-io/design-system"
    import {useFlowStore} from "../../stores/flow"
    import type {SourceSearchReplacePreviewResponse} from "@kestra-io/kestra-sdk"
    import {buildDiffHunks, type SelectionSummary, type SourceSearchDiffMatch} from "../../utils/sourceSearchDiff"
    import type {KsEditorExposes} from "@kestra-io/design-system"

    const props = defineProps<{
        selected: {namespace: string; id: string; line: number} | null
        query: string
        replaceMode: boolean
        previewResponse: SourceSearchReplacePreviewResponse | null
        selectionSummary: SelectionSummary | null
        readOnlyExcludedCount: number
    }>()

    const emit = defineEmits<{
        (e: "cancel"): void
        (e: "replace-all"): void
    }>()
    const flowStore = useFlowStore()

    const isLoading = ref(false)
    const error = ref(false)
    const source = ref<string | null>(null)
    const editorRef = ref<KsEditorExposes | null>(null)

    const editorKey = computed(() => props.selected ? `${props.selected.namespace}/${props.selected.id}` : "")

    const showConfirmBar = computed(() => Boolean(props.selectionSummary))

    const currentFlowPreview = computed(() => {
        if (!props.selected || !props.previewResponse) return null
        return props.previewResponse.flows?.find((flow) => flow.namespace === props.selected!.namespace && flow.id === props.selected!.id) ?? null
    })

    const diffLines = computed(() => {
        if (!currentFlowPreview.value || source.value === null) return []
        return buildDiffHunks(source.value.split("\n"), (currentFlowPreview.value.matches ?? []) as SourceSearchDiffMatch[])
    })

    let activeDecoration: {clear: () => void} | null = null

    function applyHighlight() {
        if (!props.selected) return
        const editor = editorRef.value?.getEditor?.() as any
        if (!editor) return
        const line = props.selected.line
        activeDecoration?.clear()
        activeDecoration = editor.createDecorationsCollection([
            {range: {startLineNumber: line, startColumn: 1, endLineNumber: line, endColumn: 1}, options: {isWholeLine: true, className: "source-search-preview__match-line"}},
        ])
        editor.revealLineInCenter?.(line)
    }

    watch(
        () => props.selected,
        async (sel, old, onCleanup) => {
            let cancelled = false
            onCleanup(() => {
                cancelled = true
            })

            if (!sel) {
                source.value = null
                error.value = false
                activeDecoration = null
                return
            }

            if (old && old.namespace === sel.namespace && old.id === sel.id) {
                if (!props.replaceMode) applyHighlight()
                return
            }

            isLoading.value = true
            error.value = false
            source.value = null

            try {
                const flow = await flowStore.loadFlow({namespace: sel.namespace, id: sel.id, store: false})
                if (cancelled) return
                activeDecoration = null
                source.value = flow?.source ?? null
                isLoading.value = false
            } catch {
                if (cancelled) return
                error.value = true
                isLoading.value = false
            }
        },
        {immediate: true},
    )

    watch(
        () => props.selected?.line,
        () => {
            if (source.value && props.selected && !props.replaceMode) applyHighlight()
        },
    )
</script>

<style scoped lang="scss">
.source-search-preview {
    height: 100%;
    display: flex;
    flex-direction: column;
}

.source-search-preview__empty,
.source-search-preview__error {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    padding: var(--ks-spacing-4);
}

.source-search-preview__loading {
    height: 100%;
}

.source-search-preview__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-2) var(--ks-spacing-3);
    border-bottom: 1px solid var(--ks-border-default);
    flex: 0 0 auto;
}

.source-search-preview__title {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-sm);
    min-width: 0;
}

.source-search-preview__namespace {
    color: var(--ks-text-muted);
}

.source-search-preview__id {
    color: var(--ks-text-primary);
    font-weight: 600;
}

.source-search-preview__actions {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    flex: 0 0 auto;
}

.source-search-preview__summary {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-muted);
    white-space: nowrap;
}

.source-search-preview__editor {
    flex: 1;
    min-height: 0;
}

.source-search-preview__diff {
    flex: 1;
    min-height: 0;
    overflow: auto;
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-sm);
    background: var(--ks-bg-elevated);
}

.source-search-preview__eline {
    display: flex;
    align-items: flex-start;
}

.source-search-preview__eline--removed {
    background: var(--ks-status-background-failed);
}

.source-search-preview__eline--removed .source-search-preview__sign {
    color: var(--ks-status-error);
}

.source-search-preview__eline--added {
    background: var(--ks-status-background-success);
}

.source-search-preview__eline--added .source-search-preview__sign {
    color: var(--ks-status-success);
}

.source-search-preview__sign {
    flex: 0 0 auto;
    width: var(--ks-spacing-5);
    text-align: center;
    color: var(--ks-text-muted);
}

.source-search-preview__gutter {
    flex: 0 0 auto;
    width: 3.25rem;
    text-align: right;
    padding-inline-end: var(--ks-spacing-3);
    color: var(--ks-text-muted);
    user-select: none;
}

.source-search-preview__code {
    flex: 1 1 auto;
    white-space: pre;
    padding-inline-end: var(--ks-spacing-4);
    color: var(--ks-text-dim);
}

.source-search-preview__confirm-bar {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    border-top: 1px solid var(--ks-border-default);
    flex: 0 0 auto;
}

.source-search-preview__confirm-msg {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-secondary);
}

.source-search-preview__spacer {
    flex: 1 1 auto;
}

:global(.source-search-preview__match-line) {
    background: var(--ks-bg-active);
}
</style>
