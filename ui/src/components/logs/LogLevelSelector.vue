<template>
    <KsSelect
        :modelValue="value"
        @update:model-value="emit('update:modelValue', $event)"
        filterable
        :placeholder="$t('revisions')"
    >
        <KsOption
            v-for="item in LOG_LEVELS"
            :key="item"
            :label="item"
            :value="item"
        >
            {{ item }}
        </KsOption>
    </KsSelect>
</template>
<script setup lang="ts">
    import {LOG_LEVELS, type LOG_LEVEL_TYPE} from "@kestra-io/design-system"

    const emit = defineEmits<{(e: "update:modelValue", value: LOG_LEVEL_TYPE): void;}>()

    withDefaults(defineProps<{
        value?: LOG_LEVEL_TYPE,
        router?: boolean
    }>(), {
        value: "INFO",
        router: true,
    })
</script>
