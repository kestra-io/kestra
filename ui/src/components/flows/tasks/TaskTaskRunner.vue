<template>
    <task-task @update:model-value="$emit('update:modelValue', $event)" v-bind="$attrs" :section="SECTION.TASK_RUNNERS" />
</template>
<script setup>
    import {SECTIONS as SECTION} from "../../../utils/constants.js";
    import TaskTask from "./TaskTask.vue";

    defineEmits(["update:modelValue"]);
</script>
