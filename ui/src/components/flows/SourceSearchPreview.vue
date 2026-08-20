<template>
    <div class="source-search-preview" data-test="source-search-preview">
        <div v-if="!selection" class="source-search-preview__empty">
            <KsEmpty :description="$t('source_search.preview_empty')" :background="false" />
        </div>

        <div v-else-if="selection.type === 'flows'" class="source-search-preview__flow">
            <div v-if="isLoading" class="source-search-preview__loading" v-ks-loading="true" />

            <KsAlert
                v-else-if="error"
                type="error"
                :title="$t('source_search.preview_error')"
                class="source-search-preview__error"
            />

            <template v-else-if="source !== null">
                <div class="source-search-preview__head">
                    <div class="source-search-preview__title">
                        <FileTreeOutline class="source-search-preview__title-icon" />
                        <span class="source-search-preview__namespace">{{ selection.namespace }} /</span>
                        <span class="source-search-preview__id">{{ selection.id }}.yaml</span>
                    </div>
                    <div class="source-search-preview__actions">
                        <span v-if="replaceMode" class="source-search-preview__summary">{{ $t('source_search.diff_preview_label') }}</span>
                        <span v-else class="source-search-preview__summary">{{ $t('source_search.line_label', {line: selection.line}) }}</span>
                        <KsButton
                            v-if="!replaceMode"
                            tag="router-link"
                            :to="{path: `/flows/edit/${selection.namespace}/${selection.id}/source`}"
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
                        <span class="source-search-preview__confirm-msg">
                            <i18n-t keypath="source_search.confirm_bar_message" tag="span">
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
                            <span v-if="excludedFromReplaceCount > 0" class="source-search-preview__confirm-excluded">
                                {{ $t('source_search.confirm_bar_excluded', {count: excludedFromReplaceCount}) }}
                            </span>
                        </span>
                        <div class="source-search-preview__spacer" />
                        <KsButton @click="emit('cancel')">
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

        <div v-else class="source-search-preview__meta" data-test="source-search-preview-meta">
            <div class="source-search-preview__head">
                <div class="source-search-preview__title">
                    <component :is="metaIcon" class="source-search-preview__title-icon" />
                    <span class="source-search-preview__id">{{ metaTitle }}</span>
                </div>
            </div>
            <div class="source-search-preview__meta-body">
                <div class="source-search-preview__meta-hero">
                    <span class="source-search-preview__meta-glyph">
                        <component :is="metaGlyph" />
                    </span>
                    <div>
                        <div class="source-search-preview__meta-name" v-html="metaNameHtml" />
                        <div class="source-search-preview__meta-sub">{{ selection.namespace }}</div>
                    </div>
                </div>

                <dl v-if="metaRows.length > 0" class="source-search-preview__meta-list">
                    <template v-for="row in metaRows" :key="row.label">
                        <dt>{{ row.label }}</dt>
                        <dd :class="{'source-search-preview__meta-withheld': row.withheld}">
                            <EyeOff v-if="row.withheld && selection.type === 'kv'" />
                            <Lock v-if="row.withheld && selection.type === 'secrets'" />
                            {{ row.value }}
                        </dd>
                    </template>
                </dl>

                <div class="source-search-preview__notice" :class="{'source-search-preview__notice--neutral': selection.type !== 'files'}">
                    <span class="source-search-preview__notice-icon">
                        <Lock v-if="selection.type === 'secrets'" />
                        <InformationOutline v-else />
                    </span>
                    <span>{{ metaNotice }}</span>
                </div>

                <div class="source-search-preview__meta-actions">
                    <KsButton tag="router-link" :to="metaOpenTarget">
                        <OpenInNew />
                        {{ metaOpenLabel }}
                    </KsButton>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsEditor} from "@kestra-io/design-system"
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import DatabaseOutline from "vue-material-design-icons/DatabaseOutline.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import Lock from "vue-material-design-icons/Lock.vue"
    import EyeOff from "vue-material-design-icons/EyeOff.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import {useFlowStore} from "../../stores/flow"
    import type {SourceSearchReplacePreviewResponse} from "@kestra-io/kestra-sdk"
    import {buildDiffHunks, type SelectionSummary, type SourceSearchDiffMatch} from "../../utils/sourceSearchDiff"
    import {buildHighlightHtml, buildTermHighlightHtml, buildPathSegments, type CrossSearchSelection} from "../../utils/crossResourceSearch"
    import type {KvMatchEntry} from "../../stores/crossResourceSearch"
    import type {KsEditorExposes} from "@kestra-io/design-system"
    import _escape from "lodash/escape"

    const props = defineProps<{
        selection: CrossSearchSelection | null
        query: string
        caseSensitive: boolean
        replaceMode: boolean
        previewResponse: SourceSearchReplacePreviewResponse | null
        selectionSummary: SelectionSummary | null
        readOnlyExcludedCount: number
        excludedFromReplaceCount?: number
        kvEntry?: KvMatchEntry | null
    }>()

    const emit = defineEmits<{
        (e: "cancel"): void
        (e: "replace-all"): void
    }>()

    const {t} = useI18n()
    const flowStore = useFlowStore()

    const isLoading = ref(false)
    const error = ref(false)
    const source = ref<string | null>(null)
    const editorRef = ref<KsEditorExposes | null>(null)

    const flowSelection = computed(() => props.selection?.type === "flows" ? props.selection : null)

    const editorKey = computed(() => flowSelection.value ? `${flowSelection.value.namespace}/${flowSelection.value.id}` : "")

    const excludedFromReplaceCount = computed(() => props.excludedFromReplaceCount ?? 0)

    const showConfirmBar = computed(() => Boolean(props.selectionSummary))

    const currentFlowPreview = computed(() => {
        if (!flowSelection.value || !props.previewResponse) return null
        return props.previewResponse.flows?.find((flow) => flow.namespace === flowSelection.value!.namespace && flow.id === flowSelection.value!.id) ?? null
    })

    const diffLines = computed(() => {
        if (!currentFlowPreview.value || source.value === null) return []
        return buildDiffHunks(source.value.split("\n"), (currentFlowPreview.value.matches ?? []) as SourceSearchDiffMatch[])
    })

    let activeDecoration: {clear: () => void} | null = null

    function applyHighlight() {
        if (!flowSelection.value) return
        const editor = editorRef.value?.getEditor?.() as any
        if (!editor) return
        const line = flowSelection.value.line
        activeDecoration?.clear()
        activeDecoration = editor.createDecorationsCollection([
            {range: {startLineNumber: line, startColumn: 1, endLineNumber: line, endColumn: 1}, options: {isWholeLine: true, className: "source-search-preview__match-line"}},
        ])
        editor.revealLineInCenter?.(line)
    }

    watch(
        flowSelection,
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
        () => flowSelection.value?.line,
        () => {
            if (source.value && flowSelection.value && !props.replaceMode) applyHighlight()
        },
    )

    const metaIcon = computed(() => {
        switch (props.selection?.type) {
        case "files": return FileTreeOutline
        case "kv": return DatabaseOutline
        case "secrets": return LockOutline
        default: return null
        }
    })

    const metaGlyph = computed(() => {
        switch (props.selection?.type) {
        case "files": return FileDocumentOutline
        case "kv": return DatabaseOutline
        case "secrets": return Lock
        default: return null
        }
    })

    const metaTitle = computed(() => {
        if (!props.selection) return ""
        if (props.selection.type === "files") return props.selection.path.split("/").pop()
        if (props.selection.type === "kv" || props.selection.type === "secrets") return props.selection.key
        return ""
    })

    const metaNameHtml = computed(() => {
        if (!props.selection) return ""
        if (props.selection.type === "files") {
            return buildPathSegments(props.selection.path, props.query, props.caseSensitive)
                .map((segment) => {
                    const text = segment.matched ? `<mark>${_escape(segment.text)}</mark>` : _escape(segment.text)
                    return segment.dim ? `<span class="source-search-preview__meta-dir">${text}</span>` : text
                })
                .join("")
        }
        if (props.selection.type === "kv" || props.selection.type === "secrets") {
            return props.selection.type === "kv"
                ? buildTermHighlightHtml(props.selection.key, props.query, props.caseSensitive)
                : buildHighlightHtml(props.selection.key, props.query, props.caseSensitive)
        }
        return ""
    })

    const metaRows = computed(() => {
        if (!props.selection) return []
        if (props.selection.type === "kv") {
            const rows: {label: string; value: string; withheld?: boolean}[] = []
            if (props.kvEntry?.creationDate) rows.push({label: t("source_search.meta_created"), value: props.kvEntry.creationDate})
            if (props.kvEntry?.updateDate) rows.push({label: t("source_search.meta_updated"), value: props.kvEntry.updateDate})
            rows.push({label: t("source_search.meta_expires"), value: props.kvEntry?.expirationDate ?? t("source_search.meta_never")})
            rows.push({label: t("value"), value: t("source_search.value_withheld"), withheld: true})
            return rows
        }
        if (props.selection.type === "secrets") {
            return [{label: t("value"), value: t("source_search.value_never_shown"), withheld: true}]
        }
        return []
    })

    const metaNotice = computed(() => {
        switch (props.selection?.type) {
        case "files": return t("source_search.file_match_notice")
        case "kv": return t("source_search.kv_match_notice")
        case "secrets": return t("source_search.secret_match_notice")
        default: return ""
        }
    })

    const metaOpenLabel = computed(() => {
        switch (props.selection?.type) {
        case "files": return t("source_search.open_in_editor")
        case "kv": return t("source_search.open_in_kv")
        case "secrets": return t("source_search.open_in_secrets")
        default: return ""
        }
    })

    const metaOpenTarget = computed(() => {
        if (!props.selection) return {}
        if (props.selection.type === "files") return {name: "namespaces/update/files", params: {id: props.selection.namespace}}
        if (props.selection.type === "kv") return {name: "kv/list"}
        if (props.selection.type === "secrets") return {name: "secrets/list"}
        return {}
    })
