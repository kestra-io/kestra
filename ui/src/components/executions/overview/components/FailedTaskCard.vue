<template>
    <section v-if="failedTaskRun" class="failed-task" data-test="failed-task-card">
        <span class="failed-task-label">{{ $t("execution_mobile.failed_task") }}</span>
        <p class="failed-task-path">
            {{ path.join(" › ") }}
        </p>
        <KsText v-if="message" type="danger" tag="p" :lineClamp="3" class="failed-task-message">
            {{ message }}
        </KsText>
        <KsButton
            type="primary"
            size="large"
            :tag="RouterLink"
            :to="logsRoute"
            data-test="view-task-logs"
        >
            {{ $t("execution_mobile.view_task_logs") }}
        </KsButton>
    </section>
</template>

<script setup lang="ts">
    import {RouterLink} from "vue-router"

    import type {Execution} from "../../../../stores/executions"
    import {useFirstFailure} from "../../../../composables/useFirstFailure"

    const props = defineProps<{execution: Execution}>()

    const {failedTaskRun, path, message, logsRoute} = useFirstFailure(() => props.execution)
</script>

<style scoped lang="scss">
    .failed-task {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3);
        background: var(--ks-bg-error);
        border: 1px solid var(--ks-border-error);
        border-radius: var(--ks-radius-base);
    }

    .failed-task-label {
        color: var(--ks-text-error);
        font-size: var(--ks-font-size-xs);
        letter-spacing: 0.04em;
        text-transform: uppercase;
    }

    .failed-task-path {
        margin: 0;
        color: var(--ks-text-primary);
        font-weight: var(--ks-font-weight-bold);
        overflow-wrap: anywhere;
    }

    .failed-task-message {
        overflow-wrap: anywhere;
    }
</style>
