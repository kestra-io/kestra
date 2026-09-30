<template>
    <ListPreview v-if="type === 'LIST'" :value="content" />
    <img v-else-if="type === 'IMAGE'" :src="imageContent" :alt="$t('file_preview.image_alt')">
    <PdfPreview v-else-if="type === 'PDF'" :source="content" />
    <KsMarkdown v-else-if="type === 'MARKDOWN'" :content="content" />
    <KsEditor
        v-else
        v-bind="editorBindings"
        :modelValue="!forceEditor ? content : JSON.stringify(content, null, 2)"
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
    import ListPreview from "../ListPreview.vue"

    // Async so pdfjs-dist (~417 kB) is fetched only when an output actually is a PDF.
    const PdfPreview = defineAsyncComponent(() => import("../PdfPreview.vue"))

    export interface Preview {
        truncated?: boolean;
        type?: "TEXT" | "LIST" | "IMAGE" | "PDF" | "MARKDOWN" | "RAW";
        content?: any;
        extension?: string;
    }

    const props = defineProps<Preview>()

    const wordWrap = ref(true)

    const editorBindings = useEditorBindings()

    const forceEditor = computed(() => {
        return props.type === "RAW" && typeof props.content === "object"
    })

    const copyContent = () => copyToClipboard(!forceEditor.value ? props.content : JSON.stringify(props.content, null, 2))

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
