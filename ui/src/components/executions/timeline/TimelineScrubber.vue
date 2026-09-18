<template>
    <div
        ref="trackRef"
        class="timeline-scrubber"
        :class="{dragging: draft !== null}"
        role="slider"
        tabindex="0"
        :aria-label="$t('executionsTimeline.scrubber.label')"
        :aria-valuemin="domainStartMs"
        :aria-valuemax="domainEndMs"
        :aria-valuenow="rangeStartMs"
        :aria-valuetext="valueText()"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @pointerleave="onPointerLeave"
        @keydown="onKeyDown"
    >
        <div class="scrubber-bars">
            <div
                v-for="bucket in buckets"
                :key="bucket.startMs"
                class="scrubber-bar"
                :class="{empty: bucket.total === 0}"
                :style="barStyle(bucket)"
            >
                <span
                    v-for="segment in segments(bucket)"
                    :key="segment.state"
                    class="scrubber-segment"
                    :data-state="segment.state"
                    :style="{height: `${segment.share * 100}%`}"
                />
            </div>
        </div>

        <div v-if="hoveredBucket" class="scrubber-hover" :style="hoverStyle" aria-hidden="true">
            <span class="scrubber-hover-time">{{ hoverTime() }}</span>
            <span>{{ $t("executionsTimeline.breadcrumb.total", hoveredBucket.total) }}</span>
            <span v-if="hoveredFailed > 0" class="scrubber-hover-failed">{{ $t("executionsTimeline.breadcrumb.failed", {count: hoveredFailed}) }}</span>
        </div>

        <div class="scrubber-axis" aria-hidden="true">
            <span v-for="tick in axisTicks()" :key="tick.key">{{ tick.label }}</span>
        </div>

        <div class="scrubber-selection" :style="selectionStyle" data-test="scrubber-selection">
            <span class="scrubber-grip" />
            <span class="scrubber-grip" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {dateUtils, durationUtils} from "@kestra-io/design-system"
    import {densitySeries, isFailedLikeState, type StateBucket, type TimelineExecution} from "../../../utils/executionsTimeline"
    import {MIN_RANGE_MS} from "../../../composables/useTimelineRange"

    const props = defineProps<{
        domainStartMs: number;
        domainEndMs: number;
        rangeStartMs: number;
        rangeEndMs: number;
        executions: TimelineExecution[];
        widthPx: number;
    }>()

    const emit = defineEmits<{
        change: [range: {startMs: number; endMs: number}];
    }>()

    const {t} = useI18n()

    const EDGE_GRAB_PX = 7

    const trackRef = ref<HTMLElement | null>(null)
    const draft = ref<{startMs: number; endMs: number} | null>(null)
    const gesture = ref<"brush" | "pan" | "resize-start" | "resize-end" | null>(null)
    const panAnchorMs = ref(0)
    const hoverMs = ref<number | null>(null)

    const domainSpanMs = computed(() => Math.max(1, props.domainEndMs - props.domainStartMs))

    const BUCKET_TARGET_WIDTH_PX = 9
    const MIN_BUCKETS = 16
    const MAX_BUCKETS = 140

    const bucketCount = computed(() =>
        Math.min(MAX_BUCKETS, Math.max(MIN_BUCKETS, Math.round(props.widthPx / BUCKET_TARGET_WIDTH_PX))),
    )

    const buckets = computed(() =>
        densitySeries(props.executions, props.domainStartMs, props.domainEndMs, bucketCount.value),
    )

    const hoveredBucket = computed(() => {
        const ms = hoverMs.value
        if (ms === null) return null
        return buckets.value.find(bucket => ms >= bucket.startMs && ms < bucket.endMs && bucket.total > 0) ?? null
    })

    const hoveredFailed = computed(() =>
        Object.entries(hoveredBucket.value?.byState ?? {})
            .filter(([state]) => isFailedLikeState(state))
            .reduce((total, [, count]) => total + count, 0),
    )

    const hoverStyle = computed(() => ({
        left: `${Math.min(88, Math.max(12, fractionOf(hoveredBucket.value?.startMs ?? 0) * 100))}%`,
    }))

    // dateUtils.dateFilter() needs getCurrentInstance(), so this must stay a template-called function, not a computed.
    function hoverTime(): string {
        const bucket = hoveredBucket.value
        return bucket === null ? "" : dateUtils.dateFilter(new Date(bucket.startMs).toISOString(), "LT")
    }

    // dateUtils.dateFilter() needs getCurrentInstance(), so this must stay a template-called function, not a computed.
    function axisTicks() {
        const nowMs = Date.now()
        const format = domainSpanMs.value < 86_400_000 ? "LT" : "MMM D, LT"
        return [0, 1].map((share) => {
            const ms = props.domainStartMs + share * domainSpanMs.value
            return {
                key: String(share),
                label: nowMs - ms < 60_000 ? t("now") : dateUtils.dateFilter(new Date(ms).toISOString(), format),
            }
        })
    }

    const busiestBucket = computed(() => Math.max(1, ...buckets.value.map(bucket => bucket.total)))

    const selection = computed(() => draft.value ?? {startMs: props.rangeStartMs, endMs: props.rangeEndMs})

    const selectionStyle = computed(() => {
        const left = fractionOf(selection.value.startMs)
        const right = fractionOf(selection.value.endMs)
        return {left: `${left * 100}%`, width: `${Math.max(right - left, 0.004) * 100}%`}
    })

    function fractionOf(ms: number): number {
        return clampFraction((ms - props.domainStartMs) / domainSpanMs.value)
    }

    function clampFraction(fraction: number): number {
        return Math.min(1, Math.max(0, fraction))
    }

    function msAt(event: PointerEvent): number {
        const rect = trackRef.value?.getBoundingClientRect()
        if (!rect || rect.width <= 0) return props.domainStartMs
        const fraction = clampFraction((event.clientX - rect.left) / rect.width)
        return props.domainStartMs + fraction * domainSpanMs.value
    }

    function segments(bucket: StateBucket): {state: string; share: number}[] {
        return Object.entries(bucket.byState)
            .map(([state, count]) => ({state, share: count / bucket.total}))
            .sort((a, b) => Number(isFailedLikeState(b.state)) - Number(isFailedLikeState(a.state)) || b.share - a.share)
    }

    function barStyle(bucket: StateBucket) {
        const left = fractionOf(bucket.startMs)
        const right = fractionOf(bucket.endMs)
        return {
            left: `${left * 100}%`,
            width: `${Math.max(right - left, 0.002) * 100}%`,
            height: bucket.total === 0 ? "2px" : `${Math.max(14, (bucket.total / busiestBucket.value) * 100)}%`,
        }
    }

    function valueText(): string {
        return t("executionsTimeline.toolbar.range", {
            start: dateUtils.dateFilter(new Date(selection.value.startMs).toISOString(), "lll"),
            end: dateUtils.dateFilter(new Date(selection.value.endMs).toISOString(), "lll"),
            duration: durationUtils.humanDuration((selection.value.endMs - selection.value.startMs) / 1000),
        })
    }

    function commit(startMs: number, endMs: number) {
        const orderedStart = Math.min(startMs, endMs)
        const orderedEnd = Math.max(startMs, endMs)
        const span = Math.max(orderedEnd - orderedStart, MIN_RANGE_MS)
        const center = (orderedStart + orderedEnd) / 2
        emit("change", {startMs: Math.round(center - span / 2), endMs: Math.round(center + span / 2)})
    }

    function msPerPixel(): number {
        const width = trackRef.value?.getBoundingClientRect().width ?? 0
        return width > 0 ? domainSpanMs.value / width : 0
    }

    function gestureAt(ms: number): "brush" | "pan" | "resize-start" | "resize-end" {
        const tolerance = EDGE_GRAB_PX * msPerPixel()
        if (Math.abs(ms - props.rangeStartMs) <= tolerance) return "resize-start"
        if (Math.abs(ms - props.rangeEndMs) <= tolerance) return "resize-end"
        return ms > props.rangeStartMs && ms < props.rangeEndMs ? "pan" : "brush"
    }

    function onPointerDown(event: PointerEvent) {
        trackRef.value?.setPointerCapture?.(event.pointerId)
        const ms = msAt(event)
        gesture.value = gestureAt(ms)
        panAnchorMs.value = ms
        draft.value = gesture.value === "brush"
            ? {startMs: ms, endMs: ms}
            : {startMs: props.rangeStartMs, endMs: props.rangeEndMs}
    }

    function onPointerMove(event: PointerEvent) {
        hoverMs.value = msAt(event)
        if (draft.value === null) return
        const ms = hoverMs.value

        if (gesture.value === "pan") {
            const shift = ms - panAnchorMs.value
            draft.value = {startMs: props.rangeStartMs + shift, endMs: props.rangeEndMs + shift}
        } else if (gesture.value === "resize-start") {
            draft.value = {startMs: ms, endMs: props.rangeEndMs}
        } else if (gesture.value === "resize-end") {
            draft.value = {startMs: props.rangeStartMs, endMs: ms}
        } else {
            draft.value = {startMs: draft.value.startMs, endMs: ms}
        }
    }

    function onPointerUp() {
        if (draft.value === null) return
        commit(draft.value.startMs, draft.value.endMs)
        draft.value = null
        gesture.value = null
    }

    function onPointerLeave() {
        if (draft.value === null) hoverMs.value = null
    }

    function onKeyDown(event: KeyboardEvent) {
        const step = (props.rangeEndMs - props.rangeStartMs) / 10
        if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
            const direction = event.key === "ArrowLeft" ? -1 : 1
            event.preventDefault()
            if (event.shiftKey) commit(props.rangeStartMs, props.rangeEndMs + direction * step)
            else commit(props.rangeStartMs + direction * step, props.rangeEndMs + direction * step)
        }
    }
