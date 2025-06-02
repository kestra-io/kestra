<template>
    <TaskWrapper>
        <template #tasks>
            <TaskObjectField
                :field-key="label"
                :schema
                :definitions
                :task="{SLA: value}"
                :model-value="value"
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

    defineProps<{
        label: string
    }>();

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
                    const: ["MAX_DURATION"],
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
