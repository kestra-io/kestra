<template>
    <el-button-group v-if="isFileValid(value)">
        <el-button
            type="primary"
            tag="a"
            :href="itemUrl(value.toString())"
            target="_blank"
            size="small"
            :icon="Download"
            rel="noopener noreferrer"
        >
            {{ $t('download') }}
        </el-button>
        <FilePreview v-if="isFile(value)" :value="value.toString()" :executionId="execution.id" />
        <el-button disabled size="small" type="primary" v-if="humanSize">
            ({{ humanSize }})
        </el-button>
    </el-button-group>
    <el-button-group v-else-if="isURI(value)">
        <el-button
            type="primary"
            tag="a"
            size="small"
            :href="value"
            target="_blank"
            :icon="OpenInNew"
        >
            {{ $t('open') }}
        </el-button>
    </el-button-group>

    <span v-else-if="value === null">
        <em>null</em>
    </span>
    <div v-else-if="isTooLarge">
        <el-alert
            type="warning"
            :closable="false"
            showIcon
        >
            {{ $t('large_outputs.value_too_large', {size: valueSize}) }}
        </el-alert>
        <el-button
            type="primary"
            size="small"
            class="mt-2"
            :icon="Download"
            @click="downloadJson"
        >
            {{ $t('large_outputs.download_json') }}
        </el-button>
    </div>
    <div v-else-if="isComplexValue(value)">
        <Editor
            :readOnly="true"
            :input="true"
            :fullHeight="false"
            :customHeight="editorHeight"
            :navbar="false"
            :modelValue="renderedText"
            lang="json"
            class="complex-value-editor"
        />
    </div>
    <span v-else>
        {{ value }}
    </span>
</template>

<script setup lang="ts">
    import {computed, ref, watch, onMounted} from "vue";
    import Download from "vue-material-design-icons/Download.vue";
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import FilePreview from "./FilePreview.vue";
    import Editor from "../inputs/Editor.vue";
    import {apiUrl} from "override/utils/route";
    import {useAxios} from "../../utils/axios";
    import Utils from "../../utils/utils";
    import {downloadJson as download, isTooLargeToRender} from "./largeValues";

    interface Execution {
        id: string;
    }

    interface FileMetadata {
        size: number;
    }

    const props = withDefaults(defineProps<{
        value?: string | object | boolean | number;
        execution?: Execution;
        restrictUri?: boolean;
    }>(), {
        value: "",
        execution: () => ({id: ""}),
        restrictUri: false,
    });

    const humanSize = ref<string>("");

    const isFile = (value: unknown): value is string => {
        return typeof value === "string" && (value.startsWith("kestra:///") || value.startsWith("file://") || value.startsWith("nsfile://"));
    };

    const isFileValid = (value: unknown): boolean => {
        return isFile(value) && humanSize.value.length > 0 && humanSize.value !== "0B";
    };

    const isURI = (value: unknown): value is string => {
        if (typeof value !== "string") {
            return false;
        }
        try {
            const url = new URL(value);
            if (props.restrictUri) {
                return ["http:", "https:"].includes(url.protocol);
            }
            return true;
        } catch {
            return false;
        }
    };

    const isComplexValue = (value: unknown): boolean => {
        if ((typeof value === "object" && value !== null) || Array.isArray(value)) {
            return true;
        }

        if (typeof value === "string") {
            try {
                const parsed = JSON.parse(value);
                return (typeof parsed === "object" && parsed !== null) || Array.isArray(parsed);
            } catch {
                return false;
            }
        }

        return false;
    };

    const getDisplayValue = (value: unknown): unknown => {
        if ((typeof value === "object" && value !== null) || Array.isArray(value)) {
            return value;
        }

        if (typeof value === "string") {
            try {
                const parsed = JSON.parse(value);
                if ((typeof parsed === "object" && parsed !== null) || Array.isArray(parsed)) {
                    return parsed;
                }
            } catch {
                return value;
            }
        }

        return value;
    };

    // Any value over the budget is offered as a download rather than rendered, whether it is an
    // object, a JSON string or a long plain one. Monaco and a 1 MB text node both wedge the tab.
    const renderedText = computed(() => isComplexValue(props.value)
        ? JSON.stringify(getDisplayValue(props.value), null, 2) ?? ""
        : String(props.value ?? ""));

    const isTooLarge = computed(() => isTooLargeToRender(renderedText.value));

    const valueSize = computed(() => Utils.humanFileSize(renderedText.value.length));

    const editorHeight = computed(() => Math.min(20, Math.max(5, renderedText.value.split("\n").length)));

    const downloadJson = () => download(renderedText.value, `output-${props.execution?.id || "value"}.json`);

    const itemUrl = (value: string): string => {
        return `${apiUrl()}/executions/${props.execution?.id}/file?path=${encodeURI(value)}`;
    };

    const axios = useAxios();

    const getFileSize = async (): Promise<void> => {
        if (isFile(props.value) && props.execution?.id) {
            try {
                const response = await axios.get<FileMetadata>(
                    `${apiUrl()}/executions/${props.execution.id}/file/metas?path=${props.value}`,
                    {validateStatus: (status: number) => status === 200 || status === 404 || status === 422},
                );
                if (response.status === 200) {
                    humanSize.value = Utils.humanFileSize(response.data.size);
                }
            } catch (error) {
                console.error("Failed to fetch file size:", error);
            }
        }
    };

    watch(() => props.value, (newValue) => {
        if (newValue) {
            getFileSize();
        }
    });

    onMounted(() => {
        getFileSize();
    });
</script>

<style scoped lang="scss">
.complex-value-editor {
    margin-top: 0.5rem;
    border: 1px solid var(--ks-border-primary);
    border-radius: 4px;
}
</style>