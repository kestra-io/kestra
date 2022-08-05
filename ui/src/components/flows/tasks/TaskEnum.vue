<template>
    <el-select
        :model-value="values"
        @update:model-value="onInput"
        filterable
        clearable
        :persistent="false"
        placeholder=" "
    >
        <el-option
            v-for="item in schema.enum"
            :key="item"
            :label="item"
            :value="item"
        />
    </el-select>
</template>
<script>
    import Task from "./Task";
    export default {
        mixins: [Task],
    };
</script>
