<template>
    <template v-if="execution">
        <KsPopover
            v-model:visible="popoverVisible"
            trigger="click"
            placement="top"
            :width="280"
            :showArrow="true"
        >
            <template #reference>
                <button
                    ref="barRef"
                    type="button"
                    class="timeline-bar"
                    :class="{dimmed}"
                    :data-state="execution.state"
                    :style="barStyle"
                    :aria-label="`${execution.flowId} — ${execution.state}`"
                    @mouseenter="tooltipVisible = true"
                    @mouseleave="tooltipVisible = false"
                    @focus="tooltipVisible = true"
                    @blur="tooltipVisible = false"
                />
            </template>

            <div class="timeline-bar-popover">
                <div class="popover-id">
                    <KsId :value="execution.id" :shrink="true" />
                </div>
                <div class="popover-row">
                    <span class="k">{{ $t("namespace") }}</span>
                    <span class="v">{{ execution.namespace }}</span>
                </div>
                <div class="popover-row">
                    <span class="k">{{ $t("flow") }}</span>
                    <span class="v">{{ execution.flowId }}</span>
                </div>
                <div class="popover-row">
                    <span class="k">{{ $t("state") }}</span>
                    <span class="v">{{ execution.state }}</span>
                </div>
                <div class="popover-row">
                    <span class="k">{{ $t("start date") }}</span>
                    <span class="v">{{ formattedStart }}</span>
                </div>
                <div class="popover-row">
                    <span class="k">{{ $t("end date") }}</span>
                    <span class="v">{{ formattedEnd }}</span>
                </div>
                <div class="popover-actions">
                    <KsButton type="primary" @click="openExecution">
                        {{ $t("executionsTimeline.popover.openExecution") }}
                    </KsButton>
                    <KsButton @click="showOnlyThisFlow">
                        {{ $t("executionsTimeline.popover.showOnlyFlow") }}
                    </KsButton>
                </div>
            </div>
        </KsPopover>

        <KsTooltip
            trigger="manual"
            placement="top"
            :rawContent="true"
            :visible="tooltipVisible && !popoverVisible"
            :virtualRef="barRef"
            virtualTriggering
        >
            <template #content>
                <div class="timeline-bar-tooltip">
                    <strong>{{ execution.flowId }}</strong>
                    <span>{{ execution.namespace }}</span>
                    <span>{{ execution.state }}</span>
                    <span>{{ formattedStart }} &rarr; {{ formattedEnd }}</span>
                    <span>{{ $t("id") }}: {{ execution.id }}</span>
                </div>
            </template>
        </KsTooltip>
    </template>

    <KsTooltip v-else-if="bucket" placement="top" :rawContent="true">
        <template #content>
            <div class="timeline-bar-tooltip">
                <strong>{{ bucket.total }} {{ $t("executions") }}</strong>
                <span v-for="[state, count] in Object.entries(bucket.byState)" :key="state">{{ state }}: {{ count }}</span>
                <span class="hint">{{ $t("executionsTimeline.bucket.hint") }}</span>
            </div>
        </template>
        <div
            class="timeline-bar timeline-bucket"
            :class="{dimmed}"
            :data-state="bucket.dominantState"
            :style="bucketStyle"
        />
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useRouter} from "vue-router"
    import {dateUtils} from "@kestra-io/design-system"
    import type {StateBucket, TimelineExecution} from "../../../utils/executionsTimeline"

    const props = defineProps<{
        execution?: TimelineExecution;
        bucket?: StateBucket;
        leftPercent: number;
        widthPercent: number;
        dimmed: boolean;
    }>()

    const emit = defineEmits<{
        "show-only-flow": [{namespace: string; flowId: string}];
    }>()

    const router = useRouter()
    const popoverVisible = ref(false)
    const tooltipVisible = ref(false)
    const barRef = ref<HTMLButtonElement | null>(null)

    const barStyle = computed(() => ({
        left: `${props.leftPercent}%`,
        width: `${Math.max(props.widthPercent, 0.3)}%`,
    }))

    const bucketStyle = barStyle

    const formattedStart = computed(() => props.execution ? dateUtils.dateFilter(new Date(props.execution.startMs).toISOString()) : "")
    const formattedEnd = computed(() => props.execution ? dateUtils.dateFilter(new Date(props.execution.endMs).toISOString()) : "")

    function openExecution() {
        if (!props.execution) return
        popoverVisible.value = false
        tooltipVisible.value = false
        router.push({
            name: "executions/update",
            params: {
                namespace: props.execution.namespace,
                flowId: props.execution.flowId,
                id: props.execution.id,
            },
        })
    }

    function showOnlyThisFlow() {
        if (!props.execution) return
        popoverVisible.value = false
        tooltipVisible.value = false
        emit("show-only-flow", {namespace: props.execution.namespace, flowId: props.execution.flowId})
    }
</script>

