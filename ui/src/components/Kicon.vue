<template>
    <span class="kicon" v-b-tooltip="{title:tooltip, placement, html: true}">
        <slot />
    </span>
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
    }
</script>

<style lang="scss" scoped>

.kicon {
    white-space: nowrap;
}
</style>
