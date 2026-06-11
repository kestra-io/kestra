<template>
    <ListPreview v-if="type === 'LIST'" :value="content" />
    <img v-else-if="type === 'IMAGE'" :src="imageContent" alt="Image output preview">
    <PdfPreview v-else-if="type === 'PDF'" :source="content" />
    <KsMarkdown v-else-if="type === 'MARKDOWN'" :content="content" />
    <KsEditor
        v-else
        v-bind="editorBindings"
        :modelValue="!forceEditor ? content : JSON.stringify(content, null, 2)"
        :lang="!forceEditor ? extensionToMonacoLang : 'json'"
        readOnly
        inline
        :options="{wordWrap, fullHeight: false}"
        :navbar="false"
        class="position-relative"
    >
        <template #absolute>
            <CopyToClipboard :text="!forceEditor ? content : JSON.stringify(content, null, 2)">
                <template #right>
                    <KsTooltip
                        :content="$t('toggle_word_wrap')"
                        placement="bottom"
                        :autoClose="2000"
                    >
                        <KsButton
                            :icon="Wrap"
                            type="default"
                            @click="wordWrap = !wordWrap"
                        />
                    </KsTooltip>
                </template>
            </CopyToClipboard>
        </template>
    </KsEditor>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import Wrap from "vue-material-design-icons/Wrap.vue"
    import CopyToClipboard from "../layout/CopyToClipboard.vue"
    import {KsMarkdown, KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import ListPreview from "../ListPreview.vue"
    import PdfPreview from "../PdfPreview.vue"

    export interface Preview {
        truncated?: boolean;
        type?: "LIST" | "IMAGE" | "PDF" | "MARKDOWN" | "RAW";
        content?: any;
        extension?: string;
    }

    const props = defineProps<Preview>()

    const wordWrap = ref(false)

    const editorBindings = useEditorBindings()

    const forceEditor = computed(() => {
        return props.type === "RAW" && typeof props.content === "object"
    })

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
