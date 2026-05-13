<template>
    <div :class="classes">
        <component :is="statusIcon" />
        <span class="ks-code-status__text">
            <slot>{{ label }}</slot>
        </span>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import AlertBoxOutline from "vue-material-design-icons/AlertBoxOutline.vue"

    type CodeStatus = "valid" | "error"

    const props = defineProps<{
        status: CodeStatus
        label?: string
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const statusIcon = computed(() =>
        props.status === "valid" ? CheckCircleOutline : AlertBoxOutline,
    )

    const classes = computed(() => [
        "ks-code-status",
        `ks-code-status--${props.status}`,
    ])
</script>

<style scoped lang="scss">
    .ks-code-status {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-sm);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-semibold);

        &__text {
            display: inline-flex;
            align-items: center;
        }

        &--valid {
            color: var(--ks-text-success);
        }

        &--error {
            color: var(--ks-text-error);
        }
    }
</style>
