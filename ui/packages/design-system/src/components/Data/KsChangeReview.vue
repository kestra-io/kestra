<template>
    <div class="ks-change-review">
        <div class="ks-change-review__head">
            <KsText tag="h3" size="large" class="ks-change-review__heading">{{ heading }}</KsText>
            <KsText v-if="meta.length" size="small" class="ks-change-review__muted">{{ meta.join(" · ") }}</KsText>
        </div>

        <div v-if="shownSegments.length" class="ks-change-review__bar" aria-hidden="true">
            <span v-for="segment in shownSegments" :key="segment.label" :style="{flex: segment.value, background: KS_CHANGE_REVIEW_TONES[segment.tone].color}" />
        </div>

        <div v-if="filters.length" class="ks-change-review__filters" role="group" :aria-label="filterLabel">
            <KsCheckTag :checked="filter === KS_CHANGE_REVIEW_ALL" @change="filter = KS_CHANGE_REVIEW_ALL">
                {{ allLabel }} <span class="ks-change-review__filter-count">{{ allCount }}</span>
            </KsCheckTag>
            <KsCheckTag v-for="segment in filters" :key="segment.name" :checked="filter === segment.name" @change="filter = segment.name">
                <template v-if="segment.glyph" #icon>
                    <span class="ks-change-review__glyph" :style="{color: KS_CHANGE_REVIEW_TONES[segment.tone].color}">{{ segment.glyph }}</span>
                </template>
                {{ segment.label }} <span class="ks-change-review__filter-count">{{ segment.value }}</span>
            </KsCheckTag>
            <KsText v-for="segment in passive" :key="segment.label" size="small" class="ks-change-review__muted ks-change-review__passive">
                <i :style="{background: KS_CHANGE_REVIEW_TONES[segment.tone].color}" />{{ segment.label }}
            </KsText>
        </div>

        <KsTabs v-model="tab" type="box">
            <KsTabPane v-for="item in tabs" :key="item.name" :name="item.name">
                <template #label>
                    <span class="ks-change-review__tab">
                        {{ item.label }}
                        <KsTag v-if="item.count !== undefined" size="small">{{ item.count }}</KsTag>
                    </span>
                </template>
                <div class="ks-change-review__pane">
                    <slot :name="item.name" />
                </div>
            </KsTabPane>
        </KsTabs>
    </div>
</template>

<script lang="ts">
    export type KsChangeReviewTone = "success" | "warning" | "danger" | "info" | "neutral"

    export interface KsChangeReviewSegment {
        /** Makes the segment a filter; the label of a segment without one is the whole text of its legend. */
        name?: string
        glyph?: string
        value: number
        label: string
        tone: KsChangeReviewTone
    }

    export interface KsChangeReviewTab {
        name: string
        label: string
        count?: number
    }

    export const KS_CHANGE_REVIEW_ALL = "all"

    export const KS_CHANGE_REVIEW_TONES: Record<KsChangeReviewTone, {color: string}> = {
        success: {color: "var(--ks-status-success)"},
        warning: {color: "var(--ks-status-warning)"},
        danger: {color: "var(--ks-status-error)"},
        info: {color: "var(--ks-status-running)"},
        neutral: {color: "var(--ks-border-strong)"},
    }
</script>

<script setup lang="ts">
    import {computed} from "vue"
    import KsText from "../Basic/KsText.vue"
    import KsTabPane from "../Navigation/KsTabs/KsTabPane.vue"
    import KsTabs from "../Navigation/KsTabs/KsTabs.vue"
    import KsCheckTag from "./KsTag/KsCheckTag.vue"
    import KsTag from "./KsTag/KsTag.vue"

    const props = withDefaults(defineProps<{
        heading: string
        meta?: string[]
        segments?: KsChangeReviewSegment[]
        allLabel: string
        allCount: number
        filterLabel: string
        tabs: KsChangeReviewTab[]
    }>(), {meta: () => [], segments: () => []})

    const tab = defineModel<string>("tab", {required: true})
    const filter = defineModel<string>("filter", {default: KS_CHANGE_REVIEW_ALL})

    const shownSegments = computed(() => props.segments.filter(segment => segment.value > 0))
    const filters = computed(() => shownSegments.value.filter((segment): segment is KsChangeReviewSegment & {name: string} => segment.name !== undefined))
    const passive = computed(() => shownSegments.value.filter(segment => segment.name === undefined))
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
        flex-wrap: wrap;
        align-items: baseline;
        justify-content: space-between;
        gap: var(--ks-spacing-1) var(--ks-spacing-4);
    }

    .ks-change-review__heading {
        margin: 0;
        font-weight: var(--ks-font-weight-semibold);
    }

    .ks-change-review__muted {
        color: var(--ks-text-secondary);
    }

    .ks-change-review__bar {
        display: flex;
        gap: 0.125rem;
        height: 0.375rem;
        border-radius: var(--ks-radius-xs);
        overflow: hidden;
    }

    .ks-change-review__filters {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .ks-change-review__glyph {
        font-family: var(--ks-font-family-mono);
        font-weight: var(--ks-font-weight-semibold);
    }

    .ks-change-review__filter-count {
        font-variant-numeric: tabular-nums;
        color: var(--ks-text-secondary);
    }

    .ks-change-review__passive i {
        display: inline-block;
        width: var(--ks-spacing-2);
        height: var(--ks-spacing-2);
        margin-right: var(--ks-spacing-1);
        border-radius: var(--ks-radius-xs);
    }

    .ks-change-review__pane {
        padding-top: var(--ks-spacing-3);
    }

    .ks-change-review__tab {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }
</style>
