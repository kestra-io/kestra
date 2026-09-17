<template>
    <KsEmpty v-if="rows.length === 0" :description="$t('failureDebugPanel.attempts.empty')" :imageSize="80" />
    <ul v-else class="failure-attempts">
        <li v-for="row in rows" :key="row.index" class="failure-attempts__row">
            <span class="failure-attempts__label">{{ $t("attempt") }} {{ row.index + 1 }}</span>
            <KsExecutionStatus size="small" :status="row.state" />
            <span class="failure-attempts__meta">{{ row.startedAt }}</span>
            <span v-if="row.duration" class="failure-attempts__meta">{{ row.duration }}</span>
            <code v-if="row.workerId" class="failure-attempts__worker" :title="`${$t('workerId')}: ${row.workerId}`">
                {{ row.workerId }}
            </code>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsEmpty, KsExecutionStatus, durationUtils} from "@kestra-io/design-system"
    import {date as dateFilter} from "../../../utils/filters"
    import type {FailureTaskRun} from "./types"

    const TIME_FORMAT = "HH:mm:ss.SSS"

    const props = defineProps<{
        taskRun: FailureTaskRun
    }>()

    const rows = computed(() =>
        (props.taskRun.attempts ?? []).map((attempt, index) => {
            const histories = attempt.state?.histories ?? []
            const first = histories[0]?.date
            const last = histories[histories.length - 1]?.date
            const elapsed = first && last
                ? (new Date(last).getTime() - new Date(first).getTime()) / 1000
                : undefined

            return {
                index,
                state: attempt.state?.current,
                workerId: attempt.workerId,
                startedAt: first ? dateFilter(first, TIME_FORMAT) : "",
                duration: elapsed ? durationUtils.humanDuration(elapsed, {units: ["d", "h", "m", "s", "ms"]}) : undefined,
            }
        }),
    )
</script>

<style scoped lang="scss">
    .failure-attempts {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        margin: 0;
        padding: 0;
        list-style: none;
    }

    .failure-attempts__row {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        font-size: var(--ks-font-size-xs);
    }

    .failure-attempts__label {
        color: var(--ks-text-primary);
        font-weight: 600;
    }

    .failure-attempts__meta {
        color: var(--ks-text-secondary);
    }

    .failure-attempts__worker {
        margin-left: auto;
        max-width: 14rem;
        overflow: hidden;
        color: var(--ks-text-secondary);
        text-overflow: ellipsis;
        white-space: nowrap;
    }
</style>
