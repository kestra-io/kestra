<template>
    <button
        type="button"
        :class="classes"
    >
        <component
            v-if="icon"
            :is="statusIcon"
            class="ks-execution-status__icon"
        />
        <span class="ks-execution-status__text">
            <template v-if="$slots.title">
                <slot name="title" />
            </template>
            <template v-else>
                {{ displayText }}
            </template>
        </span>
    </button>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {EXECUTION_STATUSES, type ExecutionStatus} from "./types"

    const props = withDefaults(defineProps<{
        status: ExecutionStatus;
        title?: string;
        icon?: boolean;
        size?: "large" | "default" | "small";
    }>(), {
        icon: true,
        size: "default",
        title: undefined,
    })

    defineSlots<{
        title?: unknown
    }>()

    const statusIcon = computed(() => {
        return EXECUTION_STATUSES[props.status]?.icon
    })

    const displayText = computed(() => {
        return props.title ?? (props.status.charAt(0).toUpperCase() + props.status.slice(1).toLowerCase())
    })

    const classes = computed(() => [
        "ks-execution-status",
        props.status?.toLowerCase() && `ks-execution-status--${props.status.toLowerCase()}`,
        props.size !== "default" && `ks-execution-status--${props.size}`,
    ].filter(Boolean))
</script>

<style scoped lang="scss">
$statusList: created, restarted, success, running, killing, killed, warning, failed, paused, cancelled, skipped, queued, retrying, retried, breakpoint;

.ks-execution-status {
    display: inline-flex;
    justify-content: center;
    align-items: center;
    line-height: 1;
    white-space: nowrap;
    cursor: default;
    text-align: center;
    box-sizing: border-box;
    outline: none;
    margin: 0;
    transition: 0.1s;
    font-weight: 500;
    user-select: none;
    vertical-align: middle;
    appearance: none;
    border: none;
    border-radius: var(--ks-radius-sm);
    background: var(--ks-bg-badge);
    font-family: inherit;
    height: 2rem;
    padding: 0.5rem 0.9375rem;
    font-size: var(--ks-font-size-sm);
    gap: 0.375rem;

    .ks-execution-status__icon {
        display: inline-flex;
        align-items: center;
        font-size: 1.10rem;
    }

    .ks-execution-status__text {
        display: inline-flex;
        align-items: center;
    }

    &::-moz-focus-inner {
        border: 0;
    }

    &.ks-execution-status--large {
        height: 2.5rem;
        padding: 0.75rem 1.1875rem;
        font-size: var(--ks-font-size-sm);
        gap: 0.5rem;
    }

    &.ks-execution-status--small {
        height: 1.5rem;
        padding: 0 var(--ks-spacing-2);
        font-size: var(--ks-font-size-xs);
        gap: 0.25rem;
    }
}

@each $status in $statusList {
    .ks-execution-status--#{$status} {
        color: var(--ks-status-#{$status});
        background-color: var(--ks-status-background-#{$status});
    }
}
</style>
