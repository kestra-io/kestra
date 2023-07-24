<template>
    <a class="el-button el-button--primary mt-2 mb-2 " v-if="isFile(value)" :href="itemUrl(value)" target="_blank">
        <Download />
        &nbsp;
        {{ $t('download') }}
        &nbsp;
        <span v-if="humanSize">({{ humanSize }})</span>
    </a>
    <span v-else>
        {{ value }}
    </span>
</template>

<script setup>
    import Download from "vue-material-design-icons/Download.vue";
</script>

<script>
    import {apiRoot} from "../../utils/axios";
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
                return `${apiRoot}executions/${this.execution.id}/file?path=${value}`;
            }
        },
        created() {
            if (this.isFile(this.value)) {
                this.$http(
                    `${apiRoot}executions/${this.execution.id}/file/metas?path=${this.value}`,
                    {
                        validateStatus: (status) => {
                            return status === 200 || status === 404 || status === 422;
                        }
                    }
                )
                    .then(
                        r => this.humanSize = Utils.humanFileSize(r.data.size)
                    )
            }
        },
        props: {
            value: {
                type: [String, Object, Boolean, Number],
                required: true
            },
            execution: {
                type: Object,
                required: false,
                default: undefined
            }
        }
    };
</script>
