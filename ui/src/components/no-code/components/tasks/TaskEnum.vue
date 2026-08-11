<template>
    <KsSelect
        :modelValue="values"
        @update:model-value="onInput"
        filterable
        clearable
        :placeholder="`Choose a${/^[aeiou]/i.test(root || '') ? 'n' : ''} ${root?.split('.').pop() || 'value'}`"
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
    import {computed} from "vue"
    import {collapseEmptyValues} from "../utils/collapseEmptyValues"

    const props = withDefaults(defineProps<{
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

    const values = computed(() => props.modelValue ?? (props.schema as Record<string, unknown> | undefined)?.default)

    function onInput(value: unknown) {
        emit("update:modelValue", collapseEmptyValues(value))
    }
</script>

<style scoped lang="scss">
:deep(.kel-input__inner) {
    &::placeholder {
        color: var(--ks-text-dim);
    }
}

:deep(.kel-select__suffix) {
    display: flex !important;
}
</style>
