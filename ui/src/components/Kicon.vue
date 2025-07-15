<template>
    <span data-component="FILENAME_PLACEHOLDER" class="kicon">
        <el-tooltip
            effect="light"
            v-if="tooltip"
            :content="tooltip"
            :raw-content="true"
            v-bind="placement ? {placement} : {}"
            :persistent="false"
            transition=""
            :hide-after="0"
        >
            <slot />
        </el-tooltip>
        <slot v-else />
    </span>
</template>
<script lang="ts" setup>
    withDefaults(
        defineProps<{
            tooltip?: string;
            placement?: string;
        }>(),{
            tooltip: "",
            placement: "",
        });
</script>

<style lang="scss" scoped>
    .kicon {
        white-space: nowrap;
    }
</style>
