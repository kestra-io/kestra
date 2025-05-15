<template>
    <AnyOfContent
        :model-value="modelValue"
        :schema="schema"
        :definitions="definitions"
        @update:model-value="onInput"
    />
</template>

<script setup>
    import AnyOfContent from "./AnyOfContent.vue";
    import Task from "./Task";

    defineOptions({
        mixins: [Task]
    });
</script>
