<template>
    <div class="ks-range-slider" :class="{'is-disabled': disabled}">
        <div ref="trackRef" class="ks-range-slider-track" data-test="range-slider-track">
            <div
                class="ks-range-slider-selection"
                data-test="range-slider-selection"
                :style="selectionStyle"
                @pointerdown="onBodyPointerDown"
                @pointermove="onPointerMove"
                @pointerup="onPointerUp"
                @pointercancel="onPointerUp"
            >
                <span
                    v-if="dragMode === 'start' && formatValue"
                    class="ks-range-slider-value ks-range-slider-value-start"
                >{{ formatValue(model[0]) }}</span>
                <button
                    type="button"
                    class="ks-range-slider-handle ks-range-slider-handle-start"
                    data-test="range-slider-handle-start"
                    role="slider"
                    :aria-label="startLabel"
                    aria-orientation="horizontal"
                    :aria-valuemin="min"
                    :aria-valuemax="model[1]"
                    :aria-valuenow="model[0]"
                    :aria-valuetext="formatValue ? formatValue(model[0]) : undefined"
                    :aria-disabled="disabled"
                    :tabindex="disabled ? -1 : 0"
                    :disabled="disabled"
                    @pointerdown.stop="onHandlePointerDown('start', $event)"
                    @pointermove="onPointerMove"
                    @pointerup="onPointerUp"
                    @pointercancel="onPointerUp"
                    @keydown="onHandleKeydown('start', $event)"
                />
                <button
                    type="button"
                    class="ks-range-slider-handle ks-range-slider-handle-end"
                    data-test="range-slider-handle-end"
                    role="slider"
                    :aria-label="endLabel"
                    aria-orientation="horizontal"
                    :aria-valuemin="model[0]"
                    :aria-valuemax="max"
                    :aria-valuenow="model[1]"
                    :aria-valuetext="formatValue ? formatValue(model[1]) : undefined"
                    :aria-disabled="disabled"
                    :tabindex="disabled ? -1 : 0"
                    :disabled="disabled"
                    @pointerdown.stop="onHandlePointerDown('end', $event)"
                    @pointermove="onPointerMove"
                    @pointerup="onPointerUp"
                    @pointercancel="onPointerUp"
                    @keydown="onHandleKeydown('end', $event)"
                />
                <span
                    v-if="dragMode === 'end' && formatValue"
                    class="ks-range-slider-value ks-range-slider-value-end"
                >{{ formatValue(model[1]) }}</span>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"

    const model = defineModel<[number, number]>({required: true})

    const props = withDefaults(defineProps<{
        /** Lower bound of the domain the track represents. */
        min: number
        /** Upper bound of the domain the track represents. */
        max: number
        /** Smallest allowed distance between the two handles. */
        minRange?: number
        /** Keyboard nudge amount; defaults to 1/100th of the domain. */
        step?: number
        disabled?: boolean
        /** Formats a domain value for the aria-valuetext and the drag tooltip. */
        formatValue?: (value: number) => string
        /** Accessible label for the start handle, supplied by the caller (mirrors KsIconButton's aria-label contract). */
        startLabel: string
        /** Accessible label for the end handle. */
        endLabel: string
    }>(), {
        minRange: 0,
        step: undefined,
        disabled: false,
        formatValue: undefined,
    })

    const emit = defineEmits<{
        /** Fired once a drag or a keyboard nudge finishes, only when the value actually moved. */
        change: [value: [number, number]]
    }>()

    const trackRef = ref<HTMLElement | null>(null)
    const dragMode = ref<"start" | "end" | "pan" | null>(null)

    interface DragOrigin {
        pointerX: number
        start: number
        end: number
        trackLeft: number
        trackWidth: number
    }
    let dragOrigin: DragOrigin | null = null

    const domainSpan = computed(() => Math.max(props.max - props.min, 1))
    const effectiveStep = computed(() => props.step ?? Math.max(domainSpan.value / 100, 1))

    const toPercent = (value: number) => ((value - props.min) / domainSpan.value) * 100

    const selectionStyle = computed(() => {
        const left = toPercent(model.value[0])
        const width = Math.max(toPercent(model.value[1]) - left, 0)
        return {left: `${left}%`, width: `${width}%`}
    })

    function ratioFromClientX(clientX: number): number {
        if (!dragOrigin || dragOrigin.trackWidth === 0) return 0
        return Math.min(Math.max((clientX - dragOrigin.trackLeft) / dragOrigin.trackWidth, 0), 1)
    }

    function valueFromClientX(clientX: number): number {
        return props.min + ratioFromClientX(clientX) * domainSpan.value
    }

    function beginDrag(mode: "start" | "end" | "pan", event: PointerEvent) {
        if (props.disabled || !trackRef.value) return
        event.preventDefault()
        const target = event.currentTarget as HTMLElement
        target.setPointerCapture(event.pointerId)
        const rect = trackRef.value.getBoundingClientRect()
        dragOrigin = {
            pointerX: event.clientX,
            start: model.value[0],
            end: model.value[1],
            trackLeft: rect.left,
            trackWidth: rect.width,
        }
        dragMode.value = mode
    }

    function onHandlePointerDown(which: "start" | "end", event: PointerEvent) {
        beginDrag(which, event)
    }

    function onBodyPointerDown(event: PointerEvent) {
        beginDrag("pan", event)
    }

    function onPointerMove(event: PointerEvent) {
        if (!dragMode.value || !dragOrigin) return

        if (dragMode.value === "start") {
            const next = Math.min(Math.max(valueFromClientX(event.clientX), props.min), model.value[1] - props.minRange)
            model.value = [next, model.value[1]]
        } else if (dragMode.value === "end") {
            const next = Math.max(Math.min(valueFromClientX(event.clientX), props.max), model.value[0] + props.minRange)
            model.value = [model.value[0], next]
        } else if (dragOrigin.trackWidth > 0) {
            const deltaValue = ((event.clientX - dragOrigin.pointerX) / dragOrigin.trackWidth) * domainSpan.value
            const span = dragOrigin.end - dragOrigin.start
            let nextStart = dragOrigin.start + deltaValue
            let nextEnd = dragOrigin.end + deltaValue
            if (nextStart < props.min) {
                nextStart = props.min
                nextEnd = props.min + span
            }
            if (nextEnd > props.max) {
                nextEnd = props.max
                nextStart = props.max - span
            }
            model.value = [nextStart, nextEnd]
        }
    }

    function onPointerUp() {
        if (!dragMode.value || !dragOrigin) return
        const moved = model.value[0] !== dragOrigin.start || model.value[1] !== dragOrigin.end
        dragMode.value = null
        dragOrigin = null
        if (moved) emit("change", model.value)
    }

    function onHandleKeydown(which: "start" | "end", event: KeyboardEvent) {
        if (props.disabled) return

        const stepAmount = event.shiftKey ? effectiveStep.value * 10 : effectiveStep.value
        let [start, end] = model.value

        switch (event.key) {
        case "ArrowLeft":
        case "ArrowDown":
            if (which === "start") start = Math.max(start - stepAmount, props.min)
            else end = Math.max(end - stepAmount, start + props.minRange)
            break
        case "ArrowRight":
        case "ArrowUp":
            if (which === "start") start = Math.min(start + stepAmount, end - props.minRange)
            else end = Math.min(end + stepAmount, props.max)
            break
        case "Home":
            if (which === "start") start = props.min
            else end = Math.max(props.min + props.minRange, start + props.minRange)
            break
        case "End":
            if (which === "start") start = Math.min(props.max - props.minRange, end - props.minRange)
            else end = props.max
            break
        default:
            return
        }

        event.preventDefault()
        const moved = start !== model.value[0] || end !== model.value[1]
        model.value = [start, end]
        if (moved) emit("change", model.value)
    }
