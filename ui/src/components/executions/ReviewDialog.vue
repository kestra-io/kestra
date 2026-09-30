<template>
    <KsDialog
        v-model="visible"
        destroyOnClose
        appendToBody
        scrollable
        :dirty="isDirty"
        data-test="review-dialog"
    >
        <template #header>
            <span v-html="$t('approval.title', {id: escapeHtml(execution.id)})" />
        </template>

        <template v-if="decided">
            <dl class="review-summary" data-test="review-summary">
                <dt>{{ $t("approval.decision") }}</dt>
                <dd>{{ $t(`approval.decisions.${String(outputs.decision).toLowerCase()}`) }}</dd>
                <template v-if="outputs.by">
                    <dt>{{ $t("approval.by") }}</dt>
                    <dd>{{ outputs.by }}</dd>
                </template>
                <template v-if="outputs.on">
                    <dt>{{ $t("approval.on") }}</dt>
                    <dd>{{ dateUtils.dateFilter(String(outputs.on)) }}</dd>
                </template>
                <template v-if="outputs.comment">
                    <dt>{{ $t("approval.comment") }}</dt>
                    <dd>{{ outputs.comment }}</dd>
                </template>
            </dl>
            <KsJsonTree v-if="hasReviewInputs" :value="outputs.inputs" />
        </template>

        <KsForm v-else :model="inputs" labelPosition="top" ref="form" @submit.prevent="false">
            <InputsForm
                v-if="inputsList.length > 0"
                :initialInputs="inputsList"
                :execution="execution"
                v-model="inputs"
            />
            <KsFormItem :label="$t('approval.comment')">
                <KsInput v-model="comment" type="textarea" :rows="3" data-test="review-comment" />
                <KsText size="small" type="info" data-test="review-comment-hint">
                    {{ $t(commentHintKey) }}
                </KsText>
            </KsFormItem>
        </KsForm>

        <template #footer>
            <KsButton v-if="decided" @click="visible = false" data-test="review-close">
                {{ $t("close") }}
            </KsButton>
            <template v-else>
                <KsButton :loading="submitting" @click="submit('DENY')" data-test="review-deny">
                    {{ decisionLabel(task.decisions?.deny) ?? $t("approval.deny") }}
                </KsButton>
                <KsButton type="primary" :loading="submitting" @click="submit('APPROVE')" data-test="review-approve">
                    {{ decisionLabel(task.decisions?.approve) ?? $t("approval.approve") }}
                </KsButton>
            </template>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {dateUtils, escapeHtml} from "@kestra-io/design-system"
    import InputsForm from "../inputs/InputsForm.vue"
    import {inputsToFormData} from "../../utils/submitTask"
    import {decisionLabel, isCommentRequired, type ApprovalTask, type ApprovalTaskRun} from "../../utils/approval"
    import {useExecutionsStore, type Execution, type ReviewDecision} from "../../stores/executions"
    import {useToast} from "../../utils/toast"

    const props = defineProps<{
        execution: Execution;
        taskRun: ApprovalTaskRun;
        task: ApprovalTask;
    }>()

    const visible = defineModel<boolean>({default: false})

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const toast = useToast()

    const form = ref<{validate: (cb: (valid: boolean) => void) => void} | null>(null)
    const inputs = ref<Record<string, unknown>>({})
    const comment = ref("")
    const submitting = ref(false)

    const outputs = computed(() => props.taskRun.outputs ?? {})
    const decided = computed(() => outputs.value.decision !== undefined && outputs.value.decision !== null)
    const inputsList = computed(() => props.task.inputs ?? [])
    const hasReviewInputs = computed(() => Object.keys((outputs.value.inputs as Record<string, unknown> | undefined) ?? {}).length > 0)

    const commentHintKey = computed(() => {
        switch (props.task.commentRequired) {
        case "ALWAYS":
            return "approval.comment hint.always"
        case "ON_APPROVE":
            return "approval.comment hint.on approve"
        case "ON_DENY":
            return "approval.comment hint.on deny"
        default:
            return "approval.comment hint.optional"
        }
    })

    const baseline = ref("")
    const snapshot = () => JSON.stringify({inputs: inputs.value, comment: comment.value})
    const isDirty = computed(() => !decided.value && snapshot() !== baseline.value)

    watch(visible, (open) => {
        if (open) {
            inputs.value = {}
            comment.value = ""
            baseline.value = snapshot()
        }
    }, {immediate: true})

    function submit(decision: ReviewDecision) {
        if (isCommentRequired(props.task.commentRequired, decision) && comment.value.trim() === "") {
            toast.error(t("approval.comment required"))
            return
        }

        form.value?.validate(async (valid: boolean) => {
            if (!valid) {
                return
            }

            const formData = inputsToFormData(inputsList.value, inputs.value) ?? new FormData()
            if (comment.value !== "") {
                formData.append("comment", comment.value)
            }

            submitting.value = true
            try {
                const options = {id: props.execution.id, taskRunId: props.taskRun.id, decision, formData}
                const validation = await executionsStore.validateReview(options)
                const errors = validation.inputs?.flatMap(input => input.errors?.map(error => error.message) ?? []) ?? []
                if (errors.length > 0) {
                    toast.error(t("approval.inputs invalid", {errors: errors.join(" ")}))
                    return
                }
                await executionsStore.review(options)
                visible.value = false
                toast.success(t("approval.review done"))
            } finally {
                submitting.value = false
            }
        })
    }
</script>

<style scoped>
    .review-summary {
        display: grid;
        grid-template-columns: max-content 1fr;
        gap: var(--ks-spacing-2) var(--ks-spacing-4);
    }

    .review-summary dt {
        color: var(--ks-text-secondary);
    }

    .review-summary dd {
        margin: 0;
        color: var(--ks-text-primary);
    }
</style>
