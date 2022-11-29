<template>
    <el-tooltip v-if="date" :content="inverted ? from : full">
        <span>{{ inverted ? full : from }}</span>
    </el-tooltip>
</template>
<script>
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
                default: "LLLL"
            }
        },
        computed: {
            from() {
                return this.$moment(this.date).fromNow();
            },
            full() {
                return this.$moment(this.date).format(this.format);
            }
        }
    };
</script>
