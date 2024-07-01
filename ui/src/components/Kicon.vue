<template>
    <span :data-component="dataComponent" class="kicon">
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
    import BaseComponents from "../components/BaseComponents.vue"

    export default {
        extends: BaseComponents,
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
