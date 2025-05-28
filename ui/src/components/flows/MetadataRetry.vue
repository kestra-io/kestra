<template>
    <TaskWrapper>
        <template #tasks>
            <span class="label">{{ props.label }}</span>
            <div class="mt-1 mb-2 wrapper">
                <TaskAnyOf
                    :model-value="value"
                    :schema
                    :definitions
                    @update:model-value="emits('update:modelValue', $event)"
                    @any-of-type="changeType"
                />
            </div>
        </template>
    </TaskWrapper>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import TaskAnyOf from "./tasks/TaskAnyOf.vue";
    import TaskWrapper from "./tasks/TaskWrapper.vue";

    const emits = defineEmits(["update:modelValue"]);

    const props = defineProps({
        modelValue: {type: Object, default: () => ({})},
        label: {type: String, required: true},
    });

    const changeType = (v: any) => {
        if (!v) return;

        const type = definitions[v].properties.type.const;
        value.value = type ? {type} : {};
    };

    const value = computed({
        get: () => props.modelValue,
        set: (v) => emits("update:modelValue", v),
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
