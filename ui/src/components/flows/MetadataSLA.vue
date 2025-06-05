<template>
    <TaskWrapper>
        <template #tasks>
            <TaskObjectField
                v-model="value[0]"
                :field-key="label"
                :schema
                :definitions
                :task="{[label]: value}"
                @update:model-value="(val) => emit('update:modelValue', val? [val] : undefined)"
            />
        </template>
    </TaskWrapper>
</template>

<script setup lang="ts">
    import TaskWrapper from "./tasks/TaskWrapper.vue";
    import TaskObjectField from "./tasks/TaskObjectField.vue";

    const value = defineModel<any[]>({
        type: Array,
        default: () => ([]),
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
