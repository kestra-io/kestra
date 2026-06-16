<template>
    <div class="source-search-preview" data-test="source-search-preview">
        <div v-if="!props.selected" class="source-search-preview__empty">
            <KsEmpty :description="t('source_search.preview_empty')" />
        </div>

        <div v-else-if="isLoading" class="source-search-preview__loading" v-ks-loading="true" />

        <KsAlert
            v-else-if="error"
            type="error"
            :title="t('source_search.preview_error')"
            class="source-search-preview__error"
        />

        <KsEditor
            v-else-if="source"
            class="source-search-preview__editor"
            ref="editorRef"
            :modelValue="source"
            lang="yaml"
            :readOnly="true"
            :navbar="false"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, watch, nextTick} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsEditor} from "@kestra-io/design-system"
    import {useFlowStore} from "../../stores/flow"
    import type {KsEditorExposes} from "@kestra-io/design-system"

    const props = defineProps<{
        selected: {namespace: string; id: string; matchIndex: number} | null
        query: string
    }>()

    const {t} = useI18n()
    const flowStore = useFlowStore()

    const isLoading = ref(false)
    const error = ref(false)
    const source = ref<string | null>(null)
    const editorRef = ref<KsEditorExposes | null>(null)

    let activeDecoration: ReturnType<typeof createDecoration> | null = null

    function createDecoration(editor: any, range: any) {
        return editor.createDecorationsCollection([
            {range, options: {isWholeLine: true, className: "source-search-preview__match-line"}},
        ])
    }

    function highlightMatch(matchIndex: number) {
        if (!props.query) return
        const editor = editorRef.value?.getEditor?.()
        if (!editor) return
        const model = (editor as any).getModel?.()
        if (!model) return
        const matches = model.findMatches(props.query, false, false, false, null, false)
        if (!matches?.length) return
        const m = matches[Math.min(matchIndex, matches.length - 1)]
        ;(editor as any).setSelection(m.range)
        activeDecoration?.clear()
        activeDecoration = createDecoration(editor, m.range)
        ;(editor as any).revealRangeInCenter?.(m.range)
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
                activeDecoration?.clear()
                activeDecoration = null
                return
            }

            const sameFlow = old && old.namespace === sel.namespace && old.id === sel.id

            if (sameFlow) {
                await nextTick()
                if (!cancelled) highlightMatch(sel.matchIndex)
                return
            }

            isLoading.value = true
            error.value = false
            source.value = null

            try {
                const flow = await flowStore.loadFlow({namespace: sel.namespace, id: sel.id, store: false})
                if (cancelled) return
                source.value = flow?.source ?? null
                isLoading.value = false

                if (props.query) {
                    await nextTick()
                    if (!cancelled) highlightMatch(sel.matchIndex)
                }
            } catch {
                if (cancelled) return
                error.value = true
                isLoading.value = false
            }
        },
        {immediate: true},
    )

    watch(
        () => props.query,
        async (newQuery) => {
            if (newQuery && source.value && editorRef.value && props.selected) {
                await nextTick()
                highlightMatch(props.selected.matchIndex)
            }
        },
    )
</script>

<style scoped lang="scss">
.source-search-preview {
    height: 100%;
    display: flex;
    flex-direction: column;

    &__empty,
    &__error {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        padding: var(--ks-spacing-4);
    }

    &__loading {
        height: 100%;
    }

    &__editor {
        flex: 1;
        min-height: 0;
    }
}
</style>

<style lang="scss">
.source-search-preview__match-line {
    background: var(--ks-bg-active);
}
</style>
