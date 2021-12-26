<template>
    <b-link
        v-if="isFile(value)"
        target="_blank"
        :href="itemUrl(value)"
    >
        <kicon placement="left">
            <download /> {{ $t('download') }}
            <span v-if="humanSize">({{ humanSize }})</span>
        </kicon>
    </b-link>
    <span v-else>
        {{ value }}
    </span>
</template>

<script>
    import {apiRoot} from "../../http";
    import Download from "vue-material-design-icons/Download";
    import Kicon from "../Kicon"
    import Utils from "../../utils/utils";

    export default {
        components: {
            Download,
            Kicon
        },
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
            },

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
