<template>
    <el-tooltip v-if="date" :content="inverted ? from : full" :persistent="false" transition="" :hide-after="0">
        <span :class="className">{{ inverted ? full : from }}</span>
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
            },
            className: {
                type: String,
                default: null
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
