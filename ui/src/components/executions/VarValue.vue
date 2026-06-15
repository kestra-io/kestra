<template>
    <KsButtonGroup v-if="isFileValid(value)">
        <KsButton
            type="primary"
            tag="a"
            :href="itemUrl(value.toString())"
            target="_blank"
            size="small"
            :icon="Download"
            rel="noopener noreferrer"
        >
            {{ $t('download') }}
        </KsButton>
        <FilePreviewDrawer v-if="Utils.isFile(value)" :value="value.toString()" :executionId="execution.id" />
        <KsButton disabled size="small" type="primary" v-if="humanSize">
            ({{ humanSize }})
        </KsButton>
    </KsButtonGroup>
    <KsButtonGroup v-else-if="Utils.isFile(value) && fileStatus === 'loading'">
        <KsButton disabled loading size="small" type="primary">
            {{ $t('download') }}
        </KsButton>
    </KsButtonGroup>
    <KsTooltip v-else-if="Utils.isFile(value) && fileStatus === 'missing'" :content="$t('file unavailable description')">
        <KsButton disabled size="small" type="primary" :icon="FileAlertOutline">
            {{ $t('file unavailable') }}
        </KsButton>
    </KsTooltip>
    <KsButtonGroup v-else-if="isURI(value) && !Utils.isFile(value)">
        <KsButton
            type="primary"
            tag="a"
            size="small"
            :href="value"
            target="_blank"
            :icon="OpenInNew"
        >
            {{ $t('open') }}
        </KsButton>
    </KsButtonGroup>

    <span v-else-if="value === null">
        <em>null</em>
    </span>
    <div v-else-if="isComplexValue(value)">
        <KsEditor
            v-bind="editorBindings"
            :readOnly="true"
            :inline="true"
            :options="{
                showScroll: true,
                fullHeight: false,
                customHeight: Math.min(20, Math.max(5, JSON.stringify(getDisplayValue(value), null, 2).split('\n').length)),
            }"
            :navbar="false"
            :modelValue="JSON.stringify(getDisplayValue(value), null, 2)"
            lang="json"
            class="complex-value-editor"
        />
    </div>
    <span v-else>
        {{ value }}
    </span>
</template>

<script setup lang="ts">
    import {ref, watch, onMounted} from "vue"
    import Download from "vue-material-design-icons/Download.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import FileAlertOutline from "vue-material-design-icons/FileAlertOutline.vue"
    import FilePreviewDrawer from "./FilePreviewDrawer.vue"
    import {KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {apiUrl} from "override/utils/route"
    import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"

    import * as Utils from "../../utils/utils"

    interface Execution {
        id: string;
    }

    const props = withDefaults(defineProps<{
        value?: string | object | boolean | number;
        execution?: Execution;
        restrictUri?: boolean;
    }>(), {
        value: "",
        execution: () => ({id: ""}),
        restrictUri: false,
    })

    const editorBindings = useEditorBindings()

    const humanSize = ref<string>("")
    const fileStatus = ref<"loading" | "available" | "missing">("loading")

    const isFileValid = (value: unknown): boolean => {
        return Utils.isFile(value) && humanSize.value.length > 0 && humanSize.value !== "0B"
    }

    const isURI = (value: unknown): value is string => {
        if (typeof value !== "string") {
            return false
        }
        try {
            const url = new URL(value)
            if (props.restrictUri) {
                return ["http:", "https:"].includes(url.protocol)
            }
            return true
        } catch {
            return false
        }
    }

    const isComplexValue = (value: unknown): boolean => {
        if ((typeof value === "object" && value !== null) || Array.isArray(value)) {
            return true
        }

        if (typeof value === "string") {
            try {
                const parsed = JSON.parse(value)
                return (typeof parsed === "object" && parsed !== null) || Array.isArray(parsed)
            } catch {
                return false
            }
        }

        return false
    }

    const getDisplayValue = (value: unknown): unknown => {
        if ((typeof value === "object" && value !== null) || Array.isArray(value)) {
            return value
        }

        if (typeof value === "string") {
            try {
                const parsed = JSON.parse(value)
                if ((typeof parsed === "object" && parsed !== null) || Array.isArray(parsed)) {
                    return parsed
                }
            } catch {
                return value
            }
        }

        return value
    }

    const itemUrl = (value: string): string => {
        return `${apiUrl()}/executions/${props.execution?.id}/file?path=${encodeURI(value)}`
    }

    const getFileSize = async (): Promise<void> => {
        if (Utils.isFile(props.value) && props.execution?.id) {
            humanSize.value = ""
            fileStatus.value = "loading"

            const data = await ExecutionsAPI.fileMetadatasFromExecution({
                executionId: props.execution.id, 
                path: props.value.toString(),
            }, {
                validateStatus: (status: number) => status === 200 || status === 404 || status === 422,
            })
            if(!data){
                fileStatus.value = "missing"
                return
            }    
            humanSize.value = Utils.humanFileSize(data.size)
            fileStatus.value = "available"
        }
    }

    watch(() => props.value, (newValue) => {
        if (newValue) {
            getFileSize()
        }
    })

    onMounted(() => {
        getFileSize()
    })
</script>

<style scoped lang="scss">
.complex-value-editor {
    margin-top: 0.5rem;
    border: 1px solid var(--ks-border-default);
    border-radius: 4px;
}
</style>
