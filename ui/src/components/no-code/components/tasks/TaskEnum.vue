<template>
    <KsSelect
        :modelValue="values"
        @update:model-value="onInput"
        filterable
        clearable
        :placeholder="`Choose a${/^[aeiou]/i.test(root || '') ? 'n' : ''} ${root?.split('.').pop() || 'value'}`"
    >
        <KsOption
            v-for="item in schema.enum"
            :key="item"
            :label="item"
            :value="item"
        />
    </KsSelect>
</template>
<script>
    import Task from "./MixinTask"
    export default {
        mixins: [Task],
    }
</script>

<style scoped lang="scss">
:deep(.kel-input__inner) {
    &::placeholder {
        color: var(--ks-content-tertiary);
    }
}

:deep(.kel-select__suffix) {
    display: flex !important;
}
</style>
