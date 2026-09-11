<template>
    <div class="timeline-row">
        <div class="timeline-row-label">
            <div class="name-row">
                <button type="button" class="name-button" @click="$emit('drill-in')">
                    {{ label }}
                </button>
                <KsIconButton
                    class="open-icon"
                    :tooltip="$t('executionsTimeline.row.openOwnPage')"
                    placement="top"
                    @click.stop="$emit('open-own-page')"
                >
                    <OpenInNew />
                </KsIconButton>
            </div>
            <span v-if="total !== undefined" class="counts">
                <span>{{ $t("executionsTimeline.row.runsCount", {count: total}) }}</span>
                <span v-if="failed" class="fail">{{ $t("executionsTimeline.row.failedCount", {count: failed}) }}</span>
            </span>
        </div>

        <div class="timeline-lanes" :style="{height: `${Math.max(laneCount, 1) * LANE_HEIGHT_REM}rem`}">
            <div v-for="lane in Math.max(laneCount, 1)" :key="lane" class="timeline-lane">
                <TimelineBar
                    v-for="bar in barsForLane(lane - 1)"
                    :key="bar.key"
                    :execution="bar.execution"
                    :bucket="bar.bucket"
                    :leftPercent="bar.leftPercent"
                    :widthPercent="bar.widthPercent"
                    :dimmed="bar.dimmed"
                    :intensity="bar.intensity"
                    @show-only-flow="$emit('show-only-flow', $event)"
                />
            </div>
            <div v-if="nowPercent !== undefined" class="timeline-now" :style="{left: `${nowPercent}%`}" :title="$t('now')" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import TimelineBar from "./TimelineBar.vue"
    import {
        assignLanes,
        bucketize,
        shouldBucketRow,
        type StateBucket,
        type TimelineExecution,
    } from "../../../utils/executionsTimeline"

    const LANE_HEIGHT_REM = 1.75

    const props = defineProps<{
        label: string;
        executions: TimelineExecution[];
        total?: number;
        failed?: number;
        rangeStartMs: number;
        rangeEndMs: number;
        availableWidthPx: number;
        packLanes: boolean;
        dimmedStates: Set<string>;
    }>()

    defineEmits<{
        "drill-in": [];
        "open-own-page": [];
        "show-only-flow": [{namespace: string; flowId: string}];
    }>()

    interface RenderedBar {
        key: string;
        lane: number;
        leftPercent: number;
        widthPercent: number;
        dimmed: boolean;
        execution?: TimelineExecution;
        bucket?: StateBucket;
        intensity?: number;
    }

    const rangeSpanMs = computed(() => Math.max(props.rangeEndMs - props.rangeStartMs, 1))

    const toPercent = (ms: number) => ((ms - props.rangeStartMs) / rangeSpanMs.value) * 100

    const laned = computed(() => props.packLanes ? assignLanes(props.executions) : props.executions.map(e => ({...e, lane: 0})))

    const laneCount = computed(() => laned.value.reduce((max, e) => Math.max(max, e.lane + 1), 1))

    const isBucketedByLane = computed<boolean[]>(() =>
        Array.from({length: laneCount.value}, (_, lane) =>
            shouldBucketRow(
                laned.value.filter(e => e.lane === lane),
                props.rangeStartMs,
                props.rangeEndMs,
                props.availableWidthPx,
            ),
        ),
    )

    const bars = computed<RenderedBar[]>(() => {
        const result: RenderedBar[] = []

        for (let lane = 0; lane < laneCount.value; lane++) {
            const laneExecutions = laned.value.filter(e => e.lane === lane)

            if (isBucketedByLane.value[lane]) {
                const buckets = bucketize(laneExecutions, props.rangeStartMs, props.rangeEndMs, props.availableWidthPx)
                const maxBucketTotal = Math.max(...buckets.map(b => b.total), 1)
                buckets.forEach((bucket, index) => {
                    result.push({
                        key: `bucket-${lane}-${index}`,
                        lane,
                        leftPercent: toPercent(bucket.startMs),
                        widthPercent: toPercent(bucket.endMs) - toPercent(bucket.startMs),
                        dimmed: Object.keys(bucket.byState).every(state => props.dimmedStates.has(state)),
                        bucket,
                        intensity: bucket.total / maxBucketTotal,
                    })
                })
            } else {
                for (const execution of laneExecutions) {
                    const left = toPercent(execution.startMs)
                    const width = Math.max(toPercent(execution.endMs) - left, 0)
                    result.push({
                        key: execution.id,
                        lane,
                        leftPercent: left,
                        widthPercent: width,
                        dimmed: props.dimmedStates.has(execution.state),
                        execution,
                    })
                }
            }
        }

        return result
    })

    function barsForLane(lane: number) {
        return bars.value.filter(bar => bar.lane === lane)
    }

    const nowPercent = computed(() => {
        const now = Date.now()
        if (now < props.rangeStartMs || now > props.rangeEndMs) return undefined
        return toPercent(now)
    })
</script>

<style scoped lang="scss">
.timeline-row {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-2) 0;
    border-top: 1px solid var(--ks-border-subtle);

    &:first-child {
        border-top: none;
    }
}

.timeline-row-label {
    width: var(--timeline-row-label-width, 11.25rem);
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
}

.name-row {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
}

.name-button {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    background: none;
    border: none;
    padding: 0;
    font: inherit;
    text-align: left;
    cursor: pointer;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;

    &:hover {
        color: var(--ks-text-link);
    }
}

.open-icon {
    opacity: 0;
    flex-shrink: 0;
}

.timeline-row-label:hover .open-icon {
    opacity: 1;
}

.counts {
    display: flex;
    gap: var(--ks-spacing-2);
    font-size: var(--ks-font-size-2xs);
    color: var(--ks-text-secondary);
    font-variant-numeric: tabular-nums;
}

.fail {
    color: var(--ks-chart-failed);
    font-weight: 600;
}

.timeline-lanes {
    position: relative;
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
}

.timeline-lane {
    position: relative;
    flex: 1;
    min-height: 1.5rem;
    border-radius: var(--ks-radius-xs);
    background: var(--ks-bg-base);
}

.timeline-now {
    position: absolute;
    top: 0;
    bottom: 0;
    width: 0;
    border-left: 0.09375rem dashed var(--ks-text-primary);
    pointer-events: none;
    z-index: 2;
}
</style>
