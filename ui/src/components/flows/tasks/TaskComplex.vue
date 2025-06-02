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

<script>
    import Task from "./Task";
    import TaskObject from "./TaskObject.vue";

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
        components: {TaskObject},
        computed: {
            computedProperties() {
                const type = this.schema.$ref.split("/").pop();
                return this.definitions[type]?.properties;
            },
        },
    };
</script>
