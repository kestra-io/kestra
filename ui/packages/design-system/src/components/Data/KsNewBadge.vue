<template>
    <span class="ks-new-badge"><slot>NEW</slot></span>
</template>

<script setup lang="ts">
    defineSlots<{
        default?(): unknown
    }>()
</script>

<style scoped lang="scss">
    .ks-new-badge {
        display: inline-block;
        flex-shrink: 0;
        padding: 0 var(--ks-spacing-1);
        border: var(--ks-border-width-thin) solid var(--ks-border-info);
        border-radius: var(--ks-radius-xs);
        background: var(--ks-bg-base);
        box-shadow: 0 1px 2px var(--ks-shadow-element);
        color: var(--ks-text-info);
        font-size: var(--ks-font-size-2xs);
        font-weight: var(--ks-font-weight-semibold);
        line-height: var(--ks-line-height-base);
        text-transform: uppercase;
    }
</style>
