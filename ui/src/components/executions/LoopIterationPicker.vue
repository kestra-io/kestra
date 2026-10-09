<template>
    <span v-if="disabledReason" class="loop-picker" data-test="loop-picker">
        <KsButton size="small" disabled data-test="loop-picker-disabled">
            {{ $t("topology-graph.loop.pick-first", {loop: lane.parentTaskId}) }}
        </KsButton>
    </span>
    <span v-else-if="lane.status === 'ready'" class="loop-picker" data-test="loop-picker" @click.stop>
        <KsPopover v-model:visible="open" trigger="click" placement="bottom-start" width="20rem" :showArrow="false">
            <template #reference>
                <KsButton
                    size="small"
                    class="loop-picker-trigger"
                    data-test="loop-picker-trigger"
                    :aria-label="$t('topology-graph.loop.picker-label', {loop: lane.taskId})"
                >
                    <span>{{ triggerLabel }}</span>
                    <MenuDown class="loop-picker-caret" />
                </KsButton>
            </template>
            <div class="loop-picker-panel" data-test="loop-picker-panel">
                <KsSearch
                    v-model="query"
                    clearable
                    :placeholder="$t('topology-graph.loop.search-placeholder')"
                    :aria-label="$t('topology-graph.loop.search-placeholder')"
                />
                <KsScrollbar maxHeight="15rem">
                    <button
                        type="button"
                        class="loop-picker-option"
                        :class="{'loop-picker-option-active': lane.scopedNumber === undefined}"
                        :aria-current="lane.scopedNumber === undefined ? 'true' : undefined"
                        data-test="loop-picker-all"
                        @click="emit('clear')"
                    >
                        <KsIcon size="xs" class="loop-picker-check"><Check /></KsIcon>
                        <span class="loop-picker-value">{{ $t("topology-graph.loop.all-iterations") }}</span>
                    </button>
                    <KsAlert v-if="failure" type="error" :closable="false" data-test="loop-picker-failure">
                        {{ $t(`topology-graph.loop.failure-${failure}`) }}
                        <KsButton v-if="failure === 'unknown'" link size="small" data-test="loop-picker-retry" @click="load()">
                            {{ $t("topology-graph.loop.retry") }}
                        </KsButton>
                    </KsAlert>
                    <KsSkeleton v-else-if="loading && !groups.length" :rows="4" animated data-test="loop-picker-loading" />
                    <template v-else>
                        <template v-for="group in groups" :key="group.key">
                            <KsText size="small" class="loop-picker-group" :data-test="`loop-picker-group-${group.key}`">
                                {{ group.label }}
                            </KsText>
                            <button
                                v-for="iteration in group.items"
                                :key="iteration.id"
                                type="button"
                                class="loop-picker-option"
                                :class="{'loop-picker-option-active': iteration.number === lane.scopedNumber}"
                                :aria-current="iteration.number === lane.scopedNumber ? 'true' : undefined"
                                data-test="loop-picker-option"
                                @click="select(iteration.number)"
                            >
                                <KsIcon size="xs" class="loop-picker-check"><Check /></KsIcon>
                                <span class="loop-picker-number" data-test="loop-picker-number">#{{ iteration.number }}</span>
                                <span class="loop-picker-value" data-test="loop-picker-value">{{ iteration.value }}</span>
                                <KsExecutionStatus size="small" :status="iteration.state" />
                            </button>
                            <KsButton
                                v-if="group.key === 'failed' && failedItems.length < failedTotal"
                                link
                                size="small"
                                data-test="loop-picker-more-failed"
                                @click="loadMoreFailed()"
                            >
                                {{ $t("topology-graph.loop.load-more") }}
                            </KsButton>
                        </template>
                        <KsText v-if="!loading && !groups.length" size="small" class="loop-picker-empty" data-test="loop-picker-empty">
                            {{ $t("dependency.search.no_results", {term: query}) }}
                        </KsText>
                    </template>
                </KsScrollbar>
                <div v-if="pageCount > 1" class="loop-picker-pages">
                    <KsIconButton :ariaLabel="$t('topology-graph.loop.previous-page')" :disabled="page <= 1" @click="goToPage(page - 1)">
                        <ChevronLeft />
                    </KsIconButton>
                    <span>{{ page }} / {{ pageCount }}</span>
                    <KsIconButton :ariaLabel="$t('topology-graph.loop.next-page')" :disabled="page >= pageCount" @click="goToPage(page + 1)">
                        <ChevronRight />
                    </KsIconButton>
                </div>
            </div>
        </KsPopover>
        <span v-if="lane.scopedNumber !== undefined" class="loop-stepper" data-test="loop-stepper">
            <KsIconButton
                :ariaLabel="$t('topology-graph.loop.previous-iteration')"
                :disabled="lane.scopedNumber <= 1"
                data-test="loop-step-previous"
                @click.stop="emit('step', -1)"
            >
                <ChevronLeft />
            </KsIconButton>
            <span class="loop-stepper-label" data-test="loop-stepper-label">
                {{ $t("topology-graph.loop.step-of", {current: lane.scopedNumber, total: lane.iterationCount}) }}
            </span>
            <KsIconButton
                :ariaLabel="$t('topology-graph.loop.next-iteration')"
                :disabled="lane.scopedNumber >= (lane.iterationCount ?? 0)"
                data-test="loop-step-next"
                @click.stop="emit('step', 1)"
            >
                <ChevronRight />
            </KsIconButton>
        </span>
    </span>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {debounce} from "@kestra-io/design-system"
    import type {LoopLaneData} from "@kestra-io/topology"
    import MenuDown from "vue-material-design-icons/MenuDown.vue"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import {iterationLabel} from "../../utils/loopScope"
    import {
        ITERATIONS_PAGE_SIZE,
        LoopIterationError,
        failureOf,
        searchLoopIterations,
        type LoopIteration,
        type LoopIterationFailure,
    } from "../../utils/loopIterations"

    interface IterationGroup {
        key: string;
        label: string;
        items: LoopIteration[];
    }

    const props = defineProps<{
        lane: LoopLaneData;
        hostExecutionId?: string;
    }>()

    const emit = defineEmits<{
        select: [number: number];
        clear: [];
        step: [delta: number];
    }>()

    const {t} = useI18n()

    const open = ref(false)
    const query = ref("")
    const page = ref(1)
    const loading = ref(false)
    const failure = ref<LoopIterationFailure>()
    const failedItems = ref<LoopIteration[]>([])
    const failedTotal = ref(0)
    const otherItems = ref<LoopIteration[]>([])
    const matchItems = ref<LoopIteration[]>([])
    const total = ref(0)
    let latestSearch = 0

    const disabledReason = computed(() => !props.lane.parentScoped)

    const triggerLabel = computed(() =>
        props.lane.scopedNumber === undefined
            ? t("topology-graph.loop.all-iterations")
            : iterationLabel(t, props.lane.scopedNumber, props.lane.scopedValue),
    )

    const failedCount = computed(() => props.lane.terminatedIterations?.FAILED ?? 0)
    const pageCount = computed(() => Math.ceil(total.value / ITERATIONS_PAGE_SIZE))

    const numberQuery = computed(() => {
        const match = /^#?(\d+)$/.exec(query.value.trim())
        return match ? Number(match[1]) : undefined
    })

    const groups = computed<IterationGroup[]>(() => {
        const result: IterationGroup[] = []
        if (matchItems.value.length) result.push({key: "match", label: t("topology-graph.loop.matching"), items: matchItems.value})
        if (failedItems.value.length) {
            result.push({key: "failed", label: t("topology-graph.loop.failed-group", {count: failedTotal.value}), items: failedItems.value})
        }
        if (otherItems.value.length) result.push({key: "others", label: t("topology-graph.loop.other-group"), items: otherItems.value})
        return result
    })

    function reset() {
        failedItems.value = []
        otherItems.value = []
        matchItems.value = []
        failedTotal.value = 0
        total.value = 0
    }

    async function load() {
        const parentId = props.hostExecutionId
        if (!parentId) return
        const search = ++latestSearch
        loading.value = true
        failure.value = undefined
        reset()

        try {
            const base = {parentId, taskId: props.lane.taskId}
            const hasText = Boolean(query.value.trim())

            // TODO: kestra-io/kestra#19605 search by item value once the executions API supports it
            if (hasText && numberQuery.value === undefined) return

            if (numberQuery.value !== undefined) {
                const response = await searchLoopIterations({...base, page: Math.ceil(numberQuery.value / ITERATIONS_PAGE_SIZE)})
                if (search !== latestSearch) return
                matchItems.value = response.results.filter((iteration) => iteration.number === numberQuery.value)
                return
            }

            if (failedCount.value > 0 && page.value === 1) {
                const failed = await searchLoopIterations({...base, state: "FAILED"})
                if (search !== latestSearch) return
                failedItems.value = failed.results
                failedTotal.value = failed.total
            }
            const others = await searchLoopIterations({...base, excludeState: failedCount.value > 0 ? "FAILED" : undefined, page: page.value})
            if (search !== latestSearch) return
            otherItems.value = others.results
            total.value = others.total
        } catch (error) {
            if (search !== latestSearch) return
            reset()
            failure.value = error instanceof LoopIterationError ? error.failure : failureOf(error)
        } finally {
            if (search === latestSearch) loading.value = false
        }
    }

    async function loadMoreFailed() {
        const parentId = props.hostExecutionId
        if (!parentId) return
        const search = ++latestSearch
        try {
            const next = await searchLoopIterations({
                parentId,
                taskId: props.lane.taskId,
                state: "FAILED",
                page: Math.floor(failedItems.value.length / ITERATIONS_PAGE_SIZE) + 1,
            })
            if (search !== latestSearch) return
            failedItems.value = [...failedItems.value, ...next.results]
        } catch (error) {
            if (search !== latestSearch) return
            failure.value = error instanceof LoopIterationError ? error.failure : failureOf(error)
        }
    }

    const debouncedReload = debounce(() => {
        page.value = 1
        load()
    }, 250)

    watch(query, () => debouncedReload())
    watch(open, (isOpen) => {
        if (isOpen) load()
    })

    function goToPage(next: number) {
        page.value = next
        load()
    }

    function select(number: number) {
        open.value = false
        emit("select", number)
    }