</script>

<style scoped lang="scss">
.source-search-preview {
    height: 100%;
    display: flex;
    flex-direction: column;
}

.source-search-preview__flow,
.source-search-preview__meta {
    height: 100%;
    display: flex;
    flex-direction: column;
    min-height: 0;
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
    gap: var(--ks-spacing-2);
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-sm);
    min-width: 0;
}

.source-search-preview__title-icon {
    color: var(--ks-icon-muted);
    flex: 0 0 auto;
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
    flex-wrap: wrap;
}

.source-search-preview__confirm-msg {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-secondary);
    flex: 1 1 18rem;
}

.source-search-preview__confirm-excluded {
    display: block;
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-xs);
    margin-top: var(--ks-spacing-1);
}

.source-search-preview__spacer {
    flex: 1 1 auto;
}

.source-search-preview__meta-body {
    flex: 1 1 auto;
    min-height: 0;
    overflow: auto;
    padding: var(--ks-spacing-4);
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-4);
}

.source-search-preview__meta-hero {
    display: flex;
    align-items: flex-start;
    gap: var(--ks-spacing-3);
}

.source-search-preview__meta-glyph {
    width: 2.5rem;
    height: 2.5rem;
    flex: 0 0 auto;
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-base);
    border: 1px solid var(--ks-border-default);
    color: var(--ks-icon-default);
    display: flex;
    align-items: center;
    justify-content: center;
}

