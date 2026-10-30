<template>
    <div class="stepper" data-test="gantt-iteration-stepper">
        <KsButton
            size="small"
            :icon="ChevronUp"
            :disabled="!canStepPrevious"
            :aria-label="$t('gantt_panel.previous_iteration')"
            data-test="gantt-step-previous"
            @click="step(-1)"
        >
            {{ previous ? iterationLabel(previous) : $t("previous") }}
        </KsButton>
        <span class="stepper-current">
            <strong>{{ iterationLabel(iteration.row) }}</strong>
            <span>{{ $t("gantt_panel.iteration_position", {position: iteration.row.index + 1, total: totalCount, loop: iteration.loop.taskId}) }}</span>
        </span>
        <KsButton
            size="small"
            :disabled="!canStepNext"
            :aria-label="$t('gantt_panel.next_iteration')"
            data-test="gantt-step-next"
            @click="step(1)"
        >
            {{ next ? iterationLabel(next) : $t("next") }}
            <KsIcon size="sm"><ChevronDown /></KsIcon>
        </KsButton>
        <KsButton
            :tag="RouterLink"
            size="small"
            :icon="AxisYArrow"
            :to="iterationRoute"
            data-test="gantt-open-iteration"
        >
            {{ $t("gantt_panel.open_iteration") }}
        </KsButton>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {RouterLink, useRoute} from "vue-router"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import AxisYArrow from "vue-material-design-icons/AxisYArrow.vue"
    import {KsButton, KsIcon} from "@kestra-io/design-system"
    import {iterationLabel, loadedNeighbour, owningIteration, useGanttTree, type GanttNode} from "./ganttTree"

    const props = defineProps<{
        node: GanttNode;
    }>()

    const tree = useGanttTree()
    const route = useRoute()

    const iteration = computed(() => owningIteration(props.node)!)
    const handle = computed(() => tree.loopHandle(iteration.value.loop))
    const totalCount = computed(() => handle.value.totalCount.value ?? handle.value.total.value)
    const before = computed(() => loadedNeighbour(handle.value, iteration.value.row, -1))
    const after = computed(() => loadedNeighbour(handle.value, iteration.value.row, 1))
    const previous = computed(() => before.value.row)
    const next = computed(() => after.value.row)
    const canStepPrevious = computed(() => iteration.value.row.index > 0 && (!before.value.known || previous.value !== undefined))
    const canStepNext = computed(() => !after.value.known || next.value !== undefined)

    const iterationRoute = computed(() => ({
        name: "executions/update/gantt",
        params: {
            namespace: iteration.value.loop.namespace,
            flowId: iteration.value.loop.flowId,
            id: iteration.value.row.id,
            tenant: route.params.tenant,
        },
    }))

    function step(delta: -1 | 1) {
        return tree.stepIteration(props.node, delta)
    }
</script>

<style scoped>
    .stepper {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1);
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-base);
    }

    .stepper-current {
        flex: 1;
        min-width: 0;
        display: flex;
        align-items: baseline;
        gap: var(--ks-spacing-2);
        overflow: hidden;
        white-space: nowrap;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);

        strong {
            font-family: var(--ks-font-family-mono);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-primary);
        }
    }
</style>
