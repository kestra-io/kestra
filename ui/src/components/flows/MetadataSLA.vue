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
    });

    // FIXME: Properly fetch and parse the schema and definitions
    const schema = {
        anyOf: [
            {
                $ref: "#/definitions/io.kestra.core.models.flows.sla.types.ExecutionAssertionSLA-1",
            },
            {
                $ref: "#/definitions/io.kestra.core.models.flows.sla.types.MaxDurationSLA-1",
            },
        ],
    };

    const definitions = {
        "io.kestra.core.models.flows.sla.types.ExecutionAssertionSLA-1": {
            type: "object",
            properties: {
                id: {
                    type: "string",
                    minLength: 1,
                },
                type: {
                    type: "constant",
                    const: "EXECUTION_ASSERTION",
                },
                assert: {
                    type: "string",
                    minLength: 1,
                },
                behavior: {
                    type: "string",
                    enum: ["FAIL", "CANCEL", "NONE"],
                },
                labels: {
                    anyOf: [
                        {
                            type: "array",
                            items: {},
                        },
                        {
                            type: "object",
                        },
                    ],
                },
            },
            required: ["type", "id", "assert", "behavior"],
        },
        "io.kestra.core.models.flows.sla.types.MaxDurationSLA-1": {
            type: "object",
            properties: {
                id: {
                    type: "string",
                    minLength: 1,
                },
                type: {
                    type: "constant",
                    const: "MAX_DURATION",
                },
                behavior: {
                    type: "string",
                    enum: ["FAIL", "CANCEL", "NONE"],
                },
                duration: {
                    type: "string",
                    format: "duration",
                },
                labels: {
                    anyOf: [
                        {
                            type: "array",
                            items: {},
                        },
                        {
                            type: "object",
                        },
                    ],
                },
            },
            required: ["type", "id", "behavior", "duration"],
        },
    };
</script>

<style scoped lang="scss">
@import "../code/styles/code.scss";
</style>