</script>

<style lang="scss" scoped>
    .timeline-scrubber {
        position: relative;
        height: 4rem;
        background: var(--ks-bg-input);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-xs);
        overflow: hidden;
        cursor: crosshair;
        touch-action: none;
        user-select: none;
    }

    .timeline-scrubber:focus-visible {
        outline: 2px solid var(--ks-border-focus);
        outline-offset: 1px;
    }

    .scrubber-bars {
        position: absolute;
        top: var(--ks-spacing-1);
        right: var(--ks-spacing-1);
        bottom: 1.1rem;
        left: var(--ks-spacing-1);
    }

    .scrubber-axis {
        position: absolute;
        right: var(--ks-spacing-2);
        bottom: 2px;
        left: var(--ks-spacing-2);
        display: flex;
        justify-content: space-between;
        font-size: var(--ks-font-size-xs);
        font-variant-numeric: tabular-nums;
        color: var(--ks-text-secondary);
        pointer-events: none;
    }

    .scrubber-bar {
        position: absolute;
        bottom: 0;
        display: flex;
        flex-direction: column-reverse;
        min-width: 2px;
        border-radius: 1px 1px 0 0;
        overflow: hidden;
    }

    .scrubber-bar.empty {
        background: var(--ks-border-default);
    }

    .scrubber-segment {
        display: block;
        width: 100%;
        background: var(--ks-border-default);
    }

    .scrubber-segment[data-state="SUCCESS"] {
        background: var(--ks-chart-success);
    }

    .scrubber-segment[data-state="FAILED"] {
        background: var(--ks-chart-failed);
    }

    .scrubber-segment[data-state="WARNING"] {
        background: var(--ks-chart-warning);
    }

    .scrubber-segment[data-state="KILLED"] {
        background: var(--ks-chart-killed);
    }

    .scrubber-segment[data-state="KILLING"] {
        background: var(--ks-chart-killing);
    }

    .scrubber-segment[data-state="PAUSED"] {
        background: var(--ks-chart-paused);
    }

    .scrubber-segment[data-state="QUEUED"] {
        background: var(--ks-chart-queued);
    }

    .scrubber-segment[data-state="RUNNING"] {
        background: var(--ks-chart-running);
    }

    .scrubber-segment[data-state="CREATED"] {
        background: var(--ks-chart-created);
    }

    .scrubber-segment[data-state="CANCELLED"] {
        background: var(--ks-chart-cancelled);
    }

    .scrubber-selection {
        position: absolute;
        top: 0;
        bottom: 0;
        display: flex;
        align-items: center;
        justify-content: space-between;
        border-left: 2px solid var(--ks-btn-primary-bg-default);
        border-right: 2px solid var(--ks-btn-primary-bg-default);
        cursor: grab;
    }

    .timeline-scrubber.dragging .scrubber-selection {
        cursor: grabbing;
    }

    .scrubber-grip {
        width: 2px;
        height: 1rem;
        background: var(--ks-btn-primary-bg-default);
        cursor: ew-resize;
    }

    .scrubber-hover {
        position: absolute;
        top: var(--ks-spacing-1);
        display: flex;
        gap: var(--ks-spacing-2);
        padding: 2px var(--ks-spacing-2);
        transform: translateX(-50%);
        border-radius: var(--ks-radius-xs);
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-default);
        font-size: var(--ks-font-size-xs);
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
        color: var(--ks-text-primary);
        pointer-events: none;
    }

    .scrubber-hover-time {
        color: var(--ks-text-secondary);
    }

    .scrubber-hover-failed {
        color: var(--ks-text-error);
    }

    .scrubber-selection::before {
        content: "";
        position: absolute;
        inset: 0;
        background: var(--ks-btn-primary-bg-hover);
        opacity: 0.18;
    }

</style>
