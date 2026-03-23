<template>
    <ks-tooltip
        v-if="tooltip"
        :content="tooltip"
        :rawContent="true"
        v-bind="placement ? {placement} : {}"
        :enterable="false"
    >
        <span class="kicon">
            <slot />
        </span>
    </ks-tooltip>
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
