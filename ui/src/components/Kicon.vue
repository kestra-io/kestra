<template>
    <el-tooltip
        effect="light"
        v-if="tooltip"
        :content="tooltip"
        :rawContent="true"
        v-bind="placement ? {placement} : {}"
        :persistent="false"
        :enterable="false"
        transition=""
        :hideAfter="0"
    >
        <span class="kicon">
            <slot />
        </span>
    </el-tooltip>
    <span v-else class="kicon">
        <slot />
    </span>
</template>
<script setup lang="ts">
    withDefaults(
        defineProps<{
            tooltip?: string;
            placement?: string;
        }>(),{
            tooltip: "",
            placement: "",
        });
</script>

<style scoped lang="scss">
    .kicon {
        white-space: nowrap;
    }
</style>
