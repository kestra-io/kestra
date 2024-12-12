<template>
    <el-button-group v-if="isFile(value)">
        <a class="el-button el-button--small el-button--primary" :href="itemUrl(value)" target="_blank">
            <Download />
            {{ $t('download') }}
        </a>
        <FilePreview v-if="value.startsWith('kestra:///')" :value="value" :execution-id="execution.id" />
        <el-button disabled size="small" type="primary" v-if="humanSize">
            ({{ humanSize }})
        </el-button>
    </el-button-group>

    <el-button-group v-else-if="isURI(value)">
        <a class="el-button el-button--small el-button--primary" :href="value" target="_blank">
            <OpenInNew /> &nbsp;
            {{ $t('open') }}
        </a>
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
    import axios from "axios";
    import {apiUrl} from "override/utils/route";
    import {useStore} from "vuex";
    import Download from "vue-material-design-icons/Download.vue";
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import Utils from "../../utils/utils";
    // @ts-expect-error will refactor later
    import FilePreview from "./FilePreview.vue";

    const store = useStore();

    const props = defineProps({
        value: {
            type: [String, Object, Boolean, Number],
            required: false,
            default: undefined
        },
        execution: {
            type: Object,
            required: false,
            default: undefined
        }
    });

    const humanSize = ref<string | null>(null);

    function isFile(value: unknown): value is string {
        return typeof value === "string" && value.startsWith("kestra:///")
    }

    function isURI(value: any): value is string {
        try {
            new URL(value);
            return true;
        } catch (e) {
            return false;
        }
    }

    function itemUrl(value:string) {
        return `${apiUrl(store)}/executions/${props.execution?.id}/file?path=${encodeURI(value)}`;
    }

    function getFileSize(){
        if (isFile(props.value)) {
            axios.get(`${apiUrl(store)}/executions/${props.execution?.id}/file/metas?path=${props.value}`, {
                validateStatus: (status: number) => status === 200 || status === 404 || status === 422
            }).then((r:any) => humanSize.value = Utils.humanFileSize(r.data.size))
        }
    }

    watch(() => props.value, (newValue) => {
        if (newValue) getFileSize();
    });

    onMounted(() => {
        getFileSize();
    });
</script>
