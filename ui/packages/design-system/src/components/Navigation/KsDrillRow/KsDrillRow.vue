<template>
    <button
        type="button"
        class="kel-drill-row"
        :disabled="disabled"
        :aria-label="ariaLabel ?? label"
        @click="emit('open')"
    >
        <span v-if="$slots.leading" class="kel-drill-row__leading">
            <slot name="leading" />
        </span>

        <span class="kel-drill-row__main">
            <span class="kel-drill-row__head">
                <span class="kel-drill-row__label">{{ label }}</span>
                <span v-if="type" class="kel-drill-row__type">{{ type }}</span>
            </span>
            <span v-if="preview || $slots.default" class="kel-drill-row__preview">
                <slot>{{ preview }}</slot>
            </span>
        </span>

        <span class="kel-drill-row__chevron">
            <slot name="trailing">
                <ChevronRight :size="18" />
            </slot>
        </span>
    </button>
</template>

<style lang="scss">
    .kel-drill-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        width: 100%;
        text-align: left;
        padding: var(--ks-spacing-3);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        color: var(--ks-text-primary);
        cursor: pointer;
        font-family: inherit;
        transition: background-color 0.15s, border-color 0.15s;

        &:hover:not(:disabled) {
            background: var(--ks-bg-hover);
            border-color: var(--ks-border-strong);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: -1px;
        }

        &:disabled {
            opacity: 0.5;
            cursor: not-allowed;
        }
    }

    .kel-drill-row__leading {
        display: inline-flex;
        flex: none;
        color: var(--ks-icon-muted);
    }

    .kel-drill-row__main {
        display: flex;
        flex-direction: column;
        gap: 2px;
        flex: 1;
        min-width: 0;
    }

    .kel-drill-row__head {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-width: 0;
    }

    .kel-drill-row__label {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        color: var(--ks-text-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .kel-drill-row__type {
        flex: none;
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        background: var(--ks-bg-tag-inactive);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-xs);
        padding: 0 var(--ks-spacing-2);
    }

    .kel-drill-row__preview {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .kel-drill-row__chevron {
        display: inline-flex;
        flex: none;
        color: var(--ks-icon-muted);
    }

    .kel-drill-row:hover:not(:disabled) .kel-drill-row__chevron {
        color: var(--ks-text-link);
    }
</style>

<script setup lang="ts">
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"

    withDefaults(defineProps<{
        label: string;
        type?: string;
        preview?: string;
        disabled?: boolean;
        ariaLabel?: string;
    }>(), {})

    const emit = defineEmits<{open: []}>()

    defineSlots<{
        default?(): unknown;
        leading?(): unknown;
        trailing?(): unknown;
    }>()
</script>
