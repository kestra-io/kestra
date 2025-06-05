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
        inheritAttrs: false,
        mixins: [Task],
        computed: {
            computedProperties() {
                if(!this.schema?.allOf && !this.schema?.$ref) {
                    return this.schema?.properties || {};
                }
                const schemas = this.schema.allOf ?? [this.schema];
                return schemas.reduce((acc, item) => {
                    if (item.$ref) {
                        const type = item.$ref.split("/").pop();
                        return {
                            ...acc,
                            ...this.definitions[type]?.properties
                        };
                    }
                    return {...acc, ...item.properties};
                }, {});
            },
        },
    };
</script>