<style scoped lang="scss">
.timeline-bar {
    position: absolute;
    top: 0.3rem;
    bottom: 0.3rem;
    min-width: 0.1875rem;
    margin: 0;
    padding: 0;
    border: none;
    border-radius: var(--ks-radius-xs);
    font: inherit;
    cursor: pointer;
    outline: 2px solid transparent;
    outline-offset: 1px;
    transition: opacity var(--ks-duration-fast) ease, outline-color var(--ks-duration-fast) ease;

    &:hover,
    &:focus-visible {
        outline-color: var(--ks-border-focus);
        z-index: 5;
    }

    &.dimmed {
        opacity: 0.2;
    }
}

.timeline-bucket {
    cursor: default;
}

.timeline-bar[data-state="SUCCESS"] {
    background: var(--ks-chart-success);
}

.timeline-bar[data-state="FAILED"] {
    background: var(--ks-chart-failed);
}

.timeline-bar[data-state="WARNING"] {
    background: var(--ks-chart-warning);
}

.timeline-bar[data-state="PAUSED"] {
    background: var(--ks-chart-paused);
}

.timeline-bar[data-state="CANCELLED"] {
    background: var(--ks-chart-cancelled);
}

.timeline-bar[data-state="SKIPPED"] {
    background: var(--ks-chart-skipped);
}

.timeline-bar[data-state="CREATED"] {
    background: var(--ks-chart-created);
}

.timeline-bar[data-state="RESTARTED"] {
    background: var(--ks-chart-restarted);
}

.timeline-bar[data-state="RETRIED"] {
    background: var(--ks-chart-retried);
}

.timeline-bar[data-state="RETRYING"] {
    background: var(--ks-chart-retrying);
}

// Static diagonal stripe: distinguishes the "waiting" QUEUED state from a solid fill without relying on color alone.
.timeline-bar[data-state="QUEUED"] {
    background: repeating-linear-gradient(
        45deg,
        var(--ks-chart-queued),
        var(--ks-chart-queued) 0.3125rem,
        color-mix(in srgb, var(--ks-chart-queued) 55%, transparent) 0.3125rem,
        color-mix(in srgb, var(--ks-chart-queued) 55%, transparent) 0.625rem
    );
}

// Animated diagonal stripe: RUNNING/KILLING are in-progress states, visually distinct from QUEUED's static stripe.
.timeline-bar[data-state="RUNNING"],
.timeline-bar[data-state="KILLING"] {
    background-size: 200% 200%;
    animation: timeline-bar-stripe-move 900ms linear infinite;
}

.timeline-bar[data-state="RUNNING"] {
    background-image: repeating-linear-gradient(
        45deg,
        var(--ks-chart-running),
        var(--ks-chart-running) 0.3125rem,
        color-mix(in srgb, var(--ks-chart-running) 65%, transparent) 0.3125rem,
        color-mix(in srgb, var(--ks-chart-running) 65%, transparent) 0.625rem
    );
}

.timeline-bar[data-state="KILLING"] {
    background-image: repeating-linear-gradient(
        45deg,
        var(--ks-chart-killing),
        var(--ks-chart-killing) 0.3125rem,
        color-mix(in srgb, var(--ks-chart-killing) 55%, transparent) 0.3125rem,
        color-mix(in srgb, var(--ks-chart-killing) 55%, transparent) 0.625rem
    );
}

// Diagonal hatch, opposite angle and denser than the RUNNING/KILLING stripe, so KILLED never reads as "in progress".
.timeline-bar[data-state="KILLED"] {
    background: repeating-linear-gradient(
        135deg,
        var(--ks-chart-killed),
        var(--ks-chart-killed) 0.1875rem,
        color-mix(in srgb, var(--ks-chart-killed) 55%, black 15%) 0.1875rem,
        color-mix(in srgb, var(--ks-chart-killed) 55%, black 15%) 0.375rem
    );
}

@keyframes timeline-bar-stripe-move {
    from {
        background-position: 0 0;
    }
    to {
        background-position: 0.875rem 0;
    }
}

.timeline-bar-tooltip {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-1);
    font-size: var(--ks-font-size-xs);

    .hint {
        color: var(--ks-text-secondary);
    }
}

.timeline-bar-popover {
    display: flex;
    flex-direction: column;

    .popover-id {
        margin-bottom: var(--ks-spacing-2);
    }

    .popover-row {
        display: flex;
        justify-content: space-between;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-1) 0;
        border-bottom: 1px solid var(--ks-border-subtle);
        font-size: var(--ks-font-size-sm);

        &:last-of-type {
            border-bottom: none;
        }

        .k {
            color: var(--ks-text-secondary);
        }

        .v {
            color: var(--ks-text-primary);
            font-weight: 500;
            text-align: right;
        }
    }

    .popover-actions {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        margin-top: var(--ks-spacing-3);
    }
}
</style>
