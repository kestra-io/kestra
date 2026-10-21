<template>
    <span v-if="handle.totalCount.value !== undefined" class="loop-chips" data-test="gantt-loop-chips">
        <KsTag size="xs" data-test="gantt-loop-total">
            {{ $t("gantt_loop_iterations_count", {count: handle.totalCount.value}, handle.totalCount.value) }}
        </KsTag>
        <KsTag
            v-if="handle.failedCount.value"
            type="error"
            size="xs"
            :icon="active ? FilterVariant : CloseCircleOutline"
            :closable="active"
            :title="$t(active ? 'gantt_loop_show_all' : 'gantt_loop_show_failed')"
            :aria-pressed="active"
            role="button"
            tabindex="0"
            class="failed-chip"
            data-test="gantt-loop-failed"
            @click.stop="toggle"
            @keydown.enter.stop.prevent="toggle"
            @keydown.space.stop.prevent="toggle"
            @close="toggle"
        >
            {{ $t("gantt_loop_failed_count", {count: handle.failedCount.value}) }}
        </KsTag>
    </span>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue"
    import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue"
    import FilterVariant from "vue-material-design-icons/FilterVariant.vue"
    import {KsTag} from "@kestra-io/design-system"
    import {useGanttTree, type LoopRef} from "./ganttTree"

    const props = defineProps<{
        loop: LoopRef;
        state: string;
        nodeKey: string;
    }>()

    const tree = useGanttTree()
    const handle = computed(() => tree.loopHandle(props.loop))
    const active = computed(() => handle.value.failedOnly.value)

    function toggle() {
        const showFailedOnly = !handle.value.failedOnly.value
        handle.value.setFailedOnly(showFailedOnly)
        if (showFailedOnly) tree.setExpanded(props.nodeKey, true)
    }

    watch(handle, (current) => current.loadCounts(), {immediate: true})
    watch(() => props.state, () => handle.value.loadCounts({force: true}))
</script>

<style scoped>
    .loop-chips {
        display: inline-flex;
        flex-shrink: 0;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .failed-chip {
        cursor: pointer;
    }
</style>
