<template>
    <Editor
        :modelValue="localEditorValue"
        :navbar="false"
        :fullHeight="false"
        :input="true"
        lang="yaml"
        :placeholder="`Your ${root || 'value'} here...`"
        @update:model-value="editorInput"
        :largeSuggestions="false"
    />
</template>
<script setup lang="ts">
    import {collapseEmptyValues} from "./MixinTask";
    import Editor from "../../../../components/inputs/Editor.vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {computed, ref} from "vue";

    const props = defineProps({
        modelValue: {
            type: [String, Object],
            default: undefined
        },
        root: {
            type: String,
            default: undefined
        }
    });

    function editorInput(value: string) {
        localEditorValue.value = value;
        onInput(parseValue(value));
    }
    const emit = defineEmits(["update:modelValue"]);

    function onInput(value: any) {
        emit("update:modelValue", collapseEmptyValues(value));
    }

    const editorValue = computed(() => {
        if (typeof props.modelValue === "string") {
            return props.modelValue;
        }

        return props.modelValue !== undefined && props.modelValue !== null
            ? YAML_UTILS.stringify(props.modelValue)
            : "";
    })

    const localEditorValue = ref(editorValue.value)

    function parseValue(value: string) {
        if(value.match(/^\s*{{/)) {
            return value;
        }

        return YAML_UTILS.parse(value);
    }
</script>

<style scoped lang="scss">
:deep(.placeholder) {
    top: -7px !important;
}
</style>
