<template>
    <div class="source-search-preview" data-test="source-search-preview">
        <div v-if="!props.selected" class="source-search-preview__empty">
            <KsEmpty :background="false" :description="t('source_search.preview_empty')" />
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
            :key="editorKey"
            class="source-search-preview__editor"
            ref="editorRef"
            :modelValue="source"
            lang="yaml"
            :readOnly="true"
            :navbar="false"
            @editorMounted="applyHighlight"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
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

    const editorKey = computed(() => props.selected ? `${props.selected.namespace}/${props.selected.id}` : "")

    let activeDecoration: {clear: () => void} | null = null

    function applyHighlight() {
        if (!props.selected || !props.query) return
        const editor = editorRef.value?.getEditor?.() as any
        const model = editor?.getModel?.()
        if (!model) return
        const matches = model.findMatches(props.query, false, false, false, null, false)
        if (!matches?.length) return
        const m = matches[Math.min(props.selected.matchIndex, matches.length - 1)]
        editor.setSelection(m.range)
        activeDecoration?.clear()
        activeDecoration = editor.createDecorationsCollection([
            {range: m.range, options: {isWholeLine: true, className: "source-search-preview__match-line"}},
        ])
        editor.revealRangeInCenter?.(m.range)
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
                applyHighlight()
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
        () => props.query,
        () => {
            if (props.query && source.value && props.selected) applyHighlight()
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

:global(.source-search-preview__match-line) {
    background: var(--ks-bg-active);
}
</style>
