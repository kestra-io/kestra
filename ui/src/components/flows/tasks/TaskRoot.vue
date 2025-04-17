<template>
    <TaskObject
        v-if="schema"
        :model-value="modelValue"
        @update:model-value="onInput"
        :schema="schema.properties"
        :definitions="schema.definitions"
    />
</template>
<script>
    import Task from "./Task";
    import TaskObject from "./TaskObject.vue";

    export default {
        inheritAttrs: false,
        components: {
            TaskObject,
        },
        mixins: [Task],
    };
</script>

<style lang="scss">
    .el-form-item {
        margin-bottom: 1rem;
    }

    .el-form-item__content {
        .el-form-item {
            width: 100%;
        }
    }
</style>
