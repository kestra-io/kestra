<template>
    <KsDialog
        :modelValue="shortcutsOpen"
        :title="t('block_editor.shortcuts.title')"
        data-test="block-editor-shortcuts"
        @update:modelValue="(open?: boolean) => emit('update:shortcutsOpen', open ?? false)"
    >
        <div class="block-editor-shortcuts">
            <div v-for="group in shortcutGroups" :key="group.group" class="block-editor-shortcuts-col">
                <span class="block-editor-shortcuts-heading">{{ t(`block_editor.shortcuts.group_${group.group}`) }}</span>
                <div v-for="binding in group.bindings" :key="binding.id" class="block-editor-shortcut">
                    <span class="block-editor-shortcut-keys">
                        <kbd v-for="key in displayKeys(binding.keys)" :key="key">{{ key }}</kbd>
                        <template v-if="binding.alt?.length">
                            <span class="block-editor-shortcut-or">{{ t('block_editor.shortcuts.or') }}</span>
                            <kbd v-for="key in displayKeys(binding.alt)" :key="key">{{ key }}</kbd>
                        </template>
                    </span>
                    <span>{{ t(binding.i18nKey) }}</span>
                </div>
            </div>
        </div>
    </KsDialog>

    <button
        type="button"
        class="block-editor-help"
        :aria-label="t('block_editor.shortcuts.title')"
        :title="t('block_editor.shortcuts.title')"
        data-test="block-editor-help"
        @click="emit('update:shortcutsOpen', true)"
    >
        <Keyboard class="block-editor-help-ico" />
        <kbd class="block-editor-help-kbd">?</kbd>
    </button>

    <div v-if="!shortcutsOpen" class="block-editor-footer" role="status" data-test="block-editor-footer">
        <span class="block-editor-footer-context">{{ footerContext }}</span>
        <span v-for="hint in footerHints" :key="hint.id" class="block-editor-footer-hint">
            <kbd v-for="key in displayKeys(hint.keys)" :key="key">{{ key }}</kbd>
            {{ t(hint.i18nKey) }}
        </span>
    </div>

    <Transition name="block-editor-undo">
        <div v-if="undoState" class="block-editor-undo" role="status" aria-live="polite">
            <span class="block-editor-undo-label">{{ undoState.label }}</span>
            <button
                type="button"
                class="block-editor-undo-btn"
                data-test="block-editor-undo"
                @click="emit('undo')"
            >
                {{ t("block_editor.undo") }}
            </button>
        </div>
    </Transition>
</template>

<style scoped lang="scss">
    .block-editor-shortcuts {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--ks-spacing-5);
    }

    .block-editor-shortcuts-col {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .block-editor-shortcuts-heading {
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: var(--ks-text-secondary);
    }

    .block-editor-shortcut {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-3);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
    }

    .block-editor-shortcut-keys {
        display: inline-flex;
        gap: var(--ks-spacing-1);
        flex-shrink: 0;
    }

    .block-editor-shortcut-keys kbd {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        background: var(--ks-bg-tag-inactive);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-sm);
        padding: 1px var(--ks-spacing-1);
        color: var(--ks-text-secondary);
        min-width: 18px;
        text-align: center;
    }

    .block-editor-shortcut-or {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        padding: 0 1px;
    }

    .block-editor-footer {
        position: absolute;
        left: 0;
        right: 0;
        bottom: 0;
        z-index: 9;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-4);
        height: 2.25rem;
        padding: 0 var(--ks-spacing-4);
        background: var(--ks-bg-surface);
        border-top: 1px solid var(--ks-border-subtle);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        overflow: hidden;
    }

    .block-editor-footer::after {
        content: "";
        position: absolute;
        top: 0;
        right: 0;
        bottom: 0;
        width: var(--ks-spacing-8);
        background: linear-gradient(to right, transparent, var(--ks-bg-surface));
        pointer-events: none;
    }

    .block-editor-footer-context {
        margin-right: auto;
        flex-shrink: 1;
        color: var(--ks-text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .block-editor-footer-hint {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        flex-shrink: 0;
        white-space: nowrap;
    }

    .block-editor-footer-hint kbd {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        background: var(--ks-bg-tag-inactive);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-sm);
        padding: 1px var(--ks-spacing-1);
        min-width: 18px;
        text-align: center;
        color: var(--ks-text-secondary);
    }

    .block-editor-help {
        position: absolute;
        right: var(--ks-spacing-4);
        bottom: calc(2.25rem + var(--ks-spacing-3));
        z-index: 10;
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        box-shadow: var(--ks-shadow-sm);
        color: var(--ks-text-secondary);
        cursor: pointer;
        transition: color 0.15s, border-color 0.15s, background-color 0.15s;
    }

    .block-editor-help:hover {
        color: var(--ks-text-primary);
        border-color: var(--ks-border-strong);
        background: var(--ks-bg-surface);
    }

    .block-editor-help:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: 2px;
    }

    .block-editor-help-ico {
        display: flex;
        font-size: 1rem;
    }

    .block-editor-help-kbd {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-xs);
        background: var(--ks-bg-tag-inactive);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-sm);
        padding: 1px var(--ks-spacing-1);
        min-width: 18px;
        text-align: center;
        color: var(--ks-text-secondary);
    }

    .block-editor-undo {
        position: absolute;
        bottom: calc(2.25rem + var(--ks-spacing-3));
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

    .block-editor-undo-label {
        white-space: nowrap;
    }

    .block-editor-undo-btn {
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

    .block-editor-undo-btn:hover {
        background: var(--ks-bg-hover);
    }

    .block-editor-undo-btn:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: 1px;
    }

    .block-editor-undo-enter-active,
    .block-editor-undo-leave-active {
        transition: opacity 0.18s ease, transform 0.18s ease;
    }

    .block-editor-undo-enter-from,
    .block-editor-undo-leave-to {
        opacity: 0;
        transform: translate(-50%, 8px);
    }

    @media (prefers-reduced-motion: reduce) {
        .block-editor-undo-enter-active,
        .block-editor-undo-leave-active {
            transition: none;
        }
    }
</style>

<script setup lang="ts">
    import Keyboard from "vue-material-design-icons/Keyboard.vue"
    import {useI18n} from "vue-i18n"
    import {displayKeys, type FooterHint} from "./shortcutHints"
    import type {BlockEditorKeyBinding, BlockEditorKeymapGroup} from "./keymap"

    defineProps<{
        shortcutsOpen: boolean
        shortcutGroups: {group: BlockEditorKeymapGroup; bindings: BlockEditorKeyBinding[]}[]
        footerContext: string
        footerHints: FooterHint[]
        undoState: {label: string} | null
    }>()

    const emit = defineEmits<{
        "update:shortcutsOpen": [open: boolean]
        undo: []
    }>()

    const {t} = useI18n()
</script>
