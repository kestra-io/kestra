<template>
    <TaskWrapper>
        <template #tasks>
            <TaskObjectField
                :field-key="label"
                v-model="value"
                :schema
                :definitions
                :task="{[label]: value}"
                @update:model-value="(val) => emit('update:modelValue', val)"
            />
        </template>
    </TaskWrapper>
</template>

<script setup lang="ts">
    import TaskWrapper from "./tasks/TaskWrapper.vue";
    import TaskObjectField from "./tasks/TaskObjectField.vue";

    const value = defineModel({
        type: Object,
        default: () => ({}),
    });

    const emit = defineEmits<{
        (e: "update:modelValue", value: any): void;
    }>();

    defineProps({
        label: {type: String, required: true},
        schema: {
            type: Object,
            default: () => ({}),
        },
        definitions: {
            type: Object,
            default: () => ({}),
        },
    });
</script>

<style scoped lang="scss">
@import "../code/styles/code.scss";
</style>
