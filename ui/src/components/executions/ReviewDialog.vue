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
            <i18n-t keypath="approval.title" tag="span" scope="global">
                <template #task><code>{{ taskRun.taskId }}</code></template>
                <template #id><code>{{ execution.id }}</code></template>
            </i18n-t>
        </template>

        <KsText v-if="title" tag="p" data-test="review-title">{{ title }}</KsText>
        <KsForm :model="inputs" labelPosition="top" ref="form" @submit.prevent="false">
            <InputsForm
                v-if="inputsList.length > 0"
                :initialInputs="inputsList"
                v-model="inputs"
                @validation="validateInputs"
                @ready="baseline = snapshot()"
            />
            <KsFormItem :label="$t('approval.comment')">
                <KsInput v-model="comment" type="textarea" :rows="3" data-test="review-comment" />
                <KsText size="small" type="info" data-test="review-comment-hint">
                    {{ $t(commentHintKey) }}
                </KsText>
            </KsFormItem>
        </KsForm>

        <template #footer>
            <KsButton :loading="submitting" @click="submit('DENY')" data-test="review-deny">
                {{ decisionLabel(task.decisions?.deny) ?? $t("approval.deny") }}
            </KsButton>
            <KsButton type="primary" :loading="submitting" @click="submit('APPROVE')" data-test="review-approve">
                {{ decisionLabel(task.decisions?.approve) ?? $t("approval.approve") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import InputsForm from "../inputs/InputsForm.vue"
    import {inputsToFormData} from "../../utils/submitTask"
    import {decisionLabel, isCommentRequired, type ApprovalTask, type ApprovalTaskRun} from "../../utils/approval"
    import {useExecutionsStore, type Execution, type ReviewDecision, type ValidationEventPayload} from "../../stores/executions"
    import {loadTaskRunOutputs} from "../../composables/useTaskRunOutputs"
    import {handledIf} from "../../utils/kestraHttp"
    import {useToast} from "../../utils/toast"

    const props = defineProps<{
        execution: Execution;
        taskRun: ApprovalTaskRun;
        task: ApprovalTask;
    }>()

    const visible = defineModel<boolean>({default: false})

    const emit = defineEmits<{reviewed: [decision: ReviewDecision]}>()

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const toast = useToast()

    const form = ref<{validate: (cb: (valid: boolean) => void) => void} | null>(null)
    const inputs = ref<Record<string, unknown>>({})
    const comment = ref("")
    const submitting = ref(false)

    const inputsList = computed(() => props.task.inputs ?? [])

    const COMMENT_HINT_KEYS: Record<string, string> = {
        ALWAYS: "approval.comment hint.always",
        ON_APPROVE: "approval.comment hint.on approve",
        ON_DENY: "approval.comment hint.on deny",
    }

    // The task properties can be expressions, so the rendered values are read from the paused task run's outputs.
    const renderedCommentRequired = ref<string>()
    const title = ref<string>()
    const commentRequired = computed(() => renderedCommentRequired.value ?? props.task.commentRequired)
    const commentHintKey = computed(() => COMMENT_HINT_KEYS[commentRequired.value ?? ""] ?? "approval.comment hint.optional")

    const baseline = ref("")
    const snapshot = () => JSON.stringify(inputs.value)
    const isDirty = computed(() => snapshot() !== baseline.value || comment.value !== "")

    watch(visible, (open) => {
        if (open) {
            inputs.value = {}
            comment.value = ""
            baseline.value = snapshot()
            loadOutputs()
        }
    }, {immediate: true})

    async function loadOutputs() {
        renderedCommentRequired.value = undefined
        title.value = undefined
        const outputs = await loadTaskRunOutputs(props.execution.id, props.taskRun.id).catch((e: unknown): Record<string, unknown> => {
            if (!handledIf(e, [403])) throw e
            return {}
        })
        if (typeof outputs.commentRequired === "string") {
            renderedCommentRequired.value = outputs.commentRequired
        }
        if (typeof outputs.title === "string") {
            title.value = outputs.title
        }
    }

    function validateInputs({formData, callback}: ValidationEventPayload) {
        executionsStore.validateReview({id: props.execution.id, taskRunId: props.taskRun.id, decision: "APPROVE", formData}).then(callback)
    }

    function submit(decision: ReviewDecision) {
        const trimmedComment = comment.value.trim()
        if (isCommentRequired(commentRequired.value, decision) && trimmedComment === "") {
            toast.error(t("approval.comment required"))
            return
        }

        form.value?.validate(async (valid: boolean) => {
            if (!valid) {
                return
            }

            const formData = inputsToFormData(inputsList.value, inputs.value) ?? new FormData()
            if (trimmedComment !== "") {
                formData.append("kestra_review_comment", trimmedComment)
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
                emit("reviewed", decision)
            } finally {
                submitting.value = false
            }
        })
    }
</script>
