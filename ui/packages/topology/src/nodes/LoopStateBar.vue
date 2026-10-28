<template>
    <KsTooltip v-if="segments.length" placement="bottom" trigger="manual" :visible="visible">
        <div
            ref="bar"
            class="loop-state-bar"
            role="img"
            tabindex="0"
            :aria-label="ariaLabel"
            data-test="loop-state-bar"
            @mouseenter="show"
            @mouseleave="scheduleHide"
            @focus="focused = true"
            @blur="focused = false"
            @mousedown.prevent
            @click.stop
        >
            <div class="loop-state-bar__track">
                <span
                    v-for="segment in segments"
                    :key="segment.state"
                    class="loop-state-bar__segment"
                    :style="{flexGrow: segment.count, backgroundColor: segment.color}"
                    :data-state="segment.state"
                    data-test="loop-state-bar-segment"
                />
            </div>
        </div>
        <template #content>
            <ul ref="legend" class="loop-state-bar__legend" data-test="loop-state-bar-legend">
                <li v-for="segment in segments" :key="segment.state" class="loop-state-bar__legend-row">
                    <span class="loop-state-bar__dot" :style="{backgroundColor: segment.color}" />
                    <span class="loop-state-bar__state">{{ labelOf(segment) }}</span>
                    <span class="loop-state-bar__count">{{ $n(segment.count) }}</span>
                </li>
            </ul>
        </template>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed, onBeforeUnmount, ref, useTemplateRef, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsTooltip} from "@kestra-io/design-system"
    import {stateBarSegments, type StateBarSegment} from "../utils/stateBar"

    const HIDE_DELAY_MS = 150

    const props = defineProps<{
        counts?: Record<string, number>;
        total?: number;
        running?: number;
    }>()

    const {t, n} = useI18n()

    const bar = useTemplateRef<HTMLElement>("bar")
    const legend = useTemplateRef<HTMLElement>("legend")
    const hovered = ref(false)
    const focused = ref(false)
    let hideTimer: ReturnType<typeof setTimeout> | undefined

    const visible = computed(() => hovered.value || focused.value)

    const segments = computed(() => stateBarSegments(props.counts, {total: props.total, running: props.running}))

    const labelOf = (segment: StateBarSegment) =>
        segment.notStarted ? t("topology-graph.loop-state-bar.not-started") : segment.state

    const ariaLabel = computed(() =>
        segments.value
            .map((segment) => t("topology-graph.loop-state-bar.item", {count: n(segment.count), state: labelOf(segment)}))
            .join(", "),
    )

    function show() {
        clearTimeout(hideTimer)
        hovered.value = true
    }

    function scheduleHide() {
        clearTimeout(hideTimer)
        hideTimer = setTimeout(() => {
            hovered.value = false
        }, HIDE_DELAY_MS)
    }

    function dismiss() {
        clearTimeout(hideTimer)
        hovered.value = false
        focused.value = false
    }

    function onDocumentKeydown(event: KeyboardEvent) {
        if (event.key === "Escape") dismiss()
    }

    function onDocumentMouseover(event: MouseEvent) {
        const target = event.target as Node
        if (bar.value?.contains(target)) return
        if (legend.value?.closest("[role='tooltip']")?.contains(target)) show()
        else scheduleHide()
    }

    function removeDocumentListeners() {
        document.removeEventListener("keydown", onDocumentKeydown)
        document.removeEventListener("mouseover", onDocumentMouseover)
    }

    watch(visible, (isVisible) => {
        if (isVisible) {
            document.addEventListener("keydown", onDocumentKeydown)
            document.addEventListener("mouseover", onDocumentMouseover)
        } else {
            removeDocumentListeners()
        }
    })

    onBeforeUnmount(() => {
        clearTimeout(hideTimer)
        removeDocumentListeners()
    })
</script>

<style scoped>
    .loop-state-bar {
        position: relative;
        width: 100%;
        height: var(--loop-state-bar-height, var(--ks-spacing-1));
        border-radius: var(--ks-radius-xs);
    }

    .loop-state-bar::before {
        content: "";
        position: absolute;
        inset: calc(var(--ks-spacing-1) / -2) 0;
    }

    .loop-state-bar:focus-visible {
        outline: 2px solid var(--ks-border-focus);
    }

    .loop-state-bar__track {
        display: flex;
        height: 100%;
        overflow: hidden;
        border-radius: inherit;
    }

    .loop-state-bar__segment {
        flex-basis: 0;
        flex-shrink: 0;
        min-width: var(--ks-spacing-1);
        box-sizing: border-box;
    }

    .loop-state-bar__segment + .loop-state-bar__segment {
        border-left: 1px solid var(--ks-bg-badge);
    }

    .loop-state-bar__legend {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        margin: 0;
        padding: 0;
        list-style: none;
        font-size: var(--ks-font-size-xs);
    }

    .loop-state-bar__legend-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .loop-state-bar__dot {
        flex-shrink: 0;
        width: var(--ks-spacing-2);
        height: var(--ks-spacing-2);
        border-radius: 50%;
    }

    .loop-state-bar__state {
        flex: 1;
    }

    .loop-state-bar__count {
        margin-left: var(--ks-spacing-3);
        font-variant-numeric: tabular-nums;
        font-weight: var(--ks-font-weight-semibold);
    }
</style>
