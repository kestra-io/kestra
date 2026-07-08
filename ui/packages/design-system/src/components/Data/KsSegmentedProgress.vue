<template>
    <div class="ks-segmented-progress">
        <div
            class="ks-segmented-progress-track"
            role="progressbar"
            :aria-valuenow="valueSum"
            aria-valuemin="0"
            :aria-valuemax="total"
        >
            <KsTooltip
                v-for="segment in visibleSegments"
                :key="segment.key"
                :content="segment.tooltip"
                :disabled="!segment.tooltip"
            >
                <div
                    class="ks-segmented-progress-segment"
                    :style="{width: segmentWidth(segment) + '%', backgroundColor: segment.color}"
                />
            </KsTooltip>
        </div>
        <span v-if="$slots.default" class="ks-segmented-progress-label">
            <slot :value="valueSum" :total="total" />
        </span>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"

    export interface KsSegmentedProgressSegment {
        key: string
        value: number
        color: string
        tooltip?: string
    }

    const props = defineProps<{
        segments: KsSegmentedProgressSegment[]
        total: number
    }>()

    defineSlots<{
        default?(props: {value: number; total: number}): unknown
    }>()

    const visibleSegments = computed(() => props.segments.filter(segment => segment.value > 0))

    const valueSum = computed(() => props.segments.reduce((sum, segment) => sum + segment.value, 0))

    function segmentWidth(segment: KsSegmentedProgressSegment): number {
        return props.total > 0 ? (segment.value / props.total) * 100 : 0
    }
</script>

<style scoped lang="scss">
    .ks-segmented-progress {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        flex: 1;
    }

    .ks-segmented-progress-track {
        display: flex;
        flex: 1;
        height: var(--ks-spacing-2);
        border-radius: var(--ks-radius-sm);
        overflow: hidden;
        background-color: var(--ks-bg-hover);
    }

    .ks-segmented-progress-segment {
        height: 100%;
    }

    .ks-segmented-progress-label {
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
        white-space: nowrap;
    }
</style>
