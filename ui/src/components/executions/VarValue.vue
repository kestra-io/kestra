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

    <span v-else-if="value === null">
        <em>null</em>
    </span>
    <span v-else>
        {{ value }}
    </span>
</template>

<script setup>
    import Download from "vue-material-design-icons/Download.vue";
    import FilePreview from "./FilePreview.vue";
</script>

<script>
    import {apiUrl} from "override/utils/route";
    import Utils from "../../utils/utils";

    export default {
        data () {
            return {
                humanSize: ""
            }
        },
        methods: {
            isFile(value) {
                return typeof(value) === "string" && value.startsWith("kestra:///")
            },
            itemUrl(value) {
                return `${apiUrl(this.$store)}/executions/${this.execution.id}/file?path=${value}`;
            },
            getFileSize(){
                if (this.isFile(this.value)) {
                    this.$http(`${apiUrl(this.$store)}/executions/${this?.execution?.id}/file/metas?path=${this.value}`, {
                        validateStatus: (status) => status === 200 || status === 404 || status === 422
                    }).then(r => this.humanSize = Utils.humanFileSize(r.data.size))
                }
            }
        },
        watch: {
            value(newValue) {
                if(newValue) this.getFileSize()
            }
        },
        mounted() {
            this.getFileSize()
        },
        props: {
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
        }
    };
</script>
