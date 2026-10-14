<template>
    <div class="details">
        <KsButton
            link
            size="small"
            class="back"
            @click="emit('close')"
        >
            <ArrowLeft />
            <span class="label">{{ $t("back") }}</span>
        </KsButton>

        <header class="heading">
            <KsText
                size="large"
                tag="h3"
                class="title"
            >
                {{ shortName }}
            </KsText>
            <span v-if="status" :class="['state', status]">
                <KsIcon size="xs" :color="statusColor">
                    <component :is="statusIcon" />
                </KsIcon>
                {{ $t(`dependency.dag.status.${status}`) }}
            </span>
        </header>

        <Link :node :subtype="node.metadata.subtype" />

        <dl class="grid">
            <template v-for="row in rows" :key="row.label">
                <dt class="label">
                    {{ row.label }}
                </dt>
                <dd :class="['value', {mono: row.mono}]">
                    <KsDateAgo
                        v-if="row.date"
                        inverted
                        :date="row.value"
                    />
                    <template v-else>
                        {{ row.value }}
                    </template>
                </dd>
            </template>
        </dl>

        <section v-if="runs.length" class="runs">
            <h4 class="label">
                {{ $t("dependency.dag.recent_runs") }}
            </h4>

            <RouterLink
                v-for="run in runs"
                :key="run.executionId"
                class="run"
                :to="executionRoute(run)"
            >
                <KsExecutionStatus
                    v-if="run.state"
                    :status="run.state"
                    size="small"
                />
                <span class="date">
                    <KsDateAgo
                        inverted
                        :date="run.created"
                    />
                </span>
            </RouterLink>
        </section>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"
    import {stringUtils} from "@kestra-io/design-system"
    import ArrowLeft from "vue-material-design-icons/ArrowLeft.vue"
    import Link from "./Link.vue"
    import {normalizeStatus, statusIconOf, statusColorOf} from "../utils/assetStatus"
    import {ASSET} from "../utils/types"
    import type {Types, Node, AssetRun} from "../utils/types"

    const props = defineProps<{
        node: Node;
    }>()

    const emit = defineEmits<{close: []}>()

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()

    const metadata = computed(() => props.node.metadata as {
        subtype: Types;
        assetType?: string;
        producer?: string;
        system?: string;
        updated?: string;
        status?: string;
        runs?: AssetRun[];
    })

    const shortName = computed(() => stringUtils.afterLastDot(props.node.flow) || props.node.flow)

    const status = computed(() =>
        (metadata.value.subtype === ASSET ? normalizeStatus(metadata.value.status) : undefined),
    )
    const statusIcon = computed(() => statusIconOf(status.value))
    const statusColor = computed(() => statusColorOf(status.value))

    const runs = computed(() => (metadata.value.runs ?? [])
        .filter((run) => run.executionId && run.namespace && run.flowId))

    const rows = computed(() => [
        {
            label: t("dependency.dag.last_update"),
            value: metadata.value.updated,
            date: true,
        },
        {
            label: t("type"),
            value: metadata.value.assetType ? stringUtils.afterLastDot(metadata.value.assetType) : undefined,
        },
        {
            label: t("plugins.names"),
            value: metadata.value.producer,
            mono: true,
        },
        {
            label: t("dependency.dag.system"),
            value: metadata.value.system,
        },
        {
            label: t("namespace"),
            value: props.node.namespace,
            mono: true,
        },
    ].filter((row) => Boolean(row.value)))

    const executionRoute = (run: AssetRun) => ({
        name: "executions/update",
        params: {
            tenant: route.params.tenant,
            namespace: run.namespace,
            flowId: run.flowId,
            id: run.executionId,
        },
    })
</script>

<style scoped lang="scss">
    .details {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        padding: var(--ks-spacing-4);
        font-size: var(--ks-font-size-sm);
        line-height: 1.5;

        .back {
            align-self: flex-start;
            color: var(--ks-text-secondary);

            &:hover {
                color: var(--ks-text-primary);
            }

            .label {
                margin-left: var(--ks-spacing-1);
            }
        }

        .heading {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-3);
        }

        .title {
            margin: 0;
            word-break: break-all;
        }

        .state {
            --ks-details-status: var(--ks-status-neutral);

            display: inline-flex;
            align-items: center;
            flex-shrink: 0;
            gap: var(--ks-spacing-2);
            padding: var(--ks-spacing-1) var(--ks-spacing-3);
            border: 1px solid color-mix(in srgb, var(--ks-details-status) 40%, transparent);
            border-radius: var(--ks-radius-lg);
            background: color-mix(in srgb, var(--ks-details-status) 12%, transparent);
            color: var(--ks-details-status);
            font-size: var(--ks-font-size-xs);
            font-weight: 600;
            text-transform: uppercase;

            &.fresh {
                --ks-details-status: var(--ks-status-success);
            }

            &.stale {
                --ks-details-status: var(--ks-status-warning);
            }

            &.failed {
                --ks-details-status: var(--ks-status-error);
            }
        }

        .grid {
            display: grid;
            grid-template-columns: 8rem 1fr;
            gap: var(--ks-spacing-2) var(--ks-spacing-3);
            margin: 0;
            padding-top: var(--ks-spacing-3);
            border-top: 1px solid var(--ks-border-subtle);
        }

        .label {
            margin: 0;
            font-size: var(--ks-font-size-xs);
            font-weight: 400;
            color: var(--ks-text-secondary);
        }

        .value {
            margin: 0;
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-primary);
            word-break: break-all;

            &.mono {
                font-family: var(--ks-font-family-mono);
                font-size: var(--ks-font-size-xs);
            }
        }

        .runs {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-1);
            padding-top: var(--ks-spacing-3);
            border-top: 1px solid var(--ks-border-subtle);

            .label {
                padding-bottom: var(--ks-spacing-1);
            }
        }

        .run {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-3);
            padding: var(--ks-spacing-2);
            border-radius: var(--ks-radius-sm);
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-primary);

            &:hover {
                background: var(--ks-bg-hover);
            }

            .date {
                color: var(--ks-text-secondary);
            }
        }
    }
</style>
