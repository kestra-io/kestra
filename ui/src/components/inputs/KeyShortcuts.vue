<template>
    <KsDialog
        v-model="isKeyShortcutsDialogShown"
        scrollable
    >
        <template #header>
            <div class="key-shortcuts__header">
                <KsIcon size="xl">
                    <Keyboard />
                </KsIcon>
                <span class="key-shortcuts__title">
                    {{ $t("editor_shortcuts.label") }}
                </span>
            </div>
        </template>

        <div class="key-shortcuts__list">
            <div
                v-for="(command, i) in commands"
                :key="i"
                class="key-shortcuts__row"
            >
                <div class="key-shortcuts__keys">
                    <template v-for="(key, index) in command.keys" :key="index">
                        <KsTag>{{ key }}</KsTag>
                        <span
                            v-if="index < command.keys.length - 1"
                            class="key-shortcuts__plus"
                        >+</span>
                    </template>
                </div>
                <div class="key-shortcuts__description">
                    {{ $t(command.description) }}
                </div>
            </div>
        </div>
    </KsDialog>
</template>

<script setup lang="ts">
    import Keyboard from "vue-material-design-icons/Keyboard.vue"
    import {useKeyShortcuts} from "../../utils/useKeyShortcuts"

    const {isKeyShortcutsDialogShown} = useKeyShortcuts()

    const commands = [
        {
            keys: ["Ctrl", "SPACE"],
            description: "editor_shortcuts.trigger_autocompletion",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "p"],
            description: "editor_shortcuts.command_palette",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "s"],
            description: "editor_shortcuts.save_flow",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "e"],
            description: "editor_shortcuts.execute_flow",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "⌥ Option/Alt", "Shift", "K"],
            description: "editor_shortcuts.toggle_ai_agent",
        },
        {
            keys: ["⌥ Option/Alt", "↑", "↓"],
            description: "editor_shortcuts.move_line",
        },
        {
            keys: ["⇧ Shift", "⌥ Option/Alt", "↑", "↓"],
            description: "editor_shortcuts.duplicate_cursor",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "k", "l"],
            description: "editor_shortcuts.fold_unfold",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "/"],
            description: "editor_shortcuts.comment_uncomment",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "k", "c"],
            description: "editor_shortcuts.comment",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "k", "u"],
            description: "editor_shortcuts.uncomment",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "↓"],
            description: "editor_shortcuts.decrease_fontsize",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "↑"],
            description: "editor_shortcuts.increase_fontsize",
        },
        {
            keys: ["⌘ Cmd/Ctrl", "0"],
            description: "editor_shortcuts.reset_fontsize",
        },
    ]
</script>

<style scoped lang="scss">
.key-shortcuts__header {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    font-weight: 400;
}

.key-shortcuts__title {
    font-size: var(--ks-font-size-sm);
}

.key-shortcuts__list {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-4);
    font-weight: 400;
}

.key-shortcuts__row {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-4);
}

.key-shortcuts__keys {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
}

.key-shortcuts__plus {
    font-weight: 700;
}

.key-shortcuts__description {
    word-break: break-word;
    overflow-wrap: break-word;
}

.kel-tag {
    background-color: var(--ks-bg-tag);
    color: var(--ks-text-primary);
    font-size: var(--kel-tag-font-size);
    text-transform: capitalize;
    font-weight: 500;
    border: 1px solid var(--ks-border-default);
    border-radius: 4px;
    display: inline-block;
    padding: 6px 10px;
}

.kel-tag::after {
    content: attr(data-content);
    text-transform: none;
}
</style>
