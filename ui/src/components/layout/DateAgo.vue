<template>
    <el-tooltip
        data-component="FILENAME_PLACEHOLDER"
        :key="uid('tooltip')"
        v-if="date"
        :content="inverted ? from : full"
        :persistent="false"
        transition=""
        :hide-after="0"
        effect="light"
    >
        <span :class="className">{{ inverted ? full : from }}</span>
    </el-tooltip>
</template>
<script lang="ts">
    import Utils from "../../utils/utils";

    export default {
        props: {
            date: {
                type: String,
                default: undefined
            },
            inverted: {
                type: Boolean,
                default: false
            },
            format: {
                type: String,
                default: undefined
            },
            className: {
                type: String,
                default: null
            }
        },
        methods: {
            uid(key: string) {
                return key + "-" + Utils.uid();
            }
        },
        computed: {
            from() {
                return (this as any).$moment(this.date).fromNow();
            },
            full() {
                return (this as any).$filters.date(this.date, this.format);
            },

        }
    };
</script>