</script>

<style scoped lang="scss">
    .loop-picker {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        min-width: 0;
    }

    .loop-picker-trigger {
        max-width: 12rem;
        white-space: nowrap;
    }

    .loop-picker-caret {
        font-size: var(--ks-font-size-sm);
    }

    .loop-picker-panel {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .loop-picker-group {
        display: block;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        color: var(--ks-text-secondary);
        text-transform: uppercase;
    }

    .loop-picker-option {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        width: 100%;
        min-width: 0;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border: 0;
        background: transparent;
        color: var(--ks-text-primary);
        font: inherit;
        font-size: var(--ks-font-size-xs);
        font-weight: 400;
        text-align: left;
        cursor: pointer;
    }

    .loop-picker-option:hover,
    .loop-picker-option-active {
        background: var(--ks-bg-hover-elevated);
    }

    .loop-picker-check {
        flex: 0 0 auto;
        visibility: hidden;
        color: var(--ks-text-link);
    }

    .loop-picker-option-active .loop-picker-check {
        visibility: visible;
    }

    .loop-picker-number {
        flex-shrink: 0;
        color: var(--ks-text-muted);
    }

    .loop-picker-value {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        text-align: left;
        white-space: nowrap;
    }

    .loop-picker-empty {
        display: block;
        padding: var(--ks-spacing-2);
    }

    .loop-picker-pages {
        display: flex;
        align-items: center;
        justify-content: space-between;
        font-size: var(--ks-font-size-xs);
    }

    .loop-stepper {
        display: inline-flex;
        align-items: center;
        font-size: var(--ks-font-size-2xs);
        white-space: nowrap;
    }
</style>
