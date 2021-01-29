<template>
    <b-link
        v-if="isFile(value)"
        target="_blank"
        :href="itemUrl(value)"
        @mouseenter="getSize(value)"
    >
        <kicon placement="left" :tooltip="humanSize">
            <download /> {{ $t('download') }}
        </kicon>
    </b-link>
    <span v-else v-html="value" />
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
            getSize(value) {
                this.$http(`${apiRoot}executions/${this.execution.id}/filemetas?path=${value}`).then(
                    r => this.humanSize = Utils.humanFileSize(r.data.size)
                )
            }
        },
        props: {
            value: {
                type: [String, Object],
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
