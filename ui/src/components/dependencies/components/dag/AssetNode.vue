<template>
    <div :class="['asset-card', `status-${status}`, cardState]">
        <!-- Without handles vue-flow has nothing to anchor an edge to, ignores the node's
             sourcePosition/targetPosition and falls back to top/bottom attachment, which
             sends every edge on a detour around the card. topology.scss hides them. -->
        <Handle type="target" :position="Position.Left" />

        <!-- The tile is topology's node signature, so the asset graph reads as a sibling
             of the flow graph. It carries freshness as a tint because row 2 disappears at
             the compact detail level, and status has to survive that. Sized and shaped to
             take a plugin logo unchanged once assets carry their producing task type. -->
        <div class="asset-tile">
            <KsIcon size="base">
                <component :is="kindIcon" />
            </KsIcon>
        </div>

        <div class="asset-body">
            <span class="asset-name" :title="id">{{ name }}</span>

            <div v-if="detail === 'full'" class="asset-meta">
                <KsIcon size="xs" class="asset-status" :tooltip="statusLabel">
                    <component :is="statusIcon" />
                </KsIcon>
                <KsDateAgo v-if="updated" :date="updated" className="asset-age" />
                <KsText v-else size="small" class="asset-age">{{ statusLabel }}</KsText>
            </div>
        </div>

        <Handle type="source" :position="Position.Right" />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useI18n} from "vue-i18n"

    import {Handle, Position} from "@vue-flow/core"

    import Sprout from "vue-material-design-icons/Sprout.vue"
    import Eye from "vue-material-design-icons/Eye.vue"
    import Table from "vue-material-design-icons/Table.vue"
    import Sitemap from "vue-material-design-icons/Sitemap.vue"
    import PackageVariantClosed from "vue-material-design-icons/PackageVariantClosed.vue"
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
    import ClockAlertOutline from "vue-material-design-icons/ClockAlertOutline.vue"
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
    import CircleOutline from "vue-material-design-icons/CircleOutline.vue"
    import HelpCircleOutline from "vue-material-design-icons/HelpCircleOutline.vue"

    import {DAG_SELECTED, DAG_TRACED, DAG_DIMMED, DAG_DETAIL} from "../../utils/dagConstants"

    const props = defineProps<{
        id: string;
        data: {
            name: string;
            kind?: string;
            status: string;
            updated?: string;
        };
    }>()

    const {t} = useI18n()

    const KIND_ICONS: Record<string, unknown> = {
        seed:  Sprout,
        view:  Eye,
        table: Table,
        flow:  Sitemap,
    }

    // never is hollow and unknown is a question mark: both use --ks-status-neutral, so
    // without different glyphs "has never run" and "we do not track this" look identical.
    const STATUS_ICONS: Record<string, unknown> = {
        fresh:   CheckCircle,
        stale:   ClockAlertOutline,
        failed:  AlertCircle,
        never:   CircleOutline,
        unknown: HelpCircleOutline,
    }

    const name = computed(() => props.data.name)
    const status = computed(() => props.data.status)
    const updated = computed(() => props.data.updated)
    const kindIcon = computed(() => KIND_ICONS[props.data.kind ?? ""] ?? PackageVariantClosed)
    const statusIcon = computed(() => STATUS_ICONS[status.value] ?? HelpCircleOutline)
    const statusLabel = computed(() => t(`dependency.dag.status.${status.value}`))

    const selected = inject(DAG_SELECTED)
    const traced = inject(DAG_TRACED)
    const dimmed = inject(DAG_DIMMED)
    const detail = inject(DAG_DETAIL)

    /**
     * One class rather than several booleans: the states are mutually exclusive in
     * priority order, and letting them stack produced a dimmed-and-selected card.
     */
    const cardState = computed(() => {
        if (selected?.value === props.id) return "is-selected"
        if (dimmed?.value && !dimmed.value.has(props.id)) return "is-dimmed"
        if (traced?.value) return traced.value.has(props.id) ? "is-traced" : "is-faded"
        return ""
    })
</script>

<style lang="scss" scoped>
    // Declared once here and read by the tile and the status glyph, so freshness maps to a
    // semantic token in one place rather than in a rule per element per state.
    .asset-card {
        --ks-dag-status: var(--ks-status-neutral);

        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        width: var(--ks-dag-card-width);
        height: var(--ks-dag-card-height);
        box-sizing: border-box;
        overflow: hidden;
        padding: var(--ks-spacing-2);
        // Border carries exactly one meaning, selection. Freshness lives on the tile, so
        // the two never compete, and a graph of stale assets is not a wall of colour.
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);
        box-shadow: 0 0.125rem 0.25rem var(--ks-shadow-surface);
        transition: opacity var(--ks-duration-fast), filter var(--ks-duration-fast), border-color var(--ks-duration-fast);
    }

    .asset-card.status-fresh {
        --ks-dag-status: var(--ks-status-success);
    }

    .asset-card.status-stale {
        --ks-dag-status: var(--ks-status-warning);
    }

    .asset-card.status-failed {
        --ks-dag-status: var(--ks-status-error);
    }

    @media (prefers-reduced-motion: reduce) {
        .asset-card {
            transition: none;
        }
    }

    .asset-card.is-selected {
        border-color: var(--ks-border-focus);
        box-shadow: 0 0 0 1px var(--ks-border-focus);
    }

    .asset-card.is-traced {
        border-color: var(--ks-border-default);
    }

    /**
     * Two intensities, matched to intent. A filter the user applied (search or an isolated
     * group) desaturates: on a dark surface the card is already close to the canvas colour,
     * so opacity alone is a weak signal, whereas losing colour is categorical and survives
     * the compact detail level, since the tile tint is what remains visible there. Members
     * need no styling of their own; staying in colour is the emphasis.
     */
    .asset-card.is-dimmed {
        opacity: 0.4;
        filter: grayscale(1);
    }

    // Merely not on the hovered path: transient, fires on every mouse move, so it only
    // fades. Greying the graph on each hover would strobe.
    .asset-card.is-faded {
        opacity: 0.6;
    }

    .asset-tile {
        display: flex;
        align-items: center;
        justify-content: center;
        flex: 0 0 auto;
        width: var(--ks-spacing-7);
        height: var(--ks-spacing-7);
        box-sizing: border-box;
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-lg);
        background: color-mix(in srgb, var(--ks-dag-status) 10%, var(--ks-bg-badge));
        color: var(--ks-dag-status);
    }

    .asset-body {
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: var(--ks-spacing-1);
        min-width: 0;
    }

    // Monospace because asset ids are dotted database paths: it aligns them down a
    // column so the eye scans the segment that varies, and it reads as an identifier
    // rather than a label.
    .asset-name {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .asset-meta {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-width: 0;
    }

    .asset-status {
        color: var(--ks-dag-status);
        flex: 0 0 auto;
    }

    .asset-age {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
</style>
