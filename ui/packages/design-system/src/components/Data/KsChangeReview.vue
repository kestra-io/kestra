<template>
    <div class="ks-change-review">
        <div class="ks-change-review__head">
            <KsText tag="h3" size="large" class="ks-change-review__heading">{{ heading }}</KsText>
            <div v-if="meta.length" class="ks-change-review__meta">
                <KsText v-for="item in meta" :key="item" size="small" class="ks-change-review__muted">{{ item }}</KsText>
            </div>
        </div>

        <div class="ks-change-review__tiles">
            <div
                v-for="tile in tiles"
                :key="tile.label"
                class="ks-change-review__tile"
                :class="{'ks-change-review__tile--zero': !tile.count}"
                :style="toneStyle(tile.tone)"
            >
                <span class="ks-change-review__glyph">{{ tile.glyph }}</span>
                <span class="ks-change-review__count">{{ tile.count }}</span>
                <span class="ks-change-review__label">
                    {{ tile.label }}
                    <small v-if="tile.hint">{{ tile.hint }}</small>
                </span>
            </div>
        </div>

        <div v-if="shownSegments.length" class="ks-change-review__bar" aria-hidden="true">
            <span v-for="segment in shownSegments" :key="segment.label" :style="{flex: segment.value, background: KS_CHANGE_REVIEW_TONES[segment.tone].color}" />
        </div>
        <div class="ks-change-review__legend">
            <KsText v-for="segment in shownSegments" :key="segment.label" size="small" class="ks-change-review__muted">
                <i :style="{background: KS_CHANGE_REVIEW_TONES[segment.tone].color}" />{{ segment.value }} {{ segment.label }}
            </KsText>
            <KsText v-if="total" size="small" class="ks-change-review__muted ks-change-review__total">{{ total }}</KsText>
        </div>

        <KsSegmented v-model="tab" :options="tabOptions" size="small" class="ks-change-review__tabs" />
        <slot :name="tab" />
    </div>
</template>

<script lang="ts">
    export type KsChangeReviewTone = "success" | "warning" | "danger" | "info" | "neutral"

    export interface KsChangeReviewTile {
        glyph: string
        count: number
        label: string
        hint?: string
        tone: KsChangeReviewTone
    }

    export interface KsChangeReviewSegment {
        value: number
        label: string
        tone: KsChangeReviewTone
    }

    export interface KsChangeReviewTab {
        name: string
        label: string
        count?: number
    }

    export const KS_CHANGE_REVIEW_TONES: Record<KsChangeReviewTone, {color: string, background: string}> = {
        success: {color: "var(--ks-status-success)", background: "var(--ks-bg-success)"},
        warning: {color: "var(--ks-status-warning)", background: "var(--ks-bg-warning)"},
        danger: {color: "var(--ks-status-error)", background: "var(--ks-bg-error)"},
        info: {color: "var(--ks-status-running)", background: "var(--ks-bg-info)"},
        neutral: {color: "var(--ks-border-strong)", background: "var(--ks-bg-base)"},
    }
</script>

<script setup lang="ts">
    import {computed} from "vue"
    import KsText from "../Basic/KsText.vue"
    import KsSegmented from "./KsSegmented.vue"

    const props = withDefaults(defineProps<{
        heading: string
        meta?: string[]
        tiles: KsChangeReviewTile[]
        segments?: KsChangeReviewSegment[]
        total?: string
        tabs: KsChangeReviewTab[]
    }>(), {meta: () => [], segments: () => [], total: undefined})

    const tab = defineModel<string>("tab", {required: true})

    const shownSegments = computed(() => props.segments.filter(segment => segment.value > 0))

    const tabOptions = computed(() => props.tabs.map(item => ({
        label: item.count === undefined ? item.label : `${item.label} ${item.count}`,
        value: item.name,
    })))

    function toneStyle(tone: KsChangeReviewTone): Record<string, string> {
        return {"--ks-change-review-tone": KS_CHANGE_REVIEW_TONES[tone].color, "--ks-change-review-tone-background": KS_CHANGE_REVIEW_TONES[tone].background}
    }
</script>

<style scoped>
    .ks-change-review {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        min-width: 0;
    }

    .ks-change-review__head {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }

    .ks-change-review__heading {
        align-self: flex-start;
        margin: 0;
        font-weight: var(--ks-font-weight-semibold);
    }

    .ks-change-review__meta,
    .ks-change-review__legend {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-1) var(--ks-spacing-4);
    }

    .ks-change-review__muted {
        color: var(--ks-text-secondary);
    }

    .ks-change-review__tiles {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
        gap: var(--ks-spacing-3);
    }

    .ks-change-review__tile {
        display: flex;
        align-items: baseline;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        border: 1px solid color-mix(in srgb, var(--ks-change-review-tone) 40%, var(--ks-border-default));
        border-radius: var(--ks-radius-base);
        background: var(--ks-change-review-tone-background);
        color: var(--ks-change-review-tone);
    }

    .ks-change-review__tile--zero {
        border-color: var(--ks-border-default);
        background: var(--ks-bg-base);
        color: var(--ks-text-inactive);
    }

    .ks-change-review__glyph,
    .ks-change-review__count {
        font-family: var(--ks-font-family-mono);
        font-weight: var(--ks-font-weight-semibold);
        font-variant-numeric: tabular-nums;
    }

    .ks-change-review__glyph {
        font-size: var(--ks-font-size-lg);
    }

    .ks-change-review__count {
        font-size: var(--ks-font-size-4xl);
        line-height: 1;
    }

    .ks-change-review__label {
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);

        & small {
            display: block;
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-inactive);
        }
    }

    .ks-change-review__bar {
        display: flex;
        gap: 0.125rem;
        height: 0.375rem;
        border-radius: var(--ks-radius-xs);
        overflow: hidden;
        background: var(--ks-bg-tag);
    }

    .ks-change-review__legend i {
        display: inline-block;
        width: var(--ks-spacing-2);
        height: var(--ks-spacing-2);
        margin-right: var(--ks-spacing-1);
        border-radius: var(--ks-radius-xs);
    }

    .ks-change-review__total {
        margin-left: auto;
    }

    .ks-change-review__tabs {
        align-self: flex-start;
        max-width: 100%;
        overflow-x: auto;
    }
</style>
