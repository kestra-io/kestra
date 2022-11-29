<template>
    <span class="kicon">
        <el-tooltip
            v-if="tooltip"
            :content="tooltip"
            :raw-content="true"
            :placement="placement"
        >
            <slot />
        </el-tooltip>
        <slot v-else />
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
