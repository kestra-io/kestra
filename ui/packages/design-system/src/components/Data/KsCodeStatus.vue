<template>
    <div :class="classes">
        <component :is="statusIcon" class="ks-code-status__icon" />
        <span v-if="!iconOnly" class="ks-code-status__text">
            <slot>{{ label }}</slot>
        </span>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import AlertBoxOutline from "vue-material-design-icons/AlertBoxOutline.vue"
    import AlertOutline from "vue-material-design-icons/AlertOutline.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"

    type CodeStatus = "valid" | "error" | "warning" | "info"

    const props = withDefaults(defineProps<{
        status: CodeStatus
        label?: string
        iconOnly?: boolean
    }>(), {
        iconOnly: false,
    })

    defineSlots<{
        default?(): unknown
    }>()

    const ICONS = {
        valid: CheckCircleOutline,
        error: AlertBoxOutline,
        warning: AlertOutline,
        info: InformationOutline,
    } as const

    const statusIcon = computed(() => ICONS[props.status])

    const classes = computed(() => [
        "ks-code-status",
        `ks-code-status--${props.status}`,
        {"ks-code-status--icon-only": props.iconOnly},
    ])
</script>

<style scoped>
    .ks-code-status {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-sm);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-semibold);
    }

    .ks-code-status--icon-only {
        padding: var(--ks-spacing-1);
        aspect-ratio: 1 / 1;
        justify-content: center;
    }

    .ks-code-status__text {
        display: inline-flex;
        align-items: center;
    }

    .ks-code-status--valid {
        color: var(--ks-text-success);
    }

    .ks-code-status--error {
        color: var(--ks-text-error);
    }

    .ks-code-status--warning {
        color: var(--ks-status-warning);
    }

    .ks-code-status--info {
        color: var(--ks-status-info);
    }
</style>
