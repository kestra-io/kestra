<template>
    <KsDialog
        :modelValue="open"
        :title="$t('block_editor.shortcuts.title')"
        data-test="block-editor-shortcuts"
        @update:modelValue="(next?: boolean) => emit('update:open', next ?? false)"
    >
        <div class="block-editor-shortcuts">
            <div v-for="group in groups" :key="group.group" class="block-editor-shortcuts-col">
                <span class="block-editor-shortcuts-heading">{{ $t(`block_editor.shortcuts.group_${group.group}`) }}</span>
                <div v-for="binding in group.bindings" :key="binding.id" class="block-editor-shortcut">
                    <span class="block-editor-shortcut-keys">
                        <kbd v-for="key in displayKeys(binding.keys)" :key="key">{{ key }}</kbd>
                        <template v-if="binding.alt?.length">
                            <span class="block-editor-shortcut-or">{{ $t('block_editor.shortcuts.or') }}</span>
                            <kbd v-for="key in displayKeys(binding.alt)" :key="key">{{ key }}</kbd>
                        </template>
                    </span>
                    <span>{{ $t(binding.i18nKey) }}</span>
                </div>
            </div>
        </div>
    </KsDialog>
</template>

<script setup lang="ts">
    import {displayKeys} from "./shortcutHints"
    import type {BlockEditorKeyBinding, BlockEditorKeymapGroup} from "./keymap"

    defineProps<{
        open: boolean
        groups: {group: BlockEditorKeymapGroup; bindings: BlockEditorKeyBinding[]}[]
    }>()

    const emit = defineEmits<{
        "update:open": [open: boolean]
    }>()
</script>

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
</style>
