<template>
    <KsTooltip v-if="segments.length" placement="bottom" trigger="manual" :visible="hovered || focused">
        <div
            class="loop-state-bar"
            role="img"
            tabindex="0"
            :aria-label="ariaLabel"
            data-test="loop-state-bar"
            @mouseenter="hovered = true"
            @mouseleave="hovered = false"
            @focus="focused = true"
            @blur="focused = false"
            @keydown.esc="dismiss"
        >
            <span
                v-for="segment in segments"
                :key="segment.state"
                class="loop-state-bar__segment"
                :style="{flexGrow: segment.count, backgroundColor: segment.color}"
                :data-state="segment.state"
                data-test="loop-state-bar-segment"
            />
        </div>
        <template #content>
            <ul class="loop-state-bar__legend" data-test="loop-state-bar-legend">
                <li v-for="segment in segments" :key="segment.state" class="loop-state-bar__legend-row">
                    <span class="loop-state-bar__dot" :style="{backgroundColor: segment.color}" />
                    <span class="loop-state-bar__state">{{ labelOf(segment) }}</span>
                    <span class="loop-state-bar__count">{{ n(segment.count) }}</span>
                </li>
            </ul>
        </template>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsTooltip} from "@kestra-io/design-system"
    import type {StateCounts} from "../utils/loopOutcome"
    import {stateBarSegments, type StateBarSegment} from "../utils/stateBar"

    const props = defineProps<{
        counts?: StateCounts;
        total?: number;
        running?: number;
    }>()

    const {t, n} = useI18n()

    const hovered = ref(false)
    const focused = ref(false)

    function dismiss() {
        hovered.value = false
        focused.value = false
    }

    const segments = computed(() => stateBarSegments(props.counts, {total: props.total, running: props.running}))

    const labelOf = (segment: StateBarSegment) =>
        segment.notStarted ? t("topology-graph.loop-state-bar.not-started") : segment.state.toLowerCase()

    const ariaLabel = computed(() =>
        segments.value
            .map((segment) => t("topology-graph.loop-state-bar.item", {count: n(segment.count), state: labelOf(segment)}))
            .join(", "),
    )
</script>

<style scoped>
    .loop-state-bar {
        display: flex;
        width: 100%;
        height: var(--ks-spacing-1);
        overflow: hidden;
        border-radius: var(--ks-radius-xs);
    }

    .loop-state-bar:focus-visible {
        outline: 2px solid var(--ks-border-focus);
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
        font-weight: 600;
    }
</style>
