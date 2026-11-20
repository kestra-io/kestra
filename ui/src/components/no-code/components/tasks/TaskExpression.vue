<template>
    <KsEditor
        v-bind="editorBindings"
        :modelValue="localEditorValue"
        :navbar="false"
        :options="{fullHeight: false, largeSuggestions: false}"
        :inline="true"
        lang="yaml"
        :placeholder="$t('no_code.expression_placeholder', {field: root || 'value'})"
        @update:model-value="editorInput"
    />
</template>

<script setup lang="ts">
    import isEqual from "lodash/isEqual"
    import {collapseEmptyValues} from "./MixinTask"
    import {KsEditor} from "@kestra-io/design-system"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import {useEditorBindings} from "../../../../composables/useEditorBindings"
    import {computed, ref, watch} from "vue"

    const editorBindings = useEditorBindings()

    const props = defineProps({
        modelValue: {
            type: [String, Object],
            default: undefined,
        },
        root: {
            type: String,
            default: undefined,
        },
    })

    function editorInput(value: string) {
        localEditorValue.value = value
        onInput(parseValue(value))
    }
    const emit = defineEmits(["update:modelValue"])

    function onInput(value: any) {
        emit("update:modelValue", collapseEmptyValues(value))
    }

    const editorValue = computed(() => {
        if (typeof props.modelValue === "string") {
            return props.modelValue
        }

        return props.modelValue !== undefined && props.modelValue !== null
            ? YAML_UTILS.stringify(props.modelValue)
            : ""
    })

    const localEditorValue = ref(editorValue.value)

    // Adopt a value the parent sets, but leave the text already being typed alone: its parse is what was emitted.
    watch(() => props.modelValue, (value) => {
        if (emittedBy(localEditorValue.value, value)) return
        localEditorValue.value = editorValue.value
    }, {deep: true})

    // A text that is not valid YAML was never emitted as such, so it cannot be the echo of what was typed.
    function emittedBy(text: string, value: unknown) {
        try {
            return isEqual(collapseEmptyValues(parseValue(text)), value)
        } catch {
            return false
        }
    }

    function parseValue(value: string) {
        if(value.match(/^\s*{{/)) {
            return value
        }

        return YAML_UTILS.parse(value)
    }
</script>
