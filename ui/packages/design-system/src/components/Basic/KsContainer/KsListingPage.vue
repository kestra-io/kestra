<template>
    <section class="ks-listing-page" v-bind="$attrs">
        <slot />
    </section>
</template>

<script setup lang="ts">
    defineOptions({inheritAttrs: false})

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    .ks-listing-page {
        padding-block: 1.5rem;
        padding-inline: 0;
    }
</style>
