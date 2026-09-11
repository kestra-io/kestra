<template>
    <KsTimeline class="state-history">
        <KsTimelineItem
            v-for="(entry, index) in entries"
            :key="index"
            :timestamp="dateFilter(entry.date, TIME_FORMAT)"
            :color="State.getStateColor(entry.state)"
        >
            <div class="state-history-row">
                <KsExecutionStatus size="small" :status="entry.state" />
                <span v-if="entry.elapsedSeconds !== undefined" class="state-history-row__elapsed">
                    {{ $t("failureDebugPanel.stateHistory.elapsed", {duration: formatElapsed(entry.elapsedSeconds)}) }}
                </span>
            </div>
        </KsTimelineItem>
    </KsTimeline>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {State, durationUtils} from "@kestra-io/design-system"
    import {date as dateFilter} from "../../../utils/filters"
    import type {FailureTaskRun} from "./types"

    const TIME_FORMAT = "HH:mm:ss.SSS"

    const props = defineProps<{
        taskRun: FailureTaskRun
    }>()

    const entries = computed(() =>
        props.taskRun.state.histories.map((entry, index, histories) => ({
            state: entry.state,
            date: entry.date,
            elapsedSeconds: index === 0
                ? undefined
                : (new Date(entry.date).getTime() - new Date(histories[index - 1].date).getTime()) / 1000,
        })),
    )

    // humanDuration's default unit set stops at seconds, so a transition inside the same
    // millisecond tick (CREATED -> SUBMITTED -> RUNNING commonly are) always rounds to "0s" —
    // include "ms" explicitly so the granularity this section exists for isn't lost.
    function formatElapsed(seconds: number): string {
        return durationUtils.humanDuration(seconds, {units: ["d", "h", "m", "s", "ms"]})
    }
</script>

<style scoped lang="scss">
    .state-history {
        padding-left: var(--ks-spacing-1);
    }

    .state-history-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .state-history-row__elapsed {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }
</style>