</script>

<style scoped lang="scss">
.ks-range-slider {
    display: flex;
    align-items: center;
    width: 100%;
    height: var(--ks-spacing-5);
}

.ks-range-slider-track {
    position: relative;
    width: 100%;
    height: var(--ks-spacing-2);
    border-radius: var(--ks-radius-lg);
    background: var(--ks-toggle-default);
}

.ks-range-slider-selection {
    position: absolute;
    top: 0;
    bottom: 0;
    border-radius: var(--ks-radius-lg);
    background: var(--ks-bg-tag-active);
    cursor: grab;
    transition: background-color var(--ks-duration-fast) var(--ks-ease-standard);

    &:active {
        cursor: grabbing;
        background: var(--ks-toggle-active);
    }
}

.ks-range-slider-handle {
    position: absolute;
    top: 50%;
    width: var(--ks-spacing-3);
    height: var(--ks-spacing-5);
    transform: translate(-50%, -50%);
    padding: 0;
    border: var(--ks-border-width-base) solid var(--ks-bg-surface);
    border-radius: var(--ks-radius-sm);
    background: var(--ks-btn-primary-bg-default);
    box-shadow: 0 1px 2px var(--ks-shadow-element);
    cursor: ew-resize;
    transition: background-color var(--ks-duration-fast) var(--ks-ease-standard), transform var(--ks-duration-fast) var(--ks-ease-standard);

    &:hover {
        background: var(--ks-btn-primary-bg-hover);
    }

    &:active {
        background: var(--ks-btn-primary-bg-active);
    }

    &:focus-visible {
        outline: var(--ks-border-width-base) solid var(--ks-border-focus);
        outline-offset: var(--ks-spacing-1);
    }
}

.ks-range-slider-handle-start {
    left: 0;
}

.ks-range-slider-handle-end {
    left: 100%;
}

.ks-range-slider-value {
    position: absolute;
    bottom: 100%;
    transform: translateX(-50%);
    margin-bottom: var(--ks-spacing-1);
    padding: 0 var(--ks-spacing-1);
    border-radius: var(--ks-radius-xs);
    background: var(--ks-text-primary);
    color: var(--ks-bg-surface);
    font-size: var(--ks-font-size-2xs);
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
    pointer-events: none;
}

.ks-range-slider-value-start {
    left: 0;
}

.ks-range-slider-value-end {
    left: 100%;
}

.ks-range-slider.is-disabled {
    .ks-range-slider-selection {
        background: var(--ks-bg-inactive);
        cursor: not-allowed;
    }

    .ks-range-slider-handle {
        background: var(--ks-toggle-inactive);
        cursor: not-allowed;
    }
}
</style>
