<template>
    <div class="panel-loop">
        <div class="loop-toolbar">
            <KsSegmented
                v-model="drillDown.state.value"
                size="small"
                :options="stateOptions"
                data-test="gantt-loop-state-filter"
            />
            <KsInput
                v-model="drillDown.search.value"
                size="small"
                clearable
                :prefixIcon="Magnify"
                :placeholder="$t('gantt_panel.search_value')"
                :aria-label="$t('gantt_panel.search_value')"
                data-test="gantt-loop-search"
            />
        </div>

        <KsSkeleton v-if="drillDown.loading.value && drillDown.rows.value.length === 0" animated :rows="4" />
        <KsAlert v-else-if="drillDown.error.value" type="error" :closable="false">
            {{ $t("loop_iterations_load_error") }}
            <KsButton size="small" link @click="drillDown.load()">{{ $t("retry") }}</KsButton>
        </KsAlert>
        <p v-else-if="drillDown.rows.value.length === 0" class="loop-empty">{{ $t("no_iterations") }}</p>
        <div v-else class="mini-list">
            <GanttPanelListRow
                v-for="row in drillDown.rows.value"
                :key="row.id"
                :name="row.value"
                emphasized
                :offset="offset(row.state.histories)"
                :histories="row.state.histories"
                :state="row.state.current"
                data-test="gantt-loop-iteration"
                @click="tree.selectIteration(node, row)"
            >
                <template #leading>
                    <span class="iteration-index">#{{ row.index + 1 }}</span>
                </template>
            </GanttPanelListRow>
        </div>

        <div class="loop-pager">
            <span>{{ $t("gantt_panel.range", {from: drillDown.firstShown.value, to: drillDown.lastShown.value, total: drillDown.total.value}) }}</span>
            <span class="pager-actions">
                <KsIconButton
                    :aria-label="$t('previous')"
                    :disabled="drillDown.page.value <= 1"
                    @click="drillDown.goToPage(drillDown.page.value - 1)"
                >
                    <ChevronUp />
                </KsIconButton>
                <KsIconButton
                    :aria-label="$t('next')"
                    :disabled="drillDown.page.value >= drillDown.pageCount.value"
                    @click="drillDown.goToPage(drillDown.page.value + 1)"
                >
                    <ChevronDown />
                </KsIconButton>
                <KsButton :tag="RouterLink" size="small" link :icon="OpenInNew" :to="allExecutionsRoute">
                    {{ $t("show_all_executions") }}
                </KsButton>
            </span>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {RouterLink} from "vue-router"
    import {useI18n} from "vue-i18n"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import {KsAlert, KsButton, KsIconButton, KsInput, KsSegmented, KsSkeleton} from "@kestra-io/design-system"
    import GanttPanelListRow from "./GanttPanelListRow.vue"
    import {loopRefOf, useGanttTree, type TaskRunNode} from "./ganttTree"
    import {useLoopDrillDown} from "./useLoopDrillDown"
    import {useGanttTimeline} from "../ganttTimeline"

    const props = defineProps<{
        node: TaskRunNode;
    }>()

    const {t} = useI18n()
    const tree = useGanttTree()
    const {offset} = useGanttTimeline()
    const drillDown = useLoopDrillDown(loopRefOf(props.node))

    const stateOptions = computed(() => [
        {value: "all", label: t("gantt_panel.filter_all", {count: drillDown.counts.value.all ?? "…"})},
        {value: "failed", label: t("gantt_panel.filter_failed", {count: drillDown.counts.value.failed ?? "…"})},
        {value: "success", label: t("gantt_panel.filter_success", {count: drillDown.counts.value.success ?? "…"})},
    ])

    const allExecutionsRoute = {
        name: "executions/list",
        query: {
            "filters[parentId][EQUALS]": props.node.executionId,
            "filters[kind][EQUALS]": "LOOP",
            "filters[taskId][EQUALS]": props.node.taskRun.taskId,
        },
    }
</script>

<style scoped>
    .panel-loop {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
    }

    .loop-toolbar {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);

        & > :last-child {
            flex: 1;
        }
    }

    .loop-empty {
        margin: 0;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .mini-list {
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-sm);
        overflow: hidden;
    }

    .iteration-index {
        display: grid;
        place-items: center;
        min-width: 1.75rem;
        height: 1.75rem;
        padding: 0 var(--ks-spacing-1);
        border: var(--ks-border-width-thin) dashed var(--ks-border-strong);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-base);
        color: var(--ks-text-secondary);
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-2xs);
    }

    .loop-pager {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
    }

    .pager-actions {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }
</style>
