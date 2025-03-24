<template>
    <el-form label-position="top">
        <task-object
            v-if="schema"
            :model-value="modelValue"
            @update:model-value="onInput"
            :schema="schema.properties"
            :definitions="schema.definitions"
        />
    </el-form>
</template>
<script>
    import Task from "./Task";

    export default {
        mixins: [Task],
    };
</script>

<style lang="scss" scoped>
    :deep(.el-form-item) {
        margin-bottom: 1rem;
    }

    :deep(.el-form-item__content) {
        .el-form-item {
            width: 100%;
        }
    }
</style>
