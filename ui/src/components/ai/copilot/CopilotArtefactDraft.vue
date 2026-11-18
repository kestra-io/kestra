<template>
    <div class="copilot-draft" data-test="copilot-draft">
        <div class="copilot-draft-header">
            <KsText size="small" class="copilot-draft-title">
                <KsIcon><FileDocumentOutline /></KsIcon>
                {{ $t(`ai.copilot.draft.title.${draft.kind.toLowerCase()}`) }}
            </KsText>
            <KsCodeStatus
                data-test="copilot-draft-status"
                :status="draft.valid ? 'valid' : 'error'"
                :label="draft.valid ? $t('ai.copilot.draft.valid') : $t('ai.copilot.draft.invalid')"
            />
        </div>

        <KsAlert
            v-if="!draft.valid && draft.constraints"
            type="warning"
            :closable="false"
            class="copilot-draft-constraints"
            data-test="copilot-draft-constraints"
        >
            {{ draft.constraints }}
        </KsAlert>

        <div v-if="draft.kind === 'FLOW' && draft.valid" class="copilot-draft-view">
            <KsTabs v-model="view" type="segmented" data-test="copilot-draft-view">
                <KsTabPane name="yaml" :label="$t('flow_change_review.yaml')" />
                <KsTabPane name="graph" :label="$t('flow_change_review.graph')" />
            </KsTabs>
            <KsButton
                v-if="view === 'graph' && !dismissed && !applied"
                text
                size="small"
                :icon="ArrowExpand"
                :disabled="applying"
                data-test="copilot-draft-review"
                @click="onApply"
            >
                {{ $t("flow_change_review.review_changes") }}
            </KsButton>
        </div>
        <div
            v-if="view === 'graph'"
            v-ks-loading="base === undefined"
            class="copilot-draft-graph"
            data-test="copilot-draft-graph"
        >
            <FlowChangeReview
                v-if="base !== undefined"
                compact
                :before="base || undefined"
                :after="draft.yaml"
            />
        </div>
        <KsMarkdown
            v-else
            class="copilot-draft-yaml"
            data-test="copilot-draft-yaml"
            :content="yamlBlock"
        />

        <div
            v-if="dismissed || applied"
            class="copilot-draft-footer"
            :data-test="dismissed ? 'copilot-draft-dismissed' : 'copilot-draft-applied'"
        >
            <KsText size="small" type="secondary" class="copilot-draft-status">
                {{ $t(dismissed ? "ai.copilot.draft.dismissed" : "ai.copilot.draft.applied") }}
            </KsText>
        </div>
        <div v-else class="copilot-draft-footer">
            <KsButton
                text
                size="small"
                class="copilot-draft-dismiss"
                data-test="copilot-draft-dismiss"
                @click="emit('dismiss', draft.draftId)"
            >
                {{ $t("ai.copilot.draft.dismiss") }}
            </KsButton>
            <template v-if="showActions">
                <KsButton size="small" data-test="copilot-draft-open" @click="openInEditor(draft)">
                    {{ $t("ai.copilot.draft.openInEditor") }}
                </KsButton>
                <KsButton
                    v-if="draft.kind !== 'APP'"
                    size="small"
                    type="primary"
                    :disabled="!draft.valid || applying"
                    data-test="copilot-draft-apply"
                    @click="onApply"
                >
                    {{ $t("ai.copilot.draft.apply") }}
                </KsButton>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, defineAsyncComponent, ref, watch} from "vue"
    import ArrowExpand from "vue-material-design-icons/ArrowExpand.vue"
    import FileDocumentOutline from "vue-material-design-icons/FileDocumentOutline.vue"
    import {useApplyDraft} from "./useApplyDraft"
    import type {ArtefactDraftEvent} from "./types"

    const props = defineProps<{
        draft: ArtefactDraftEvent
        dismissed?: boolean
        applied?: boolean
    }>()

    const emit = defineEmits<{
        (e: "dismiss", draftId: string): void
        (e: "applied", draftId: string): void
    }>()

    const FlowChangeReview = defineAsyncComponent(
        () => import("../../flows/changeReview/FlowChangeReview.vue"),
    )

    const {
        applying,
        appSupported,
        dashboardSupported,
        testSuiteSupported,
        openInEditor,
        apply,
        draftBaseSource,
    } = useApplyDraft()

    const view = ref<"yaml" | "graph">("yaml")
    const base = ref<string>()

    const yamlBlock = computed(() => `\`\`\`yaml\n${props.draft.yaml}\n\`\`\``)
    const showActions = computed(() => {
        const {kind} = props.draft
        if (kind === "DASHBOARD") {
            return dashboardSupported.value
        }
        if (kind === "APP") {
            return appSupported
        }
        if (kind === "TEST_SUITE") {
            return testSuiteSupported
        }
        return kind === "FLOW"
    })

    watch(view, async (value) => {
        if (value !== "graph" || base.value !== undefined) {
            return
        }
        base.value = await draftBaseSource(props.draft).catch(() => "")
    })

    async function onApply(): Promise<void> {
        if (await apply(props.draft)) emit("applied", props.draft.draftId)
    }
</script>

<style scoped>
    .copilot-draft {
        width: 100%;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg);
        overflow: hidden;
        background: var(--ks-bg-base);
    }

    .copilot-draft-header,
    .copilot-draft-footer {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-elevated);
    }

    .copilot-draft-header {
        justify-content: space-between;
    }

    .copilot-draft-footer {
        justify-content: flex-end;
    }

    .copilot-draft-title {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        font-size: var(--ks-font-size-sm);
    }

    .copilot-draft-constraints {
        margin: var(--ks-spacing-2) var(--ks-spacing-3) 0;
    }

    .copilot-draft-view {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--ks-spacing-2) var(--ks-spacing-3) 0;
    }

    .copilot-draft-graph {
        min-height: 10rem;
        padding: var(--ks-spacing-3);
    }

    .copilot-draft-yaml {
        margin: 0;
        max-height: 18rem;
        overflow: auto;
        padding: var(--ks-spacing-3);
        font-size: var(--ks-font-size-sm);
    }

    .copilot-draft-dismiss {
        color: var(--ks-text-secondary);
    }

    .copilot-draft-status {
        font-style: italic;
    }
</style>
