<template>
    <span data-component="FILENAME_PLACEHOLDER" class="kicon">
        <el-tooltip
            effect="light"
            v-if="tooltip"
            :content="tooltip"
            :raw-content="true"
            :placement="placement"
            :persistent="false"
            transition=""
            :hide-after="0"
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
