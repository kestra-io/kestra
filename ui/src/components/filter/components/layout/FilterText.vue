<template>
    <div class="p-3">
        <el-input
            :modelValue="textValue"
            :placeholder="`Enter ${label.toLowerCase()}`"
            @update:model-value="emits('update:textValue', $event)"
        />
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        label: string;
        textValue: string;
    }>();

    const emits = defineEmits<{
        "update:textValue": [value: string];
    }>();
</script>

<style lang="scss" scoped>
    :deep(.el-input__inner) {
        &::placeholder {
            color: var(--ks-content-tertiary);
            font-size: 14px;
        }
    }
</style>