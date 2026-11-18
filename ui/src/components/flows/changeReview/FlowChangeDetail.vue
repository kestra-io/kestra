<template>
    <div v-if="task" class="flow-change-detail" data-test="flow-change-detail">
        <div class="detail-head">
            <KsIconButton :aria-label="$t('flow_change_review.back')" @click="emit('select', undefined)">
                <ArrowLeft />
            </KsIconButton>
            <div class="detail-title">
                <KsText tag="span" class="detail-id">{{ task.id }}</KsText>
                <KsText tag="span" size="small" type="secondary" class="detail-type" :title="task.type">
                    {{ shortType(task.type) }}
                </KsText>
            </div>
            <KsTag size="small" :type="TONES[task.status]">
                {{ $t(`flow_change_review.status.${task.status}`) }}
            </KsTag>
        </div>

        <KsText v-if="description" tag="p" class="detail-text">{{ description }}</KsText>

        <template v-if="task.changes.length">
            <div
                v-for="change in task.changes"
                :key="change.key"
                class="detail-change"
                data-test="flow-change-property"
            >
                <KsText tag="span" class="detail-key">{{ change.key }}</KsText>
                <div class="detail-value detail-before">
                    <KsText tag="span" size="small" class="detail-value-label">
                        {{ $t("flow_change_review.before") }}
                    </KsText>
                    <KsMarkdown :content="code(change.before)" />
                </div>
                <div class="detail-value detail-after">
                    <KsText tag="span" size="small" class="detail-value-label">
                        {{ $t("flow_change_review.after") }}
                    </KsText>
                    <KsMarkdown :content="code(change.after)" />
                </div>
            </div>
        </template>
        <KsMarkdown
            v-else-if="task.status !== 'renamed' && task.status !== 'moved'"
            :content="code(task.properties)"
        />

        <KsButton
            v-if="rejectable && task.status !== 'unchanged'"
            class="detail-reject"
            :icon="Close"
            data-test="flow-change-reject"
            @click="emit('reject', task.id)"
        >
            {{ $t("flow_change_review.reject") }}
        </KsButton>
    </div>

    <div v-else class="flow-change-list">
        <KsText tag="div" size="small" type="secondary" class="list-head">
            {{ $t("flow_change_review.changes", {count: listed.length}) }}
        </KsText>
        <KsSelectableListItem
            v-for="item in listed"
            :key="item.id"
            :title="item.id"
            :subtitle="summary(item)"
            data-test="flow-change-row"
            @click="emit('select', item.id)"
        >
            <template #leading>
                <span class="row-status">
                    <KsTag size="small" :type="TONES[item.status]">
                        {{ $t(`flow_change_review.status.${item.status}`) }}
                    </KsTag>
                </span>
            </template>
        </KsSelectableListItem>
        <KsNoData
            v-if="!listed.length"
            :icon="CheckCircleOutline"
            :title="$t('flow_change_review.no_changes')"
        />
        <template v-if="rejected.length">
            <KsText tag="div" size="small" type="secondary" class="list-head list-head-rejected">
                {{ $t("flow_change_review.rejected", {count: rejected.length}) }}
            </KsText>
            <div v-for="id in rejected" :key="id" class="rejected-row" data-test="flow-change-rejected">
                <KsText tag="span" class="rejected-id">{{ id }}</KsText>
                <KsButton text size="small" :icon="Undo" @click="emit('undo', id)">
                    {{ $t("flow_change_review.undo_reject") }}
                </KsButton>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import ArrowLeft from "vue-material-design-icons/ArrowLeft.vue"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import Undo from "vue-material-design-icons/Undo.vue"
    import {KS_CHANGE_REVIEW_ALL, type KsTagType} from "@kestra-io/design-system"
    import type {FlowDiff, TaskDiff, TaskDiffStatus} from "@kestra-io/topology"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"

    const props = defineProps<{
        diff: FlowDiff;
        selectedId?: string;
        filter: string;
        rejectable?: boolean;
        rejected: string[];
    }>()

    const emit = defineEmits<{select: [id: string | undefined]; reject: [id: string]; undo: [id: string]}>()

    const {t} = useI18n()

    const TONES: Record<TaskDiffStatus, KsTagType> = {
        added: "success",
        removed: "danger",
        changed: "warning",
        renamed: "info",
        moved: "info",
        unchanged: "",
    }

    const task = computed(() => props.diff.tasks.find((item) => item.id === props.selectedId))

    const listed = computed(() => props.diff.tasks.filter((item) => item.status !== "unchanged"
        && (props.filter === KS_CHANGE_REVIEW_ALL || props.filter === item.status)))

    function shortType(type: string) {
        return type.replace(/^io\.kestra\.plugin\./, "")
    }

    function place(section: string) {
        return section.replaceAll(".", " › ")
    }

    function summary(item: TaskDiff) {
        if (item.status === "renamed") return t("flow_change_review.renamed_from", {id: item.renamedFrom})
        if (item.status === "moved") {
            return t("flow_change_review.moved_from", {
                from: place(item.previousSection ?? ""),
                to: place(item.section),
            })
        }
        if (item.changes.length) return item.changes.map((change) => change.key).join(", ")
        return shortType(item.type)
    }

    const description = computed(() => {
        const current = task.value
        if (!current) return undefined
        switch (current.status) {
        case "added": return t("flow_change_review.added_description", {section: place(current.section)})
        case "removed": return t("flow_change_review.removed_description", {section: place(current.section)})
        case "renamed":
            return t("flow_change_review.renamed_description", {from: current.renamedFrom, to: current.id})
        case "moved":
            return t("flow_change_review.moved_description", {
                from: place(current.previousSection ?? ""),
                to: place(current.section),
            })
        case "unchanged": return t("flow_change_review.unchanged_description")
        default: return undefined
        }
    })

    function code(value: unknown) {
        if (value === undefined) {
            return t("flow_change_review.not_set")
        }
        const yaml = YAML_UTILS.stringify(value).trimEnd()
        return `\`\`\`yaml\n${yaml}\n\`\`\``
    }
