<template>
    <span class="label">{{ props.label }}</span>
    <div class="mt-1 mb-2 wrapper">
        <TaskAnyOf
            :model-value="props.modelValue"
            :schema
            :definitions
            @update:model-value="emits('update:modelValue', $event)"
            @any-of-type="emits('update:modelValue', undefined)"
            wrap
        />
    </div>
</template>

<script setup lang="ts">
    import TaskAnyOf from "./tasks/TaskAnyOf.vue";

    const emits = defineEmits(["update:modelValue"]);

    const props = defineProps({
        modelValue: {type: Object, default: () => ({})},
        label: {type: String, required: true},
    });

    // FIXME: Properly fetch and parse the schema and definitions
    const schema = {
        anyOf: [
            {
                $ref: "#/definitions/io.kestra.core.models.tasks.retrys.Constant-2",
            },
            {
                $ref: "#/definitions/io.kestra.core.models.tasks.retrys.Exponential-2",
            },
            {
                $ref: "#/definitions/io.kestra.core.models.tasks.retrys.Random-2",
            },
        ],
    };

    const definitions = {
        "io.kestra.core.models.tasks.retrys.Random-2": {
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
                    type: "string",
                    enum: ["exponential"],
                },
                warningOnRetry: {
                    type: "boolean",
                    default: false,
                    markdownDescription: "Default value is : `false`",
                },
            },
            required: ["type", "maxInterval", "minInterval"],
        },
        "io.kestra.core.models.tasks.retrys.Exponential-2": {
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
                    type: "string",
                    enum: ["exponential"],
                },
                warningOnRetry: {
                    type: "boolean",
                    default: false,
                    markdownDescription: "Default value is : `false`",
                },
            },
            required: ["type", "interval", "maxInterval"],
        },
        "io.kestra.core.models.tasks.retrys.Constant-2": {
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
                    type: "string",
                    enum: ["constant"],
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
