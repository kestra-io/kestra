<template>
    <TaskObject
        :model-value="modelValue"
        :schema
        :definitions
        :properties="computedProperties"
        merge
        @update:model-value="onInput"
    />
</template>

<script setup>
    import TaskObject from "./TaskObject.vue";
</script>

<script>
    import Task from "./Task";

    export default {
        mixins: [Task],
        computed: {
            computedProperties() {
                const type = this.schema.$ref.split("/").pop();
                return this.definitions[type]?.properties;
            },
        },
    };
</script>
