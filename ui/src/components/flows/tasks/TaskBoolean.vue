<template>
    <el-checkbox :model-value="modelValue" @update:model-value="onInput" />
</template>

<script>
    import Task from "./Task";

    export default {
        mixins: [Task],
    };
</script>
