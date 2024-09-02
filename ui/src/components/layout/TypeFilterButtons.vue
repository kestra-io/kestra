<template>
    <el-select :model-value="value" @update:model-value="onInput" clearable multiple>
        <el-option v-for="item in options" :key="item.key" :label="item.name" :value="item.key" />
    </el-select>
</template>
<script>
    export default {
        props: {
            value: {type: Array, default: undefined}
        },
        emits: ["update:modelValue"],
        data() {
            return {
                options: [{name: "User only", key: "user"}, {name: "System only", key: "system"}]
            };
        },
        methods: {
            onInput(value) {
                this.$emit("update:modelValue", value);
            },
        },
    };
</script>
