<template>
    <div class="ks-check-item" :class="{'is-met': met}">
        <component
            :is="met ? CheckCircle : CheckboxBlankCircleOutline"
            class="ks-check-item__icon"
        />
        <span class="ks-check-item__label"><slot /></span>
    </div>
</template>

<script setup lang="ts">
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
    import CheckboxBlankCircleOutline from "vue-material-design-icons/CheckboxBlankCircleOutline.vue"

    withDefaults(defineProps<{
        met?: boolean
    }>(), {
        met: false,
    })

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style scoped lang="scss">
    .ks-check-item {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);

        &__icon {
            color: var(--ks-text-secondary);
        }

        &.is-met {
            color: var(--ks-text-success);

            .ks-check-item__icon {
                color: var(--ks-icon-success);
            }
        }
    }
</style>
