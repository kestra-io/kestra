<template>
    <KsEditor
        ref="editorRef"
        v-bind="editorBindings"
        :modelValue="localEditorValue"
        :navbar="false"
        :options="{fullHeight: false, largeSuggestions: false}"
        :inline="true"
        lang="yaml"
        :placeholder="$t('no_code.expression_placeholder', {field: root || 'value'})"
        @update:model-value="editorInput"
        @focus="onFocus"
        @focusout="onBlur"
    />
</template>

<script setup lang="ts">
    import {collapseEmptyValues} from "./MixinTask"
    import {KsEditor, type KsEditorExposes} from "@kestra-io/design-system"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import {useEditorBindings} from "../../../../composables/useEditorBindings"
    import {useFocusedExpressionEditor} from "./useFocusedExpressionEditor"
    import {computed, ref} from "vue"

    const editorBindings = useEditorBindings()
    const editorRef = ref<KsEditorExposes>()
    const {onFocus, onBlur} = useFocusedExpressionEditor(editorRef)

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

    function parseValue(value: string) {
        if(value.match(/^\s*{{/)) {
            return value
        }

        return YAML_UTILS.parse(value)
    }
</script>
