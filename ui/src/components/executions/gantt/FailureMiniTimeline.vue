<template>
    <div class="mini-timeline">
        <div class="mini-timeline__ticks">
            <span>{{ dateFilter(windowStart, TICK_FORMAT) }}</span>
            <span>{{ dateFilter(windowEnd, TICK_FORMAT) }}</span>
        </div>
        <div
            ref="trackRef"
            class="mini-timeline__track"
            @pointerdown="onTrackPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onTrackPointerUp"
            @pointercancel="cancelDrag"
            @lostpointercapture="cancelDrag"
        >
            <div v-if="bandStyle" class="mini-timeline__band" :style="bandStyle" />
            <div
                v-for="node in nodes"
                :key="node.taskRun.id"
                class="mini-timeline__row"
                :class="{'is-focused': node.taskRun.id === focusedId}"
            >
                <span class="mini-timeline__label">
                    <code>{{ node.taskRun.taskId }}</code>
                </span>
                <div class="mini-timeline__lane">
                    <button
                        type="button"
                        class="mini-timeline__bar"
                        :style="barStyle(node.taskRun)"
                        :aria-label="node.taskRun.taskId"
                        @click="onBarClick(node.taskRun.id)"
                    />
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {State} from "@kestra-io/design-system"
    import {date as dateFilter} from "../../../utils/filters"
    import {computeTaskBarPercents} from "../../../utils/ganttSeries"
    import {useTimeRangeSelection, type TimeRange} from "../../../composables/useTimeRangeSelection"
    import type {StructuralNode, FailureTaskRun} from "./types"

    const TICK_FORMAT = "HH:mm:ss"

    const props = defineProps<{
        nodes: StructuralNode[]
        focusedId?: string
    }>()

    const emit = defineEmits<{
        "focus-task": [taskRunId: string]
        "select-range": [range: TimeRange | undefined]
    }>()

    const trackRef = ref<HTMLElement>()
    const ts = (date: string): number => new Date(date).getTime()

    const barInputs = computed(() =>
        props.nodes.map((node) => {
            const histories = node.taskRun.state.histories
            const startTs = ts(histories[0].date)
            const stopTs = State.isRunning(node.taskRun.state.current)
                ? Date.now()
                : ts(histories[histories.length - 1].date)
            return {id: node.taskRun.id, parentTaskRunId: node.taskRun.parentTaskRunId, startTs, stopTs}
        }),
    )

    const windowStart = computed(() => Math.min(...barInputs.value.map((b) => b.startTs)))
    const windowEnd = computed(() => Math.max(...barInputs.value.map((b) => b.stopTs)))
    const windowDelta = computed(() => Math.max(windowEnd.value - windowStart.value, 1))

    const percentsById = computed(() =>
        Object.fromEntries(
            computeTaskBarPercents(barInputs.value, windowStart.value, windowDelta.value).map((p) => [p.id, p]),
        ),
    )

    function barStyle(taskRun: FailureTaskRun): Record<string, string> {
        const percents = percentsById.value[taskRun.id]
        if (!percents) return {}
        const width = Math.max(percents.width, 3)
        return {
            left: `${Math.max(0, Math.min(percents.start, 100 - width))}%`,
            width: `${width}%`,
            background: State.color()[taskRun.state.current],
        }
    }

    function timeAt(clientX: number): number {
        const rect = trackRef.value?.getBoundingClientRect()
        if (!rect || rect.width === 0) return windowStart.value
        const ratio = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width))
        return windowStart.value + ratio * windowDelta.value
    }

    const {
        selection,
        dragBandPx,
        onPointerDown,
        onPointerMove,
        onPointerUp,
        cancelDrag,
        clear,
    } = useTimeRangeSelection(timeAt)

    // Pointer capture keeps the track receiving move/up events even once the cursor leaves its
    // bounding box mid-drag — without it, releasing outside the track never fires onTrackPointerUp
    // and the drag state (and its preview band) gets stuck until the next drag silently overwrites it.
    function onTrackPointerDown(event: PointerEvent) {
        onPointerDown(event)
        trackRef.value?.setPointerCapture(event.pointerId)
    }

    let lastGestureWasDrag = false

    function onTrackPointerUp(event: PointerEvent) {
        lastGestureWasDrag = onPointerUp(event)
        if (lastGestureWasDrag) {
            emit("select-range", selection.value)
        }
    }

    function onBarClick(taskRunId: string) {
        if (lastGestureWasDrag) {
            lastGestureWasDrag = false
            return
        }
        emit("focus-task", taskRunId)
    }

    const bandStyle = computed(() => {
        const rect = trackRef.value?.getBoundingClientRect()
        if (!rect || rect.width === 0) return undefined

        if (dragBandPx.value) {
            const left = ((dragBandPx.value.from - rect.left) / rect.width) * 100
            const width = ((dragBandPx.value.to - dragBandPx.value.from) / rect.width) * 100
            return {left: `${Math.max(0, left)}%`, width: `${Math.max(0, width)}%`}
        }

        if (selection.value) {
            const leftPct = ((selection.value.start - windowStart.value) / windowDelta.value) * 100
            const rightPct = ((selection.value.end - windowStart.value) / windowDelta.value) * 100
            return {left: `${Math.max(0, leftPct)}%`, width: `${Math.max(0, rightPct - leftPct)}%`}
        }

        return undefined
    })

    function clearSelection() {
        clear()
        emit("select-range", undefined)
    }

    defineExpose({clearSelection})
</script>

<style scoped lang="scss">
    .mini-timeline {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .mini-timeline__ticks {
        display: flex;
        justify-content: space-between;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }

    .mini-timeline__track {
        position: relative;
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) 0;
        touch-action: none;
    }

    .mini-timeline__band {
        position: absolute;
        top: 0;
        bottom: 0;
        background: var(--ks-bg-tag-active);
        border-left: 1px solid var(--ks-border-focus);
        border-right: 1px solid var(--ks-border-focus);
        pointer-events: none;
        z-index: 1;
    }

    .mini-timeline__row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
    }

    .mini-timeline__label {
        flex: 0 0 8rem;
        min-width: 0;

        code {
            display: block;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-xs);
        }
    }

    .is-focused .mini-timeline__label code {
        font-weight: 600;
        color: var(--ks-text-error);
    }

    .mini-timeline__lane {
        position: relative;
        flex: 1;
        height: 0.75rem;
        background: var(--ks-bg-active);
        border-radius: var(--ks-radius-xs);
    }

    .mini-timeline__bar {
        position: absolute;
        top: 0;
        bottom: 0;
        min-width: 3px;
        border: none;
        border-radius: var(--ks-radius-xs);
        cursor: pointer;
    }
</style>
