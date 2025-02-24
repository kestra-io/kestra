<template>
    <Splitpanes>
        <Pane>
            <EditorWrapper>
                <template #absolute>
                    <KeyShortcuts />
                </template>
            </EditorWrapper>
        </Pane>
        <Pane />
    </Splitpanes>
</template>

<script setup lang="ts">
    import "splitpanes/dist/splitpanes.css"
    import {Splitpanes, Pane} from "splitpanes"

    import EditorWrapper from "../inputs/EditorWrapper.vue";
    import KeyShortcuts from "../inputs/KeyShortcuts.vue";
</script>