.source-search-preview__meta-name {
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-base);
    color: var(--ks-text-primary);
    font-weight: 600;
    overflow-wrap: anywhere;

    :deep(mark) {
        background-color: var(--ks-status-background-warning);
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-xs);
        padding: 0 var(--ks-spacing-1);
    }
}

:global(.source-search-preview__meta-dir) {
    color: var(--ks-text-secondary);
}

.source-search-preview__meta-sub {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-secondary);
    margin-top: var(--ks-spacing-1);
}

.source-search-preview__meta-list {
    margin: 0;
    display: grid;
    grid-template-columns: max-content 1fr;
    gap: var(--ks-spacing-2) var(--ks-spacing-4);
    font-size: var(--ks-font-size-sm);

    dt {
        color: var(--ks-text-secondary);
    }

    dd {
        margin: 0;
        color: var(--ks-text-primary);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-sm);
        overflow-wrap: anywhere;
    }
}

.source-search-preview__meta-withheld {
    color: var(--ks-text-secondary) !important;
    font-family: var(--ks-font-family-sans) !important;
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-2);
}

.source-search-preview__notice {
    display: flex;
    align-items: flex-start;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-3);
    border-radius: var(--ks-radius-base);
    background: var(--ks-bg-info);
    border: 1px solid var(--ks-border-info);
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
}

.source-search-preview__notice--neutral {
    background: var(--ks-bg-base);
    border-color: var(--ks-border-default);
    color: var(--ks-text-secondary);
}

.source-search-preview__notice-icon {
    flex: 0 0 auto;
    color: var(--ks-status-info);
    padding-top: 0.1rem;
}

.source-search-preview__notice--neutral .source-search-preview__notice-icon {
    color: var(--ks-icon-muted);
}

.source-search-preview__meta-actions {
    display: flex;
    gap: var(--ks-spacing-2);
    flex-wrap: wrap;
}

:global(.source-search-preview__match-line) {
    background: var(--ks-bg-active);
}
</style>
