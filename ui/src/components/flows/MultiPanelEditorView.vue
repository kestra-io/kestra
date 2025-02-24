<template>
    <Splitpanes>
        <Pane>
            <Editor>
                <template #absolute>
                    <KeyShortcuts />
                </template>
            </Editor>
        </Pane>
        <Pane />
    </Splitpanes>
</template>

<script setup lang="ts">
    import "splitpanes/dist/splitpanes.css"
    import {Splitpanes, Pane} from "splitpanes"

    import Editor from "../inputs/Editor.vue";
    import KeyShortcuts from "../inputs/KeyShortcuts.vue";
</script>