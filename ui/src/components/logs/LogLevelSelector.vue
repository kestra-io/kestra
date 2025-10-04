<template>
    <el-select
        :modelValue="value"
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
<script lang="ts">
    import Logs from "../../utils/logs.js";

    export default {
        emits: ["update:modelValue"],
        data() {
            return {
                levelOptions: Logs.LEVELS,
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
