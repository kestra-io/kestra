<template>
    <KsSelect
        :modelValue="value"
        @update:model-value="emit('update:modelValue', $event)"
        filterable
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
    import type {LOG_LEVEL} from "./TaskRunDetails.vue"
    const emit = defineEmits<{(e: "update:modelValue", value: LOG_LEVEL): void;}>()

    withDefaults(defineProps<{
        value?: LOG_LEVEL,
        router?: boolean
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
