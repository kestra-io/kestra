<template>
    <div v-b-tooltip="{title:tooltip, placement, html: true}">
        <slot />
    </div>
</template>
<script>
    export default {
        props:{
            tooltip: {
                type: String,
                default: ""
            },
            placement:{
                type: String,
                default: "top"
            },
        },
        mounted () {
            this.$el.querySelector("title").remove()
        }
    }
</script>