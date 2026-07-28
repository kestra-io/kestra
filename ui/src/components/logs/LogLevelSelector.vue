<template>
    <KsSelect
        :modelValue="value"
        @update:model-value="emit('update:modelValue', $event)"
        filterable
        :fit="fit"
        :placeholder="$t('revisions')"
    >
        <KsOption
            v-for="item in LEVELS"
            :key="item"
            :label="item"
            :value="item"
        >
            {{ item }}
        </KsOption>
    </KsSelect>
</template>
<script setup lang="ts">
    const emit = defineEmits<{(e: "update:modelValue", value: string): void;}>()

    withDefaults(defineProps<{
        value?: string,
        router?: boolean,
        fit?: boolean
    }>(), {
        value: "INFO",
        router: true,
    })

    const LEVELS = [
        "TRACE",
        "DEBUG",
        "INFO",
        "WARN",
        "ERROR",
    ]
</script>
