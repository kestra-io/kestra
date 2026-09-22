<template>
    <KsSelect
        :modelValue="modelValue"
        @update:model-value="onInput"
        filterable
        :clearable="!required"
        :placeholder="$t('no_code.choose_placeholder', {field: root?.split('.').pop() || 'value'})"
    >
        <KsOption
            v-for="item in (schema?.enum as string[])"
            :key="item"
            :label="item"
            :value="item"
        />
    </KsSelect>
</template>

<script setup lang="ts">
    import {collapseEmptyValues} from "../utils/collapseEmptyValues"

    withDefaults(defineProps<{
        modelValue?: object | string | number | boolean | unknown[]
        schema?: Record<string, unknown>
        required?: boolean
        task?: Record<string, unknown>
        root?: string
        definitions?: Record<string, unknown>
    }>(), {
        modelValue: undefined,
        schema: undefined,
        required: false,
        task: undefined,
        root: undefined,
        definitions: undefined,
    })

    const emit = defineEmits<{
        "update:modelValue": [value: unknown]
    }>()

    function onInput(value: unknown) {
        emit("update:modelValue", collapseEmptyValues(value))
    }
</script>

<style scoped lang="scss">
:deep(.kel-select__suffix) {
    display: flex !important;
}
</style>
