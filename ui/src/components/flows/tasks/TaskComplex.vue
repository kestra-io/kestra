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

    import {
        BREADCRUMB_INJECTION_KEY,
        PANEL_INJECTION_KEY,
    } from "../../code/injectionKeys";

    export default {
        mixins: [Task],
        inject: {
            panel: {from: PANEL_INJECTION_KEY},
            breadcrumbs: {from: BREADCRUMB_INJECTION_KEY},
        },
        computed: {
            computedProperties() {
                const type = this.schema.$ref.split("/").pop();
                return this.definitions[type]?.properties;
            },
        },
    };
</script>
