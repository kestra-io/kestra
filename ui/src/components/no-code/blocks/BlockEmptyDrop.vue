<template>
    <button
        class="block-empty-drop"
        :class="`block-empty-drop--${variant}`"
        type="button"
        :data-test="dataTest"
        @click="emit('add', $event)"
    >
        <span class="block-empty-drop-lead">
            <PlusCircleOutline class="block-empty-drop-ico" />
            {{ variant === "empty" ? t("block_editor.empty_add_lead", {label}) : t("block_editor.inline_add", {label}) }}
        </span>

        <span v-if="hint" class="block-empty-drop-hint">{{ hint }}</span>
    </button>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import PlusCircleOutline from "vue-material-design-icons/PlusCircleOutline.vue"

    const {t} = useI18n()

    withDefaults(defineProps<{
        label: string
        variant?: "empty" | "inline"
        hint?: string
        dataTest?: string
    }>(), {
        variant: "inline",
    })

    const emit = defineEmits<{
        (e: "add", evt: MouseEvent): void
    }>()
</script>

<style scoped lang="scss">
    .block-empty-drop {
        display: flex;
        background: transparent;
        border: 1px dashed var(--ks-border-strong);
        border-radius: var(--ks-radius-base);
        color: var(--ks-text-secondary);
        cursor: pointer;
        font-family: inherit;
        transition: color 0.12s, border-color 0.12s, background-color 0.12s;

        &:hover {
            color: var(--ks-text-primary);
            border-color: var(--ks-border-strong);
            background: var(--ks-btn-secondary-bg-hover);
        }

        &:focus-visible {
            outline: none;
            border-color: var(--ks-border-focus);
            box-shadow: 0 0 0 2px var(--ks-border-focus);
        }
    }

    // Reactive canvas-focus ring for an empty section's sentinel (see
    // sectionSentinelId in BlockEditor.vue) — mirrors the same rule every
    // real block card already has, since :focus-visible only reacts to real
    // DOM focus and this ring is driven by the virtual j/k selection instead.
    .block-kbd-focused {
        border-color: var(--ks-border-focus);
        box-shadow: 0 0 0 2px var(--ks-border-focus);
    }

    .block-empty-drop--empty {
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-4) var(--ks-spacing-6);
        text-align: center;
    }

    .block-empty-drop--inline {
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        font-size: var(--ks-font-size-sm);
    }

    .block-empty-drop-lead {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        font-size: var(--ks-font-size-sm);
        font-weight: 500;
    }

    .block-empty-drop-ico {
        display: flex;
        font-size: var(--ks-font-size-base);
    }

    .block-empty-drop-hint {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
    }
</style>
