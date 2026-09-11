<template>
    <KsEditor
        class="diff-view"
        data-test="copilot-diff"
        v-bind="editorBindings"
        :options="{diffSideBySide: false}"
        :modelValue="newValue"
        :original="oldValue"
        readOnly
        lang="yaml"
    />
</template>

<script setup lang="ts">
    import {KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../../composables/useEditorBindings"

    defineProps<{
        /** The current content ("before"). Empty when there's nothing to diff against (e.g. a brand-new
         *  flow) — Monaco's diff editor then renders every line of `newValue` as an addition. */
        oldValue: string
        newValue: string
    }>()

    const editorBindings = useEditorBindings()
</script>

<style scoped>
    /* KsEditor's own height rule, `:not(.namespace-defaults, .kel-drawer__body) > .ks-editor`, is
       (0,2,0) — :not() contributes the specificity of its most specific argument. A bare scoped
       `.diff-view` compiles to `.diff-view[data-v-...]`, also (0,2,0): a tie, decided by source order
       rather than lost outright. `.ks-editor.diff-view[data-v-...]` is (0,3,0) and wins cleanly. */
    .ks-editor.diff-view {
        height: 20rem;
    }
</style>
