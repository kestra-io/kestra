<template>
    <div :class="['asset-card', `status-${status}`, cardState]">
        <!-- Without handles vue-flow has nothing to anchor an edge to, ignores the node's
             sourcePosition/targetPosition and falls back to top/bottom attachment, which
             sends every edge on a detour around the card. topology.scss hides them. -->
        <Handle type="target" :position="Position.Left" />

        <!-- The tile is topology's node signature, so the asset graph reads as a sibling
             of the flow graph. It carries freshness as a tint because row 2 disappears at
             the compact detail level, and status has to survive that. -->
        <div class="asset-tile">
            <!-- The producing plugin's logo, which is why the tile was sized for one. Falls
                 back to the asset type's own icon, so a never-run asset still gets a real
                 glyph. Assets always carry a type, so the material branch below is reached
                 only by flow nodes; a cls that resolves nothing shows TaskIcon's own
                 placeholder rather than this fallback. -->
            <component
                :is="taskIconComponent"
                v-if="iconCls"
                :cls="iconCls"
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
            <span class="asset-name" :title="id">{{ name }}</span>

            <div v-if="detail === 'full'" class="asset-meta">
                <!-- Colour via the prop, not the class: a class lands on the ElIcon root
                     where a kel-icon rule already sets colour and wins, so the glyph kept
                     the default icon colour instead of the status one. -->
                <KsIcon size="xs" class="asset-status" :color="statusColor" :tooltip="statusLabel">
                    <component :is="statusIcon" />
                </KsIcon>
                <!-- Wrapped in a span this component owns: KsDateAgo renders its text inside
                     KsTooltip, so the span is not the component root and never receives the
                     scoped attribute. Passing className styled nothing, which is why the age
                     wrapped to a second line and refused to shrink. -->
                <span v-if="updated" class="asset-age"><KsDateAgo :date="updated" /></span>
                <KsText v-else size="small" class="asset-age">{{ statusLabel }}</KsText>
                <!-- Durable where the dbt manifest's `kind` was demo scaffolding, and
                     populated on every asset. A technical identifier, so no i18n key. -->
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

    import {DAG_SELECTED, DAG_HOVERED, DAG_TRACED, DAG_DIMMED, DAG_DETAIL} from "../../utils/dagConstants"

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

    const name = computed(() => props.data.name)
    // Normalised, not raw: an unrecognised value would interpolate straight into the
    // i18n key below and render `dependency.dag.status.<value>` on the card.
    const status = computed(() => normalizeStatus(props.data.status))
    const updated = computed(() => props.data.updated)
    const iconCls = computed(() => props.data.iconCls)
    const fallbackIcon = computed(() => (props.data.isFlow ? Sitemap : PackageVariantClosed))
    const typeName = computed(() => (props.data.assetType ? stringUtils.afterLastDot(props.data.assetType) : undefined))
    const statusIcon = computed(() => statusIconOf(status.value))
    const statusColor = computed(() => statusColorOf(status.value))
    const statusLabel = computed(() => t(`dependency.dag.status.${status.value}`))

    const selected = inject(DAG_SELECTED)
    const hovered = inject(DAG_HOVERED)
    const traced = inject(DAG_TRACED)
    const dimmed = inject(DAG_DIMMED)
    const detail = inject(DAG_DETAIL)

    /**
     * One class rather than several booleans: the states are mutually exclusive in
     * priority order, and letting them stack produced a dimmed-and-selected card.
     */
    const cardState = computed(() => {
        if (selected?.value === props.id) return "is-selected"
        // Above the dim: hovering a side-table row exists to find that node on the canvas,
        // so it has to show even when the node sits outside an isolated group.
        if (hovered?.value === props.id) return "is-hovered"
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

    // The node being pointed at, from the canvas or from a side-table row. Shares the
    // selection colour but not its ring, so a hovered card and a selected one stay
    // distinguishable. Opacity and filter are reset so it shows through a dim it may be
    // sitting under, which is the whole point when the row is outside an isolated group.
    .asset-card.is-hovered {
        border-color: var(--ks-border-focus);
        opacity: 1;
        filter: none;
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
        // The border, not the fill, is what carries freshness now. The tile used to draw a
        // monochrome glyph in the status colour, and that glyph was doing most of the work;
        // a full-colour plugin logo replaces it and would leave only a 10% tint, which
        // disappears under dbt orange on a warning tint. A ring survives any logo, and it
        // survives the compact detail level where row 2 is hidden. Still not the CARD
        // border, so "border means selection" holds.
        border: 1px solid color-mix(in srgb, var(--ks-dag-status) 60%, transparent);
        border-radius: var(--ks-radius-lg);
        background: color-mix(in srgb, var(--ks-dag-status) 10%, var(--ks-bg-badge));
        // Inherited by the fallback material glyph, which is still monochrome. The status
        // glyph in row 2 sets its own colour through KsIcon's prop.
        color: var(--ks-dag-status);
    }

    // The logo is a plugin's own artwork, so it keeps its colours and only gets sized;
    // the tile's status tint stays visible as the surface behind it.
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

    // The age is the item that yields: a clipped "a few seco..." still reads and the glyph
    // still carries the status, whereas a type clipped to "Ta..." is noise.
    .asset-age {
        flex: 1 1 auto;
        min-width: 0;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    // Dimmer than the age: the type is context you read once, not a value that changes.
    // Shrinks before the age does, since the age is the freshness signal on this row.
    // Pushed to the right edge so row 2 reads as two things (freshness left, type right)
    // rather than one run-on string: at one size step and one colour step apart, an 8px
    // gap was not enough separation to stop "21 hours ago Table" reading as a sentence.
    .asset-type {
        flex: 0 0 auto;
        // Only a guard against an absurd type name; normal ones never hit it.
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
     * The age text is coloured for every state we actually know, and left neutral for the
     * two that mean "no signal" (never run, not tracked). Colour therefore answers "do we
     * know?" before it answers "is it healthy?", which is what stops a fresh asset and an
     * untracked one from both reading as grey text.
     *
     * Emphasis still lives on the tile rather than here: fresh keeps a 60% ring, stale and
     * failed get a full-strength ring and a stronger tint. That is what keeps a healthy
     * graph from becoming a wall of green, and it is the part that survives the compact
     * detail level, where row 2 and its coloured text are gone entirely.
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
