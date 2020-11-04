<template>
    <b-link
        v-if="isFile(value)"
        target="_blank"
        :href="itemUrl(value)"
    >
        <download /> {{ $t('download') }}
    </b-link>
    <span v-else v-html="value" />
</template>

<script>
    import {apiRoot} from "../../http";
    import Download from "vue-material-design-icons/Download";

    export default {
        components: {
            Download
        },
        methods: {
            isFile(value) {
                return typeof(value) === "string" && value.startsWith("kestra:///")
            },
            itemUrl(value) {
                return `${apiRoot}executions/${this.execution.id}/file?path=${value}`;
            },
        },
        props: {
            value: {
                type: Object,
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
