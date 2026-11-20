<template>
    <KsEditor
        ref="editorRef"
        v-bind="editorBindings"
        :modelValue="localEditorValue"
        :navbar="false"
        :options="{fullHeight: false, largeSuggestions: false}"
        :inline="true"
        :readOnly="disabled"
        lang="yaml"
        :placeholder="$t('no_code.expression_placeholder', {field: root || 'value'})"
        @update:model-value="editorInput"
        @focus="onFocus"
    />
</template>

<script setup lang="ts">
    import {collapseEmptyValues} from "./MixinTask"
    import {KsEditor, isDeepEqual, type KsEditorExposes} from "@kestra-io/design-system"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import {useEditorBindings} from "../../../../composables/useEditorBindings"
    import {useFocusedExpressionEditor} from "./useFocusedExpressionEditor"
    import {computed, ref, watch, type PropType} from "vue"

    const editorBindings = useEditorBindings()
    const editorRef = ref<KsEditorExposes>()
    const {onFocus} = useFocusedExpressionEditor(editorRef)

    const props = defineProps({
        modelValue: {
            type: [String, Number, Boolean, Object] as PropType<unknown>,
        },
        root: {
            type: String,
            default: undefined,
        },
        disabled: {
            type: Boolean,
            default: false,
        },
    })

    function editorInput(value: string) {
        localEditorValue.value = value
        onInput(parseValue(value))
    }
    const emit = defineEmits(["update:modelValue"])

    function onInput(value: unknown) {
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
            return isDeepEqual(collapseEmptyValues(parseValue(text)), value)
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
