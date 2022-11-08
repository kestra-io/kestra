<template>
    <span v-if="date" v-b-tooltip.hover="inverted ? from : full">{{ inverted ? full : from }}</span>
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
