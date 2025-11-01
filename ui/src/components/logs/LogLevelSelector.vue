<template>
    <el-select
        :modelValue="value"
        @update:model-value="emit('update:modelValue', $event)"
        filterable
        :persistent="false"
        :placeholder="$t('revisions')"
    >
        <el-option
            v-for="item in LEVELS"
            :key="item"
            :label="item"
            :value="item"
        >
            {{ item }}
        </el-option>
    </el-select>
</template>
<script setup lang="ts">
    const emit = defineEmits<{(e: "update:modelValue", value: string): void;}>()

    withDefaults(defineProps<{
        value?: string,
        router?: boolean
    }>(), {
        value: "INFO",
        router: true
    })

    const LEVELS = [
        "TRACE",
        "DEBUG",
        "INFO",
        "WARN",
        "ERROR",
    ];
</script>
