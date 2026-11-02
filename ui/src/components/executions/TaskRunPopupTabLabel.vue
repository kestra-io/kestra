<template>
    <span class="tab-label">
        <KsIcon v-if="icon" size="xs"><component :is="icon" /></KsIcon>
        <span>{{ label }}</span>
        <span v-if="status === 'loading'" class="tab-label-skeleton" data-test="tab-count-loading">
            <KsSkeleton animated :rows="1" />
        </span>
        <KsTag
            v-else-if="status === 'error'"
            size="small"
            type="warning"
            :title="$t('task_run_popup.count_error')"
            data-test="tab-count-error"
        >
            !
        </KsTag>
        <KsTag v-else-if="status === 'ready'" size="small" data-test="tab-count">
            {{ count }}
        </KsTag>
    </span>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import type {LoadStatus} from "./composables/useTaskRunPopupData"

    defineProps<{
        label: string
        status: LoadStatus
        count: number
        icon?: Component
    }>()
</script>

<style scoped>
    .tab-label {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        font-size: var(--ks-font-size-sm);
    }

    .tab-label-skeleton {
        display: inline-block;
        width: var(--ks-spacing-5);
    }
</style>
