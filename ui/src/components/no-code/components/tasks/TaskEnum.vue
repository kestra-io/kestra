<template>
    <el-select
        :modelValue="values"
        @update:model-value="onInput"
        filterable
        clearable
        :persistent="false"
        :placeholder="`Choose a${/^[aeiou]/i.test(root || '') ? 'n' : ''} ${root?.split('.').pop() || 'value'}`"
    >
        <el-option
            v-for="item in schema.enum"
            :key="item"
            :label="item"
            :value="item"
        />
    </el-select>
</template>
<script>
    import Task from "./MixinTask";
    export default {
        mixins: [Task],
    };
</script>

<style scoped lang="scss">
:deep(.el-input__inner) {
    &::placeholder {
        color: var(--ks-content-tertiary);
    }
}

:deep(.el-select__suffix) {
    display: flex !important;
}
</style>
