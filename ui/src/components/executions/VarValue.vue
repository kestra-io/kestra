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
    <span v-else>
        {{ value }}
    </span>
</template>

<script setup lang="ts">
    import {ref, watch, onMounted} from "vue";
    import Download from "vue-material-design-icons/Download.vue";
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import FilePreview from "./FilePreview.vue";
    import {apiUrl} from "override/utils/route";
    import Utils from "../../utils/utils";

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
        value: undefined,
        execution: undefined,
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

    const itemUrl = (value: string): string => {
        return `${apiUrl()}/executions/${props.execution?.id}/file?path=${encodeURI(value)}`;
    };

    const getFileSize = async (): Promise<void> => {
        if (isFile(props.value) && props.execution?.id) {
            try {
                const response = await fetch(`${apiUrl()}/executions/${props.execution.id}/file/metas?path=${props.value}`, {
                    method: "GET"
                });
                if (response.ok) {
                    const data: FileMetadata = await response.json();
                    humanSize.value = Utils.humanFileSize(data.size);
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
