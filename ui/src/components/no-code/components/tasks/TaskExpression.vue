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
    import {isDeepEqual, KsEditor, type KsEditorExposes} from "@kestra-io/design-system"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import {useEditorBindings} from "../../../../composables/useEditorBindings"
    import {useFocusedExpressionEditor} from "./useFocusedExpressionEditor"
    import {computed, ref, watch, type PropType} from "vue"

    const editorBindings = useEditorBindings()
    const editorRef = ref<KsEditorExposes>()
    const {onFocus} = useFocusedExpressionEditor(editorRef)

    const props = defineProps({
        modelValue: {
            type: [String, Object] as PropType<unknown>,
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

    let lastEmit: {value: unknown} | undefined

    function onInput(value: unknown) {
        lastEmit = {value: collapseEmptyValues(value)}
        emit("update:modelValue", lastEmit.value)
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

    // Only the echo of our own edit leaves the typed text alone, so the keystrokes the value does not
    // reproduce verbatim (trailing spaces, 0.50) survive; a value written from outside replaces it.
    watch(() => props.modelValue, () => {
        if (!lastEmit || !isDeepEqual(props.modelValue, lastEmit.value)) {
            localEditorValue.value = editorValue.value
        }
    })

    function parseValue(value: string) {
        if(value.match(/^\s*{{/)) {
            return value
        }

        return YAML_UTILS.parse(value)
    }
</script>
