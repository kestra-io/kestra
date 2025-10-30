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
<script setup lang="ts">
    const emit = defineEmits<{(e: 'update:modelValue', value: string): void;}>()

    withDefaults(defineProps<{
        value?: string,
        router?: boolean
    }>(), {
        value: "INFO",
        router: true
    })

    const levelOptions = [
        "TRACE",
        "DEBUG",
        "INFO",
        "WARN",
        "ERROR",
    ];

    const onInput = (value: string) => {
        emit("update:modelValue", value);
    };
</script>
