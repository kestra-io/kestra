<template>
    <KsEditor
        class="diff-view"
        data-test="copilot-diff"
        :options="{diffSideBySide: false}"
        :modelValue="newValue"
        :original="oldValue"
        readOnly
        lang="yaml"
    />
</template>

<script setup lang="ts">
    import {KsEditor} from "@kestra-io/design-system"

    defineProps<{
        /** The current content ("before"). Empty when there's nothing to diff against (e.g. a brand-new
         *  flow) — Monaco's diff editor then renders every line of `newValue` as an addition. */
        oldValue: string
        newValue: string
    }>()
</script>

<style scoped>
    /* KsEditor's own stylesheet sets `.ks-editor { height: 100% }` at the same specificity as a bare
       `.diff-view` class would, and wins the cascade tie outside a full-height layout (e.g. inside a
       KsMessageBox, which sizes to its content) — target both classes together to win outright. */
    .ks-editor.diff-view {
        height: 20rem;
    }
</style>
