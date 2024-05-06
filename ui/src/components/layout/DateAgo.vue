<template>
    <el-tooltip :key="uid('tooltip')" v-if="date" :content="inverted ? from : full" :persistent="false" transition="" :hide-after="0" effect="light">
        <span :class="className">{{ inverted ? full : from }}</span>
    </el-tooltip>
</template>
<script>
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
            uid(key) {
                return key + "-" + Utils.uid();
            }
        },
        computed: {
            from() {
                return this.$moment(this.date).fromNow();
            },
            full() {
                return this.$filters.date(this.date, this.format);
            },

        }
    };
</script>
