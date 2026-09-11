<template>
    <div :class="['card', status, cardState]">
        <Handle type="target" :position="Position.Left" />

        <div class="tile">
            <component
                :is="taskIconComponent"
                v-if="data.iconCls"
                :cls="data.iconCls"
                :icons="pluginsStore.icons"
                :loadIcon="pluginsStore.loadIcon"
                onlyIcon
                class="logo"
            />
            <KsIcon v-else size="base">
                <component :is="data.isFlow ? Sitemap : PackageVariantClosed" />
            </KsIcon>
        </div>

        <div class="body">
            <span class="name" :title="id">{{ data.name }}</span>

            <div v-if="!data.isFlow" class="meta">
                <KsIcon
                    size="xs"
                    class="state"
                    :color="statusColorOf(status)"
                    :tooltip="statusLabel"
                >
                    <component :is="statusIconOf(status)" />
                </KsIcon>
                <span v-if="data.updated" class="age"><KsDateAgo :date="data.updated" /></span>
                <KsText
                    v-else
                    size="small"
                    class="age"
                >{{ statusLabel }}</KsText>
                <KsText
                    v-if="typeName"
                    size="small"
                    class="kind"
                >{{ typeName }}</KsText>
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
    import Sitemap from "vue-material-design-icons/Sitemap.vue"
    import PackageVariantClosed from "vue-material-design-icons/PackageVariantClosed.vue"
    import {usePluginsStore} from "../../../../stores/plugins"
    import {statusIconOf, statusColorOf, normalizeStatus} from "../../utils/assetStatus"
    import {DAG_SELECTED, DAG_HOVERED, DAG_TRACED, DAG_SHOWN} from "../../utils/dagConstants"

    const props = defineProps<{
        id: string;
        data: {
            name: string;
            iconCls?: string;
            isFlow?: boolean;
            assetType?: string;
            status: string;
            updated?: string;
        };
    }>()

    const {t} = useI18n({useScope: "global"})
    const taskIconComponent = useTaskIcon()
    const pluginsStore = usePluginsStore()

    const selected = inject(DAG_SELECTED)
    const hovered = inject(DAG_HOVERED)
    const traced = inject(DAG_TRACED)
    const shown = inject(DAG_SHOWN)

    const status = computed(() => normalizeStatus(props.data.status))
    const typeName = computed(() => (props.data.assetType ? stringUtils.afterLastDot(props.data.assetType) : undefined))
    const statusLabel = computed(() => t(`dependency.dag.status.${status.value}`))

    /** One class, not several booleans: the states are exclusive and stacking them clashed. */
    const cardState = computed(() => {
        if (selected?.value === props.id) {
            return "selected"
        }
        if (hovered?.value === props.id) {
            return "hovered"
        }
        if (shown?.value && !shown.value.has(props.id)) {
            return "dimmed"
        }
        if (traced?.value) {
            return traced.value.has(props.id) ? "traced" : "faded"
        }
        return ""
    })
</script>

<style lang="scss" scoped>
    .card {
        --ks-dag-status: var(--ks-status-neutral);

        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        width: var(--ks-dag-card-width);
        height: var(--ks-dag-card-height);
        box-sizing: border-box;
        overflow: hidden;
        padding: var(--ks-spacing-2);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);
        box-shadow: 0 0.125rem 0.25rem var(--ks-shadow-surface);
        transition: opacity var(--ks-duration-fast), filter var(--ks-duration-fast), border-color var(--ks-duration-fast);

        &.fresh {
            --ks-dag-status: var(--ks-status-success);
        }

        &.stale {
            --ks-dag-status: var(--ks-status-warning);
        }

        &.failed {
            --ks-dag-status: var(--ks-status-error);
        }

        &.selected {
            border-color: var(--ks-border-focus);
            box-shadow: 0 0 0 1px var(--ks-border-focus);
        }

        &.hovered {
            border-color: var(--ks-border-focus);
        }

        &.traced {
            border-color: var(--ks-border-default);
        }

        &.dimmed {
            opacity: 0.4;
            filter: grayscale(1);
        }

        &.faded {
            opacity: 0.6;
        }

        .tile {
            display: flex;
            align-items: center;
            justify-content: center;
            flex: 0 0 auto;
            width: var(--ks-spacing-7);
            height: var(--ks-spacing-7);
            box-sizing: border-box;
            border: 1px solid color-mix(in srgb, var(--ks-dag-status) 60%, transparent);
            border-radius: var(--ks-radius-lg);
            background: color-mix(in srgb, var(--ks-dag-status) 10%, var(--ks-bg-badge));
            color: var(--ks-dag-status);
        }

        .logo {
            width: var(--ks-spacing-5);
            height: var(--ks-spacing-5);
        }

        .body {
            display: flex;
            flex-direction: column;
            justify-content: center;
            gap: var(--ks-spacing-1);
            min-width: 0;
        }

        .name {
            font-family: var(--ks-font-family-mono);
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-primary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .meta {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            min-width: 0;
        }

        .state {
            color: var(--ks-dag-status);
            flex: 0 0 auto;
        }

        .age {
            flex: 1 1 auto;
            min-width: 0;
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-secondary);
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .kind {
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

        &.fresh .age {
            color: var(--ks-text-success);
        }

        &.stale .age {
            color: var(--ks-text-warning);
        }

        &.failed .age {
            color: var(--ks-text-error);
        }

        &.stale .tile,
        &.failed .tile {
            border-color: var(--ks-dag-status);
            background: color-mix(in srgb, var(--ks-dag-status) 18%, var(--ks-bg-badge));
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .card {
            transition: none;
        }
    }
</style>