<template>
    <Transition name="undo-toast">
        <div v-if="state" class="undo-toast" role="status" aria-live="polite">
            <span class="undo-toast-label">{{ state.label }}</span>
            <button
                type="button"
                class="undo-toast-btn"
                data-test="undo-toast-button"
                @click="emit('undo')"
            >
                {{ $t("block_editor.undo") }}
            </button>
        </div>
    </Transition>
</template>

<script setup lang="ts">
    defineProps<{state: {label: string} | null}>()
    const emit = defineEmits<{undo: []}>()
</script>

<style scoped lang="scss">
    .undo-toast {
        position: absolute;
        bottom: var(--undo-toast-offset, var(--ks-spacing-4));
        left: 50%;
        transform: translateX(-50%);
        z-index: 11;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-2) var(--ks-spacing-2) var(--ks-spacing-4);
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        box-shadow: var(--ks-shadow-sm);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
    }

    .undo-toast-label {
        white-space: nowrap;
    }

    .undo-toast-btn {
        border: none;
        background: transparent;
        color: var(--ks-text-link);
        font-weight: 600;
        font-size: var(--ks-font-size-sm);
        cursor: pointer;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-sm);
        transition: background-color 0.12s;
    }

    .undo-toast-btn:hover {
        background: var(--ks-bg-hover);
    }

    .undo-toast-btn:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: 1px;
    }

    .undo-toast-enter-active,
    .undo-toast-leave-active {
        transition: opacity 0.18s ease, transform 0.18s ease;
    }

    .undo-toast-enter-from,
    .undo-toast-leave-to {
        opacity: 0;
        transform: translate(-50%, 8px);
    }

    @media (prefers-reduced-motion: reduce) {
        .undo-toast-enter-active,
        .undo-toast-leave-active {
            transition: none;
        }
    }
</style>
