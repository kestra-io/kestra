<template>
    <span v-b-tooltip.hover="inverted ? from : full">{{ inverted ? full : from }}</span>
</template>
<script>
    import Vue from "vue";

    export default {
        props: {
            date: {
                type: String,
                required: true
            },
            inverted: {
                type: Boolean,
                default: false
            }
        },
        computed: {
            from() {
                return Vue.moment(this.date).fromNow();
            },
            full() {
                return Vue.moment(this.date).format("LLLL");
            }
        }
    };
</script>
