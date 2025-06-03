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
    });


    // FIXME: Properly fetch and parse the schema and definitions
    const schema = {
        anyOf: [
            {
                $ref: "#/definitions/kestra_frontend.core.models.tasks.retrys.Constant-2",
            },
            {
                $ref: "#/definitions/kestra_frontend.core.models.tasks.retrys.Exponential-2",
            },
            {
                $ref: "#/definitions/kestra_frontend.core.models.tasks.retrys.Random-2",
            },
        ],
    };

    const definitions = {
        "kestra_frontend.core.models.tasks.retrys.Random-2": {
            type: "object",
            properties: {
                behavior: {
                    type: "string",
                    enum: ["RETRY_FAILED_TASK", "CREATE_NEW_EXECUTION"],
                    default: "RETRY_FAILED_TASK",
                    markdownDescription: "Default value is : `RETRY_FAILED_TASK`",
                },
                maxAttempt: {
                    type: "integer",
                    minimum: 1,
                },
                maxDuration: {
                    type: "string",
                    format: "duration",
                },
                maxInterval: {
                    type: "string",
                    format: "duration",
                },
                minInterval: {
                    type: "string",
                    format: "duration",
                },
                type: {
                    type: "constant",
                    const: "random",
                },
                warningOnRetry: {
                    type: "boolean",
                    default: false,
                    markdownDescription: "Default value is : `false`",
                },
            },
            required: ["type", "maxInterval", "minInterval"],
        },
        "kestra_frontend.core.models.tasks.retrys.Exponential-2": {
            type: "object",
            properties: {
                behavior: {
                    type: "string",
                    enum: ["RETRY_FAILED_TASK", "CREATE_NEW_EXECUTION"],
                    default: "RETRY_FAILED_TASK",
                    markdownDescription: "Default value is : `RETRY_FAILED_TASK`",
                },
                delayFactor: {
                    type: "number",
                },
                interval: {
                    type: "string",
                    format: "duration",
                },
                maxAttempt: {
                    type: "integer",
                    minimum: 1,
                },
                maxDuration: {
                    type: "string",
                    format: "duration",
                },
                maxInterval: {
                    type: "string",
                    format: "duration",
                },
                type: {
                    type: "constant",
                    const: "exponential",
                },
                warningOnRetry: {
                    type: "boolean",
                    default: false,
                    markdownDescription: "Default value is : `false`",
                },
            },
            required: ["type", "interval", "maxInterval"],
        },
        "kestra_frontend.core.models.tasks.retrys.Constant-2": {
            type: "object",
            properties: {
                behavior: {
                    type: "string",
                    enum: ["RETRY_FAILED_TASK", "CREATE_NEW_EXECUTION"],
                    default: "RETRY_FAILED_TASK",
                    markdownDescription: "Default value is : `RETRY_FAILED_TASK`",
                },
                interval: {
                    type: "string",
                    format: "duration",
                },
                maxAttempt: {
                    type: "integer",
                    minimum: 1,
                },
                maxDuration: {
                    type: "string",
                    format: "duration",
                },
                type: {
                    type: "constant",
                    const: "constant",
                },
                warningOnRetry: {
                    type: "boolean",
                    default: false,
                    markdownDescription: "Default value is : `false`",
                },
            },
            required: ["type", "interval"],
        },
    };
</script>

<style scoped lang="scss">
@import "../code/styles/code.scss";
</style>
