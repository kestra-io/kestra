<template>
    <div :class="['asset-card', `status-${status}`, cardState]">
        <!-- Without handles vue-flow ignores sourcePosition/targetPosition and attaches every
             edge top/bottom, sending it on a detour around the card. topology.scss hides them. -->
        <Handle type="target" :position="Position.Left" />

        <div class="asset-tile">
            <!-- Assets always carry a type, so the material branch below is reached only by
                 flow nodes. -->
            <component
                :is="taskIconComponent"
                v-if="data.iconCls"
                :cls="data.iconCls"
                :icons="pluginsStore.icons"
                :loadIcon="pluginsStore.loadIcon"
                onlyIcon
                class="asset-logo"
            />
            <KsIcon v-else size="base">
                <component :is="fallbackIcon" />
            </KsIcon>
        </div>

        <div class="asset-body">
            <span class="asset-name" :title="id">{{ data.name }}</span>

            <!-- Assets only: a flow has no freshness, so this rendered "Not tracked" on it. -->
            <div v-if="!data.isFlow" class="asset-meta">
                <!-- Colour via the prop, not a class: a class lands on the ElIcon root where a
                     kel-icon rule already sets colour and wins. -->
                <KsIcon size="xs" class="asset-status" :color="statusColor" :tooltip="statusLabel">
                    <component :is="statusIcon" />
                </KsIcon>
                <!-- Wrapped in a span this component owns: KsDateAgo renders inside KsTooltip,
                     so its own root never receives the scoped attribute and className styles
                     nothing. -->
                <span v-if="data.updated" class="asset-age"><KsDateAgo :date="data.updated" /></span>
                <KsText v-else size="small" class="asset-age">{{ statusLabel }}</KsText>
                <!-- A technical identifier, so no i18n key. -->
                <KsText v-if="typeName" size="small" class="asset-type">{{ typeName }}</KsText>
            </div>
        </div>

        <Handle type="source" :position="Position.Right" />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useI18n} from "vue-i18n"

    import {Handle, Position} from "@vue-flow/core"

    import {stringUtils, useTaskIcon} from "@kestra-io/design-system"

    import {usePluginsStore} from "../../../../stores/plugins"

    import Sitemap from "vue-material-design-icons/Sitemap.vue"
    import PackageVariantClosed from "vue-material-design-icons/PackageVariantClosed.vue"

    import {statusIconOf, statusColorOf, normalizeStatus} from "../../utils/assetStatus"

    import {DAG_SELECTED, DAG_HOVERED, DAG_TRACED, DAG_DIMMED} from "../../utils/dagConstants"

    const props = defineProps<{
        id: string;
        data: {
            name: string;
            /** Task or asset type FQCN whose plugin logo the tile shows. */
            iconCls?: string;
            /** Flow nodes have no plugin FQCN, so they keep a material glyph. */
            isFlow?: boolean;
            /** Asset type FQCN; row 2 shows its trailing segment. */
            assetType?: string;
            status: string;
            updated?: string;
        };
    }>()

    const {t} = useI18n({useScope: "global"})

    const taskIconComponent = useTaskIcon()
    const pluginsStore = usePluginsStore()

    // Normalised, not raw: an unrecognised value interpolates into the i18n key below and
    // renders `dependency.dag.status.<value>` on the card.
    const status = computed(() => normalizeStatus(props.data.status))
    const fallbackIcon = computed(() => (props.data.isFlow ? Sitemap : PackageVariantClosed))
    const typeName = computed(() => (props.data.assetType ? stringUtils.afterLastDot(props.data.assetType) : undefined))
    const statusIcon = computed(() => statusIconOf(status.value))
    const statusColor = computed(() => statusColorOf(status.value))
    const statusLabel = computed(() => t(`dependency.dag.status.${status.value}`))

    const selected = inject(DAG_SELECTED)
    const hovered = inject(DAG_HOVERED)
    const traced = inject(DAG_TRACED)
    const dimmed = inject(DAG_DIMMED)

    /** One class, not several booleans: the states are exclusive and stacking them clashed. */
    const cardState = computed(() => {
        if (selected?.value === props.id) return "is-selected"
        // Above the dim: hovering a side-table row exists to find that node on the canvas.
        if (hovered?.value === props.id) return "is-hovered"
        if (dimmed?.value && !dimmed.value.has(props.id)) return "is-dimmed"
        if (traced?.value) return traced.value.has(props.id) ? "is-traced" : "is-faded"
        return ""
    })
</script>

<style lang="scss" scoped>
    // Declared once and read by the tile and the glyph, so freshness maps to a token in one
    // place rather than a rule per element per state.
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
        // Border means selection and nothing else; freshness lives on the tile.
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

    // Shares the selection colour but not its ring, so hovered and selected stay distinct.
    // Opacity and filter reset so it shows through a dim it may be sitting under.
    .asset-card.is-hovered {
        border-color: var(--ks-border-focus);
        opacity: 1;
        filter: none;
    }

    .asset-card.is-traced {
        border-color: var(--ks-border-default);
    }

    /**
     * A filter the user applied desaturates rather than only fading: on a dark surface the card
     * is already close to the canvas colour, so opacity alone is weak, and losing colour also
     * survives the compact detail level where only the tile tint remains.
     */
    .asset-card.is-dimmed {
        opacity: 0.4;
        filter: grayscale(1);
    }

    // Transient and fires on every mouse move, so it only fades; desaturating would strobe.
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
        // A ring, not a fill: a full-colour plugin logo leaves only a 10% tint, which vanishes
        // under dbt orange. The ring survives any logo and the compact detail level. Not the
        // CARD border, so "border means selection" still holds.
        border: 1px solid color-mix(in srgb, var(--ks-dag-status) 60%, transparent);
        border-radius: var(--ks-radius-lg);
        background: color-mix(in srgb, var(--ks-dag-status) 10%, var(--ks-bg-badge));
        // Inherited by the monochrome fallback glyph; row 2's glyph sets its own colour.
        color: var(--ks-dag-status);
    }

    // A plugin's own artwork, so it only gets sized; the tile tint shows behind it.
    .asset-logo {
        width: var(--ks-spacing-5);
        height: var(--ks-spacing-5);
    }

    .asset-body {
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: var(--ks-spacing-1);
        min-width: 0;
    }

    // Monospace because asset ids are dotted paths: it aligns the segment that varies.
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

    // The age yields first: clipped it still reads, and the glyph still carries the status.
    .asset-age {
        flex: 1 1 auto;
        min-width: 0;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    // Right-aligned so row 2 reads as two things rather than one run-on string: one size step
    // and one colour step apart was not enough to stop "21 hours ago Table" reading as prose.
    .asset-type {
        flex: 0 0 auto;
        max-width: 40%;
        margin-left: auto;
        padding-left: var(--ks-spacing-2);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    /**
     * Coloured for the states we know, neutral for the two meaning "no signal", so colour
     * answers "do we know?" before "is it healthy?". Emphasis stays on the tile: fresh keeps a
     * 60% ring, stale and failed a full-strength one, so a healthy graph is not a wall of green.
     */
    .asset-card.status-fresh .asset-age {
        color: var(--ks-text-success);
    }

    .asset-card.status-stale .asset-age {
        color: var(--ks-text-warning);
    }

    .asset-card.status-failed .asset-age {
        color: var(--ks-text-error);
    }

    .asset-card.status-stale .asset-tile,
    .asset-card.status-failed .asset-tile {
        border-color: var(--ks-dag-status);
        background: color-mix(in srgb, var(--ks-dag-status) 18%, var(--ks-bg-badge));
    }
</style>