</script>

<style scoped>
    .flow-change-detail,
    .flow-change-list {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3);
        text-align: start;
    }

    .flow-change-list {
        gap: var(--ks-spacing-1);
    }

    .detail-head {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
    }

    .detail-title {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        flex: 1;
        min-width: 0;
    }

    .detail-id {
        font-weight: var(--ks-font-weight-semibold);
        overflow-wrap: anywhere;
    }

    .detail-type,
    .detail-key {
        font-family: var(--ks-font-family-mono);
        overflow-wrap: anywhere;
    }

    /* KsText centres itself in a flex column. */
    .detail-id,
    .detail-type,
    .detail-key,
    .detail-value-label,
    .list-head {
        align-self: flex-start;
    }

    .detail-text {
        align-self: stretch;
        margin: 0;
        color: var(--ks-text-secondary);
    }

    .detail-change {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: var(--ks-spacing-1);
    }

    .detail-value {
        align-self: stretch;
        padding: var(--ks-spacing-2);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-base);
    }

    .detail-value-label {
        display: block;
        font-weight: var(--ks-font-weight-semibold);
    }

    .detail-before {
        background: var(--ks-bg-error);
        border-color: var(--ks-border-error);
        color: var(--ks-text-error);
    }

    .detail-after {
        background: var(--ks-bg-success);
        border-color: var(--ks-border-success);
        color: var(--ks-text-success);
    }

    .detail-reject {
        align-self: flex-start;
    }

    .list-head-rejected {
        margin-top: var(--ks-spacing-3);
    }

    .rejected-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: 0 var(--ks-spacing-2);
    }

    .rejected-id {
        color: var(--ks-text-secondary);
        text-decoration: line-through;
        overflow-wrap: anywhere;
    }

    .list-head {
        padding: 0 var(--ks-spacing-2) var(--ks-spacing-1);
    }

    .row-status {
        display: inline-flex;
        min-width: var(--ks-spacing-16);
    }
</style>
