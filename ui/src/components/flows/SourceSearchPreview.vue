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
        selected: {namespace: string; id: string} | null
        query: string
    }>()

    const {t} = useI18n()
    const flowStore = useFlowStore()

    const isLoading = ref(false)
    const error = ref(false)
    const source = ref<string | null>(null)
    const editorRef = ref<KsEditorExposes | null>(null)

    watch(
        () => props.selected,
        async (sel, _old, onCleanup) => {
            let cancelled = false
            onCleanup(() => { cancelled = true })

            if (!sel) {
                source.value = null
                error.value = false
                return
            }

            isLoading.value = true
            error.value = false
            source.value = null

            try {
                const flow = await flowStore.loadFlow({namespace: sel.namespace, id: sel.id, store: false})
                if (cancelled) return
                source.value = flow?.source ?? null

                if (props.query && editorRef.value) {
                    await nextTick()
                    revealFirstMatch(props.query)
                }
            } catch {
                if (cancelled) return
                error.value = true
            } finally {
                if (!cancelled) isLoading.value = false
            }
        },
        {immediate: true},
    )

    watch(
        () => props.query,
        async (newQuery) => {
            if (newQuery && source.value && editorRef.value) {
                await nextTick()
                revealFirstMatch(newQuery)
            }
        },
    )

    function revealFirstMatch(query: string) {
        const editor = editorRef.value?.getEditor?.()
        if (!editor) return
        const model = (editor as any).getModel?.()
        if (!model) return
        const matches = model.findMatches(query, false, false, false, null, false)
        if (matches?.length) {
            const line = matches[0].range.startLineNumber
            ;(editor as any).revealLineInCenter?.(line)
        }
    }
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
