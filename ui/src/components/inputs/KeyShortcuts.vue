<template>
    <KsDialog v-model="isKeyShortcutsDialogShown" top="25vh" headerClass="p-3" bodyClass="p-2">
        <template #header>
            <div class="d-flex align-items-center gap-2 fw-normal">
                <KsIcon size="xl">
                    <Keyboard />
                </KsIcon>
                <span class="fs-6">
                    {{ $t("editor_shortcuts.label") }}
                </span>
            </div>
        </template>

        <div class="d-flex flex-column gap-3 fw-normal">
            <div
                v-for="(command, i) in commands"
                :key="i"
                class="d-flex align-items-center gap-3"
            >
                <div class="d-flex align-items-center gap-2 keys">
                    <template v-for="(key, index) in command.keys" :key="index">
                        <KsTag>{{ key }}</KsTag>
                        <span
                            v-if="index < command.keys.length - 1"
                            class="fw-bold"
                        >+</span>
                    </template>
                </div>
                <div class="text-break">
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
