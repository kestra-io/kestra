<template>
    <TaskTask @update:model-value="$emit('update:modelValue', $event)" v-bind="$attrs" :section="SECTIONS.TASK_RUNNERS" />
</template>
<script setup>
    import {SECTIONS} from "@kestra-io/ui-libs";
    import TaskTask from "./TaskTask.vue";

    defineEmits(["update:modelValue"]);
</script>
