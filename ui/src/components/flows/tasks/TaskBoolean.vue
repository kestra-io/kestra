<template>
    <el-checkbox
        :model-value="values"
        @update:model-value="onInput"
        :value="true"
    />
</template>

<script>
    import Task from "./Task";

    export default {
        mixins: [Task],
    };
</script>
