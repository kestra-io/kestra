<template>
    <div v-if="compact" data-test="flow-change-review-compact">
        <div class="review-summary">
            <KsTag
                v-for="kind in kinds.filter(({value}) => value)"
                :key="kind.name"
                size="small"
                :type="kind.tone"
            >
                {{ kind.label }}
                <span class="review-summary-count">{{ kind.value }}</span>
            </KsTag>
            <KsText v-if="diff.counts.unchanged" size="small" type="secondary">{{ unchangedLabel }}</KsText>
        </div>
        <FlowChangeGraph class="review-compact-graph" v-bind="graphProps" @select-node="selectNode" />
    </div>
    <KsChangeReview
        v-else
        v-model:tab="tab"
        v-model:filter="filter"
        :segments="segments"
        :allLabel="$t('flow_change_review.all_changes')"
        :allCount="diff.tasks.length - diff.counts.unchanged"
        :filterLabel="$t('flow_change_review.filter_label')"
        :tabs="tabs"
        hideBar
        data-test="flow-change-review"
    >
        <template #graph>
            <div class="review-frame">
                <div class="review-body">
                    <FlowChangeGraph v-bind="graphProps" @select-node="selectNode" />
                    <aside class="review-details">
                        <FlowChangeDetail
                            :diff="diff"
                            :selectedId="selectedId"
                            :filter="filter"
                            :rejectable="rejectable"
                            :rejected="rejected"
                            @select="selectedId = $event"
                            @reject="reject"
                            @undo="rejected = rejected.filter((id) => id !== $event)"
                        />
                    </aside>
                </div>
            </div>
        </template>
        <template #yaml>
            <KsEditor
                class="review-yaml"
                v-bind="editorBindings"
                :options="{diffSideBySide: true}"
                :modelValue="effectiveAfter ?? ''"
                :original="before ?? ''"
                readOnly
                lang="yaml"
            />
        </template>
    </KsChangeReview>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {
        KS_CHANGE_REVIEW_ALL,
        KsEditor,
        stringUtils,
        type KsChangeReviewSegment,
    } from "@kestra-io/design-system"
    import {diffFlowSources, mergeFlowGraphs, revertChanges, type FlowGraph} from "@kestra-io/topology"
    import FlowChangeDetail from "./FlowChangeDetail.vue"
    import FlowChangeGraph from "./FlowChangeGraph.vue"
    import {useEditorBindings} from "../../../composables/useEditorBindings"
    import {useFlowStore} from "../../../stores/flow"

    const props = defineProps<{
        before?: string;
        after?: string;
        /** Lets the reader reject single changes; the proposal without them comes out as `effective`. */
        rejectable?: boolean;
        /** Graph only, with a one-line summary, for narrow places such as the Copilot draft card. */
        compact?: boolean;
    }>()

    const emit = defineEmits<{"update:effective": [source: string | undefined]}>()

    const KINDS = [
        {name: "added", glyph: "+", tone: "success"},
        {name: "removed", glyph: "−", tone: "danger"},
        {name: "changed", glyph: "~", tone: "warning"},
        {name: "renamed", tone: "info"},
        {name: "moved", tone: "info"},
    ] as const

    const {t} = useI18n()
    const flowStore = useFlowStore()
    const editorBindings = useEditorBindings()

    const tab = ref("graph")
    const filter = ref(KS_CHANGE_REVIEW_ALL)
    const selectedId = ref<string>()
    const rejected = ref<string[]>([])
    const tabs = computed(() => [
        {name: "graph", label: t("flow_change_review.graph")},
        {name: "yaml", label: t("flow_change_review.yaml")},
    ])

    const effectiveAfter = computed(() => (props.after && rejected.value.length
        ? revertChanges(props.before, props.after, rejected.value)
        : props.after))
    watch(effectiveAfter, (source) => emit("update:effective", source), {immediate: true})

    const diff = computed(() => diffFlowSources(props.before, effectiveAfter.value))
    const kinds = computed(() => KINDS.map((kind) => ({
        ...kind,
        value: diff.value.counts[kind.name],
        label: t(`flow_change_review.status.${kind.name}`),
    })))
    const unchangedLabel = computed(
        () => t("flow_change_review.unchanged", {count: diff.value.counts.unchanged}),
    )
    const segments = computed<KsChangeReviewSegment[]>(() => [
        ...kinds.value,
        {value: diff.value.counts.unchanged, label: unchangedLabel.value, tone: "neutral"},
    ])

    const graphs = ref<{before?: FlowGraph; after?: FlowGraph}>({})
    const loading = ref(false)
    const graphError = ref(false)
    let latest = 0

    async function loadGraphs() {
        const request = ++latest
        const load = (flow?: string) => (flow ? flowStore.getGraphFromSourceResponse({flow}) : undefined)
        loading.value = true
        graphError.value = false
        try {
            const [before, after] = await Promise.all([load(props.before), load(effectiveAfter.value)])
            if (request === latest) graphs.value = {before, after}
        } catch {
            if (request === latest) graphError.value = true
        } finally {
            if (request === latest) loading.value = false
        }
    }

    watch(() => [props.before, effectiveAfter.value], loadGraphs, {immediate: true})

    const merged = computed(() => (graphs.value.before || graphs.value.after
        ? mergeFlowGraphs(graphs.value.before, graphs.value.after, diff.value)
        : undefined))

    const graphProps = computed(() => ({
        loading: loading.value,
        error: graphError.value,
        source: effectiveAfter.value ?? props.before ?? "",
        graph: merged.value?.graph,
        diffStatuses: merged.value?.statusByUid ?? new Map(),
        selectedNodeUid: [...merged.value?.statusByUid.keys() ?? []]
            .find((uid) => stringUtils.afterLastDot(uid) === selectedId.value),
    }))

    function selectNode(uid: string) {
        selectedId.value = stringUtils.afterLastDot(uid)
    }

    function reject(id: string) {
        rejected.value = [...rejected.value, id]
        selectedId.value = undefined
    }
</script>

<style scoped>
    .review-frame {
        container-type: inline-size;
    }

    .review-summary {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-2);
    }

    .review-summary-count {
        font-weight: var(--ks-font-weight-semibold);
    }

    .review-compact-graph {
        height: var(--flow-change-review-height, 24rem);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        overflow: hidden;
    }

    .review-body {
        display: grid;
        grid-template-columns: minmax(0, 1fr) 20rem;
        height: var(--flow-change-review-height, min(60vh, 40rem));
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        overflow: hidden;
    }

    .review-details {
        min-height: 0;
        overflow: auto;
        border-left: 1px solid var(--ks-border-default);
        background: var(--ks-bg-surface);
    }

    @container (max-width: 48rem) {
        .review-body {
            grid-template-columns: minmax(0, 1fr);
            grid-template-rows: minmax(0, 1fr) 14rem;
        }

        .review-details {
            border-left: 0;
            border-top: 1px solid var(--ks-border-default);
        }
    }

    .ks-editor.review-yaml {
        height: var(--flow-change-review-height, min(60vh, 40rem));
    }
</style>
