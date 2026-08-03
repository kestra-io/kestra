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
        padding-top: 2rem;
        padding-inline: 0;

        &:has(.empty-state) {
            flex: 1;
            min-height: 0;
            display: flex;
            flex-direction: column;

            > .kel-table,
            > .ks-data-table-wrapper {
                flex: 1;
                min-height: 0;
            }

            .kel-scrollbar__view {
                height: 100%;
            }
        }
    }
</style>
