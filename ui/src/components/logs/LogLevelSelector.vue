<template>
    <el-select
        :model-value="value"
        @update:model-value="onInput"
        filterable
        :persistent="false"
        :placeholder="$t('revisions')"
    >
        <el-option
            v-for="item in levelOptions"
            :key="item"
            :label="item"
            :value="item"
        >
            {{ item }}
        </el-option>
    </el-select>
</template>
<script>
    export default {
        emits: ["update:modelValue"],
        data() {
            return {
                levelOptions: [
                    "TRACE",
                    "DEBUG",
                    "INFO",
                    "WARN",
                    "ERROR",
                ],
            };
        },
        props: {
            router: {
                type: Boolean,
                default: true
            },
            value: {
                type: String,
                default: "INFO"
            }
        },
        methods: {
            onInput(value) {
                this.$emit("update:modelValue", value);
            },
        },
    };
</script>
