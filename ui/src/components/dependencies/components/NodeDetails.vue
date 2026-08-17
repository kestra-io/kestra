<template>
    <div class="node-details">
        <KsButton size="small" class="details-back" @click="emit('close')">
            <ArrowLeft />
            {{ $t("back") }}
        </KsButton>

        <header class="details-heading">
            <KsText size="large" tag="h3" class="details-title">
                {{ shortName }}
            </KsText>
            <span v-if="status" :class="['details-status', `status-${status}`]">
                {{ $t(`dependency.dag.status.${status}`) }}
            </span>
        </header>

        <Link :node :subtype />

        <dl class="details-grid">
            <template v-for="row in rows" :key="row.label">
                <dt class="details-label">
                    {{ row.label }}
                </dt>
                <dd class="details-value">
                    <KsDateAgo v-if="row.date" :inverted="true" :date="row.value" />
                    <template v-else>
                        {{ row.value }}
                    </template>
                </dd>
            </template>
        </dl>

        <section v-if="runs.length" class="details-runs">
            <h4 class="details-label">
                {{ $t("dependency.dag.recent_runs") }}
            </h4>

            <RouterLink
                v-for="run in runs"
                :key="run.executionId"
                class="run-row"
                :to="executionRoute(run)"
            >
                <KsExecutionStatus v-if="run.state" :status="run.state" size="small" />
                <span class="run-date">
                    <KsDateAgo :inverted="true" :date="run.created" />
                </span>
            </RouterLink>
        </section>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute} from "vue-router"

    import ArrowLeft from "vue-material-design-icons/ArrowLeft.vue"

    import Link from "./Link.vue"
    import {ASSET} from "../utils/types"
    import type {Types, Node, AssetRun} from "../utils/types"

    const props = defineProps<{
        node: Node;
        subtype: Types;
    }>()

    const emit = defineEmits<{close: []}>()

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()

    const metadata = computed(() => props.node.metadata as {
        subtype: Types;
        kind?: string;
        system?: string;
        updated?: string;
        status?: string;
        runs?: AssetRun[];
    })

    const shortName = computed(() => props.node.flow.split(".").pop() || props.node.flow)

    const status = computed(() => (metadata.value.subtype === ASSET ? metadata.value.status : undefined))

    // A run without an execution id cannot be linked to, and would collide as a v-for key.
    const runs = computed(() => (metadata.value.runs ?? []).filter((run) => run.executionId))

    const rows = computed(() => [
        {label: t("dependency.dag.last_update"), value: metadata.value.updated, date: true},
        {label: t("dependency.dag.kind"), value: metadata.value.kind},
        {label: t("dependency.dag.system"), value: metadata.value.system},
        {label: t("namespace"), value: props.node.namespace},
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
/* One type scale for the whole panel: labels 12 secondary, values 13 primary. */
.node-details {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-4);
    padding: var(--ks-spacing-4);
    font-size: var(--ks-font-size-sm);
    line-height: 1.5;
}

.details-back {
    align-self: flex-start;
}

.details-heading {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: var(--ks-spacing-3);
}

.details-title {
    margin: 0;
    word-break: break-all;
}

.details-status {
    flex-shrink: 0;
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
    text-transform: uppercase;
}

.status-fresh {
    color: var(--ks-status-success);
}

.status-stale {
    color: var(--ks-status-warning);
}

.status-failed {
    color: var(--ks-status-error);
}

.status-never,
.status-unknown {
    color: var(--ks-text-secondary);
}

.details-grid {
    display: grid;
    grid-template-columns: 8rem 1fr;
    gap: var(--ks-spacing-2) var(--ks-spacing-3);
    margin: 0;
    padding-top: var(--ks-spacing-3);
    border-top: 1px solid var(--ks-border-subtle);
}

.details-label {
    margin: 0;
    font-size: var(--ks-font-size-xs);
    font-weight: 400;
    color: var(--ks-text-secondary);
}

.details-value {
    margin: 0;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
    word-break: break-all;
}

.details-runs {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
    padding-top: var(--ks-spacing-3);
    border-top: 1px solid var(--ks-border-subtle);
}

.details-runs .details-label {
    padding-bottom: var(--ks-spacing-1);
}

.run-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-2);
    border-radius: var(--ks-radius-sm);
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
}

.run-row:hover {
    background: var(--ks-bg-hover);
}

.run-date {
    color: var(--ks-text-secondary);
}
</style>
