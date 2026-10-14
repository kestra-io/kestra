<template>
    <ListPreview v-if="type === 'LIST'" :value="listContent" />
    <img v-else-if="type === 'IMAGE'" :src="imageContent" :alt="$t('file_preview.image_alt')">
    <PdfPreview v-else-if="type === 'PDF'" :source="textContent" />
    <KsMarkdown v-else-if="type === 'MARKDOWN'" :content="textContent" />
    <KsEditor
        v-else
        v-bind="editorBindings"
        :modelValue="editorContent"
        :lang="!forceEditor ? extensionToMonacoLang : 'json'"
        readOnly
        inline
        :options="{
            wordWrap,
            fullHeight: false,
            customHeight: 14,
        }"
        class="position-relative"
    >
        <template #nav>
            <div class="preview-actions">
                <KsButton
                    size="small"
                    square
                    :tooltip="$t('copy_to_clipboard')"
                    :icon="ContentCopy"
                    @click="copyContent"
                />
                <KsButton
                    size="small"
                    square
                    :tooltip="$t('toggle_word_wrap')"
                    :icon="Wrap"
                    :aria-pressed="wordWrap"
                    @click="wordWrap = !wordWrap"
                />
            </div>
        </template>
    </KsEditor>
</template>

<script setup lang="ts">
    import {ref, computed, defineAsyncComponent} from "vue"
    import Wrap from "vue-material-design-icons/Wrap.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import {KsMarkdown, KsEditor, KsButton, copyToClipboard} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import ListPreview, {type PreviewCell} from "../ListPreview.vue"
    import type {FilePreview} from "../../stores/executions"

    // Async so pdfjs-dist (~417 kB) is fetched only when an output actually is a PDF.
    const PdfPreview = defineAsyncComponent(() => import("../PdfPreview.vue"))

    export interface Preview extends Omit<FilePreview, "type"> {
        /** "RAW" is UI-only: FilePreview.vue forces it to show non-text content in the editor. */
        type?: FilePreview["type"] | "RAW";
    }

    const props = defineProps<Preview>()

    const wordWrap = ref(true)

    const editorBindings = useEditorBindings()

    const forceEditor = computed(() => {
        return props.type === "RAW" && typeof props.content === "object"
    })

    const listContent = computed<PreviewCell[]>(() => Array.isArray(props.content) ? props.content : [])
    const textContent = computed(() => typeof props.content === "string" ? props.content : "")
    const editorContent = computed(() => forceEditor.value ? JSON.stringify(props.content, null, 2) : textContent.value)

    const copyContent = () => copyToClipboard(editorContent.value)

    const extensionToMonacoLang = computed(() => {
        switch (props.extension) {
        case "json":
            return "json"
        case "jsonl":
            return "jsonl"
        case "yaml":
        case "yml":
        case "ion":
            return "yaml"
        case "csv":
            return "csv"
        case "py":
            return "python"
        default:
            return props.extension
        }
    })

    const imageContent = computed(() => {
        return `data:image/${props.extension};base64,${props.content}`
    })
</script>

<style scoped lang="scss">
    .preview-actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--ks-spacing-2);
    }
</style>
