<template>
    <TaskWrapper>
        <template #tasks>
            <span class="label">{{ props.label }}</span>
            <div class="mt-1 mb-2 wrapper">
                <TaskAnyOf
                    :model-value="value"
                    :schema
                    :definitions
                    @update:model-value="emits('update:modelValue', [$event])"
                    @any-of-type="changeType"
                />
            </div>
        </template>
    </TaskWrapper>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import TaskAnyOf from "./tasks/TaskAnyOf.vue";

    const emits = defineEmits(["update:modelValue"]);

    const props = defineProps({
        modelValue: {type: Array, default: () => []},
        label: {type: String, required: true},
    });

    const changeType = (v: any) => {
        if (!v) return;

        const type = definitions[v].properties.type.enum[0];
        value.value = type ? {type} : {};
    };

    const value = computed({
        get: () => (props.modelValue.length > 0 ? props.modelValue[0] : {}),
        set: (v) => emits("update:modelValue", [v]),
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
