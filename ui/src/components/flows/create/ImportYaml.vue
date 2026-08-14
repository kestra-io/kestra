<template>
    <div class="import-yaml" data-test="import-yaml">
        <div class="import-header">
            <KsButton
                link
                class="back-btn"
                data-test="import-yaml-back"
                @click="emit('back')"
            >
                <KsIcon size="sm">
                    <ArrowLeft />
                </KsIcon>
                {{ $t("new_flow_landing.import.back") }}
            </KsButton>
            <KsText tag="h2" class="import-title">{{ $t("new_flow_landing.import.title") }}</KsText>
        </div>

        <KsAlert
            v-if="errorCode"
            type="error"
            :closable="false"
            class="parse-error"
            data-test="import-yaml-error"
        >
            {{ $t(`new_flow_landing.import.error.${errorCode}`) }}
            <template v-if="parseMessage">
                <br>
                <KsText class="parse-detail">{{ parseMessage }}</KsText>
            </template>
        </KsAlert>

        <div class="editor-section">
            <KsText class="editor-label">{{ $t("new_flow_landing.import.paste_label") }}</KsText>
            <KsEditor
                v-bind="editorBindings"
                v-model="yamlContent"
                lang="yaml"
                :options="{fullHeight: false, lineNumbers: true}"
                :navbar="false"
                class="yaml-editor"
                data-test="import-yaml-editor"
            />
        </div>

        <div class="upload-section">
            <KsText class="upload-label">{{ $t("new_flow_landing.import.upload_label") }}</KsText>
            <KsUpload
                accept=".yml,.yaml"
                :autoUpload="false"
                :showFileList="false"
                data-test="import-yaml-upload"
                @change="handleFileChange"
            >
                <KsButton type="default">
                    <KsIcon size="sm">
                        <TrayArrowDown />
                    </KsIcon>
                    {{ $t("new_flow_landing.import.upload_button") }}
                </KsButton>
                <template #tip>
                    <KsText class="upload-tip">{{ $t("new_flow_landing.import.upload_tip") }}</KsText>
                </template>
            </KsUpload>
        </div>

        <div class="import-actions">
            <KsButton
                type="primary"
                :disabled="!yamlContent.trim()"
                data-test="import-yaml-submit"
                @click="submit"
            >
                {{ $t("new_flow_landing.import.submit") }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useEditorBindings} from "../../../composables/useEditorBindings"
    import {parseImportYaml, type ImportErrorCode} from "../../../utils/importYamlUtils"
    import ArrowLeft from "vue-material-design-icons/ArrowLeft.vue"
    import TrayArrowDown from "vue-material-design-icons/TrayArrowDown.vue"

    const emit = defineEmits<{
        submit: [{yaml: string}]
        back: []
    }>()

    const {t} = useI18n()
    const editorBindings = useEditorBindings()

    const yamlContent = ref("")
    const errorCode = ref<ImportErrorCode | null>(null)
    const parseMessage = ref<string | null>(null)

    const MAX_UPLOAD_BYTES = 1024 * 1024

    const handleFileChange = async (file: {raw: File}) => {
        if (!file?.raw) return
        if (file.raw.size > MAX_UPLOAD_BYTES) {
            errorCode.value = "too_large"
            parseMessage.value = null
            return
        }
        try {
            const text = await file.raw.text()
            yamlContent.value = text
            errorCode.value = null
            parseMessage.value = null
        } catch {
            errorCode.value = "parse_error"
            parseMessage.value = t("new_flow_landing.import.read_error")
        }
    }

    const submit = () => {
        const result = parseImportYaml(yamlContent.value)
        if (result.errorCode) {
            errorCode.value = result.errorCode
            parseMessage.value = result.parseMessage ?? null
            return
        }
        errorCode.value = null
        parseMessage.value = null
        emit("submit", {yaml: yamlContent.value})
    }
</script>

<style scoped lang="scss">
    .import-yaml {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        padding: var(--ks-spacing-6) var(--ks-spacing-4);
        width: 100%;
        max-width: 48rem;
        margin: 0 auto;
    }

    .import-header {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .back-btn {
        align-self: flex-start;
        gap: var(--ks-spacing-1);
        display: flex;
        align-items: center;
    }

    .import-title {
        align-self: flex-start;
        font-size: var(--ks-font-size-xl);
        font-weight: var(--ks-font-weight-semibold);
        margin: 0;
    }

    .parse-error {
        margin: 0;
    }

    .parse-detail {
        display: block;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
        margin-top: var(--ks-spacing-1);
        font-family: var(--ks-font-family-mono, monospace);
        word-break: break-word;
    }

    .editor-section {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .editor-label,
    .upload-label {
        display: block;
        align-self: flex-start;
        font-weight: var(--ks-font-weight-medium);
        font-size: var(--ks-font-size-sm);
    }

    .yaml-editor {
        min-height: 16rem;
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        overflow: hidden;
    }

    .upload-section {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .upload-tip {
        display: block;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
        margin-top: var(--ks-spacing-1);
    }

    .import-actions {
        display: flex;
        justify-content: flex-end;
    }
</style>
