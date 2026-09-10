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
    /* KsEditor's own `.ks-editor { height: 100% }` rule is less specific than the scoped `.diff-view`
       selector below (Vue adds a `[data-v-...]` attribute to it), so it would otherwise be overridden —
       target both classes together so this rule wins instead. */
    .ks-editor.diff-view {
        height: 20rem;
    }
</style>
