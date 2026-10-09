<template>
    <NavBarAction
        v-if="trigger && asItem && hasPermission && (isReplay || enabled)"
        :icon="icon"
        :disabled="!enabled"
        @click="isOpen = !isOpen"
    >
        {{ $t(replayOrRestart) }}
    </NavBarAction>
    <KsTooltip
        v-else-if="trigger && hasPermission && (isReplay || enabled)"
        :placement="tooltipPosition"
        :enterable="false"
        :content="tooltip"
        popperClass="ks-restart-tooltip--no-pointer"
        rawContent
    >
        <component
            v-if="component !== 'KsDropdownItem'"
            v-bind="$attrs"
            :is="component"
            :icon="icon"
            :disabled="!enabled"
            :class="componentClass"
            @click="isOpen = !isOpen"
        >
            {{ $t(replayOrRestart) }}
        </component>
        <span v-else>
            <component
                v-bind="$attrs"
                :is="component"
                :icon="icon"
                :disabled="!enabled"
                :class="componentClass"
                @click="isOpen = !isOpen"
            >
                {{ $t(replayOrRestart) }}
            </component>
        </span>
    </KsTooltip>

    <KsDialog
        v-if="enabled && isOpen && !isReplay"
        v-model="isOpen"
        destroyOnClose
        :appendToBody="true"
    >
        <template #header>
            <div class="modal-header m-0">
                <h3 class="modal-title">
                    {{ $t("restart execution title") }}
                </h3>
                <KsDivider />
            </div>
        </template>

        <div class="p-3 pt-0">
            <p class="mb-0" v-html="$t('restart confirm', {id: escapeHtml(execution.id)})" />
            <KsAlert
                v-if="parentLink"
                type="warning"
                :closable="false"
                showIcon
                class="mt-3"
                data-test="restart-subflow-warning"
            >
                {{ parentWarning[0] }}<router-link :to="parentLink" @click="isOpen = false">{{ $t("restart subflow warning link") }}</router-link>{{ parentWarning[1] }}
            </KsAlert>
        </div>

        <template #footer>
            <KsButton @click="isOpen = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" @click="handleRestartExecute">
                {{ $t("restart") }}
            </KsButton>
        </template>
    </KsDialog>

    <KsDialog
        v-if="enabled && isOpen && isReplay"
        v-model="isOpen"
        destroyOnClose
        :appendToBody="true"
        scrollable
    >
        <template #header>
            <div class="modal-header m-0">
                <h3 class="modal-title">
                    {{ $t("replay execution title") }}
                </h3>
                <KsDivider />
            </div>
        </template>

        <div class="p-3 pt-0">
            <p class="mb-0">
                {{ $t("replay execution description") }}
            </p>
            <KsId :value="execution.id" :shrink="false" />

            <h4 class="section-title">
                {{ $t("replay using") }}:
            </h4>

            <KsRadioGroup v-model="replayRevisionMode" class="radio-vertical">
                <KsRadio value="original" class="radio-item">
                    {{ $t("flow revision original") }}
                </KsRadio>
                <KsRadio value="latest" class="radio-item">
                    {{ $t("flow revision latest") }}
                </KsRadio>
                <KsRadio value="specific" class="radio-item">
                    {{ $t("flow revision specific") }}
                </KsRadio>
            </KsRadioGroup>

            <KsForm
                v-if="replayRevisionMode === 'specific' && revisionsOptions?.length"
                class="mt-2"
            >
                <KsFormItem>
                    <KsSelect v-model="revisionsSelected">
                        <KsOption
                            v-for="item in revisionsOptions"
                            :key="item.value"
                            :label="item.text"
                            :value="item.value"
                        />
                    </KsSelect>
                </KsFormItem>
            </KsForm>

            <KsAlert
                v-if="replayCheck.status === 'checking'"
                type="info"
                :closable="false"
                showIcon
                class="mt-3"
                data-test="replay-check-checking"
                :title="$t('replayCheck.checking', checkParams)"
            />
            <KsAlert
                v-else-if="replayCheck.status === 'error'"
                type="warning"
                :closable="false"
                showIcon
                class="mt-3"
                data-test="replay-check-error"
                :title="$t('replayCheck.error_title', checkParams)"
                :description="$t('replayCheck.error_description')"
            />
            <KsAlert
                v-else-if="replayCheck.status === 'valid'"
                type="success"
                :closable="false"
                showIcon
                class="mt-3"
                data-test="replay-check-valid"
                :title="$t('replayCheck.valid_title', checkParams)"
                :description="$t('replayCheck.valid_description')"
            />
            <template v-else-if="replayCheck.status === 'refused'">
                <KsAlert
                    v-if="replayWhole"
                    type="info"
                    :closable="false"
                    showIcon
                    class="mt-3"
                    data-test="replay-check-whole"
                    :title="$t('replayCheck.whole_title', checkParams)"
                    :description="$t('replayCheck.whole_description', checkParams)"
                />
                <KsAlert
                    v-else
                    type="error"
                    :closable="false"
                    showIcon
                    class="mt-3"
                    data-test="replay-check-refused"
                    :title="$t('replayCheck.refused_title', checkParams)"
                    :description="replayCheck.detail || $t('replayCheck.refused_description')"
                />
                <KsButton v-if="!replayWhole" class="mt-2" data-test="replay-whole-instead" @click="replayWhole = true">
                    {{ $t("replayCheck.whole_action") }}
                </KsButton>
            </template>

            <template v-if="hasInputs">
                <template v-if="!replayTaskRun">
                    <h4 class="section-title">
                        {{ $t("replay inputs") }}:
                    </h4>

                    <KsRadioGroup v-if="canReuseInputs" v-model="inputMode" class="radio-vertical">
                        <KsRadio value="reuse" class="radio-item">
                            {{ $t("reuse original inputs") }}
                        </KsRadio>
                        <KsRadio value="modify" class="radio-item">
                            {{ $t("modify inputs") }}
                        </KsRadio>
                    </KsRadioGroup>
                </template>
                <p v-if="!canReuseInputs" class="execution-description mt-2 mb-0">
                    {{ $t("replay inputs new required") }}
                </p>
            </template>
        </div>

        <template #footer>
            <KsButton @click="isOpen = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" :disabled="replayBlocked" data-test="replay-confirm" @click="handleReplayExecute">
                {{ replayTaskRun ? $t("replayCheck.confirm_from_task") : $t("replayCheck.confirm_whole") }}
            </KsButton>
        </template>
    </KsDialog>

    <KsDialog
        v-if="isReplayWithInputsOpen"
        v-model="isReplayWithInputsOpen"
        destroyOnClose
        :appendToBody="true"
        scrollable
    >
        <template #header>
            <span
                v-html="$t('replay the execution', {
                    executionId: escapeHtml(execution.id),
                    flowId: escapeHtml(execution.flowId)
                })"
            />
        </template>

        <ReplayWithInputs
            :execution="execution"
            :taskRun="replayTaskRun"
            :revision="revisionsSelected"
            @execution-trigger="closeReplayWithInputsModal"
        />
    </KsDialog>
</template>

<script setup lang="ts">
    import {ref, computed, watch, inject} from "vue"
    import {useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {useToast} from "../../../../../utils/toast"
    import {State, escapeHtml} from "@kestra-io/design-system"
    import {useFlowStore} from "../../../../../stores/flow"
    import {useAuthStore} from "override/stores/auth"
    import {useExecutionsStore} from "../../../../../stores/executions"
    import {asProblem} from "@kestra-io/kestra-sdk"
    import action from "../../../../../models/action"
    import resource from "../../../../../models/resource"
    import ReplayWithInputs from "../../../ReplayWithInputs.vue"
    import {EXECUTION_PARENT_ROUTE} from "../../../executionTabs"
    import RestartIcon from "vue-material-design-icons/Restart.vue"
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue"
    import {KsId} from "@kestra-io/design-system"
    import NavBarAction from "../../../../layout/NavBarAction.vue"
    import {asItemKey} from "../../../../layout/navBarActionsContext"
    import {splitTranslation} from "../../../../../utils/splitTranslation"

    defineOptions({inheritAttrs: false})

    const asItem = inject(asItemKey, false)

    const props = defineProps({
        component: {type: String, default: "KsButton"},
        isReplay: {type: Boolean, default: false},
        isButton: {type: Boolean, default: true},
        execution: {type: Object, required: true},
        taskRun: {type: Object, required: false, default: undefined},
        attemptIndex: {type: Number, required: false, default: undefined},
        tooltipPosition: {type: String, default: "bottom"},
        trigger: {type: Boolean, default: true},
    })

    const {t} = useI18n()
    const toast = useToast()
    const router = useRouter()
    const flowStore = useFlowStore()
    const authStore = useAuthStore()
    const executionsStore = useExecutionsStore()

    const SUBFLOW_TRIGGER_TYPE = "io.kestra.plugin.core.flow.Subflow"

    const isOpen = ref(false)
    const isReplayWithInputsOpen = ref(false)
    const revisionsSelected = ref<number | undefined>(undefined)

    const replayRevisionMode = ref<"original" | "latest" | "specific">("original")
    const inputMode = ref<"reuse" | "modify">("reuse")

    const icon = computed(() => !props.isReplay ? RestartIcon : PlayBoxMultiple)
    const componentClass = computed(() => !props.isReplay ? "restart me-1" : "")
    const replayOrRestart = computed(() => props.isReplay ? "replay" : "restart")

    const parentLink = computed(() => {
        const trigger = props.execution.trigger
        const variables = trigger?.variables
        if (trigger?.type !== SUBFLOW_TRIGGER_TYPE || !variables?.executionId) return undefined

        return {
            name: EXECUTION_PARENT_ROUTE,
            params: {
                namespace: variables.namespace,
                flowId: variables.flowId,
                id: variables.executionId,
            },
        }
    })
    const parentWarning = computed(() => splitTranslation(t, "restart subflow warning", "parent"))

    const currentFlow = ref<any | undefined>(undefined)
    const hasInputs = computed(() => (currentFlow.value?.inputs?.length ?? 0) > 0)
    const hasOriginalInputs = computed(() => {
        const inputs = props.execution.inputs
        return inputs != null && Object.keys(inputs).length > 0
    })
    const canReuseInputs = computed(() => hasInputs.value && hasOriginalInputs.value)

    const effectiveRevision = computed(() => {
        if (replayRevisionMode.value === "original") return props.execution.flowRevision
        if (replayRevisionMode.value === "latest") {
            const revisions = flowStore.revisions
            return revisions?.[revisions.length - 1]?.revision
        }
        return revisionsSelected.value
    })

    type ReplayCheck = {status: "idle" | "checking" | "valid" | "error"} | {status: "refused", detail?: string}

    const replayCheck = ref<ReplayCheck>({status: "idle"})
    const replayWhole = ref(false)
    let replayCheckSequence = 0

    const replayTaskRun = computed(() => props.isReplay && !replayWhole.value ? props.taskRun : undefined)
    const needsReplayCheck = computed(() =>
        props.isReplay
        && !!props.taskRun
        && effectiveRevision.value !== undefined
        && effectiveRevision.value !== props.execution.flowRevision,
    )
    const replayBlocked = computed(() =>
        replayCheck.value.status === "checking" || (replayCheck.value.status === "refused" && !replayWhole.value),
    )
    const checkParams = computed(() => ({revision: effectiveRevision.value, taskId: props.taskRun?.taskId}))

    const revisionsOptions = computed(() =>
        (flowStore.revisions || [])
            .map((revision) => ({
                value: revision.revision,
                text:
                    revision.revision +
                    (revision.revision === props.execution.flowRevision
                        ? ` (${t("current")})`
                        : ""),
            }))
            .reverse(),
    )

    // Split out of `enabled` so a missing permission hides the action entirely instead of
    // rendering it disabled: a permanently dead button in the nav bar would leave the user with
    // no reachable action at all in that slot.
    const hasPermission = computed(() => {
        if (!props.execution) return false

        return props.isReplay
            ? !!authStore.user?.isAllowed(resource.EXECUTION, action.REPLAY, props.execution.namespace)
            : !!authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace)
    })

    const enabled = computed(() => {
        if (!props.execution?.state) return false

        if (!hasPermission.value) return false

        if (
            props.isReplay &&
            props.taskRun?.attempts &&
            props.taskRun.attempts.length - 1 !== props.attemptIndex
        ) {
            return false
        }

        return props.isReplay
            ? State.isTerminated(props.execution.state.current)
            : props.execution.state.current === State.FAILED
    })

    const tooltip = computed(() =>
        props.isReplay
            ? props.taskRun?.id
                ? t("replay from task tooltip", {taskId: props.taskRun.taskId})
                : t("replay from beginning tooltip")
            : t("restart tooltip", {state: props.execution.state?.current}),
    )

    const openReplayWithInputsDialog = () => {
        isOpen.value = false
        loadFlowForReplay()
    }

    const closeReplayWithInputsModal = () => {
        isReplayWithInputsOpen.value = false
    }

    const loadFlowForReplay = async () => {
        const revision = replayRevisionMode.value !== "latest" ? revisionsSelected.value : undefined
        await executionsStore.loadFlowForExecution({
            flowId: props.execution.flowId,
            namespace: props.execution.namespace,
            revision,
            store: true,
        })
        isReplayWithInputsOpen.value = true
    }

    const loadRevision = async () => {
        revisionsSelected.value = props.execution.flowRevision
        currentFlow.value = undefined
        flowStore.loadRevisions({
            namespace: props.execution.namespace,
            id: props.execution.flowId,
        })
        currentFlow.value = await executionsStore.loadFlowForExecution({
            namespace: props.execution.namespace,
            flowId: props.execution.flowId,
            revision: props.execution.flowRevision,
            store: true,
        })
    }

    const restartLastRevision = () => {
        if (flowStore.revisions?.length) {
            revisionsSelected.value = flowStore.revisions[flowStore.revisions.length - 1].revision
        }
        restart()
    }

    const handleRestartExecute = () => {
        isOpen.value = false
        restart()
    }

    const handleReplayExecute = () => {
        isOpen.value = false

        if (hasInputs.value && (!canReuseInputs.value || (!replayTaskRun.value && inputMode.value === "modify"))) {
            openReplayWithInputsDialog()
            return
        }

        if (replayRevisionMode.value === "latest") {
            restartLastRevision()
            return
        }

        if (replayRevisionMode.value === "original") {
            revisionsSelected.value = props.execution.flowRevision
        }

        restart()
    }

    const restart = async () => {
        const method = `${replayOrRestart.value}Execution` as keyof typeof executionsStore
        const response = await (executionsStore[method] as any)({
            executionId: props.execution.id,
            taskRunId: replayTaskRun.value?.id,
            revision: props.isReplay ? revisionsSelected.value : undefined,
        })

        const newExecution = response

        toast.success(t(props.isReplay ? "replayed" : "restarted"))

        if (newExecution.id !== props.execution.id) {
            // The parent route resolves the default tab; the full page load runs the redirect.
            window.location.href = router.resolve({
                name: EXECUTION_PARENT_ROUTE,
                params: {
                    namespace: newExecution.namespace,
                    flowId: newExecution.flowId,
                    id: newExecution.id,
                    tenant: router.currentRoute.value.params.tenant,
                },
            }).href
        } else {
            window.setTimeout(() => window.location.reload(), 500)
        }
    }

    watch(isOpen, (newValue) => newValue && props.isReplay && loadRevision())

    watch([isOpen, needsReplayCheck, effectiveRevision], async () => {
        const sequence = ++replayCheckSequence
        // A new revision needs its own verdict, so the whole-execution choice is dropped with it.
        if (isOpen.value) replayWhole.value = false

        if (!isOpen.value || !needsReplayCheck.value) {
            replayCheck.value = {status: "idle"}
            return
        }

        replayCheck.value = {status: "checking"}
        try {
            await executionsStore.validateReplay({
                executionId: props.execution.id,
                taskRunId: props.taskRun?.id,
                revision: effectiveRevision.value,
            })
            if (sequence === replayCheckSequence) replayCheck.value = {status: "valid"}
        } catch (error) {
            if (sequence === replayCheckSequence) {
                const problem = asProblem(error)
                replayCheck.value = problem?.status === 409
                    ? {status: "refused", detail: problem.detail}
                    : {status: "error"}
            }
        }
    })

    watch(effectiveRevision, async (newRevision, oldRevision) => {
        if (!isOpen.value || newRevision === undefined || newRevision === oldRevision) return
        currentFlow.value = undefined
        currentFlow.value = await executionsStore.loadFlowForExecution({
            namespace: props.execution.namespace,
            flowId: props.execution.flowId,
            revision: newRevision,
            store: false,
        })
    })

    watch(canReuseInputs, (canReuse) => {
        inputMode.value = canReuse ? "reuse" : "modify"
    })

    defineExpose({
        open: () => {
            isOpen.value = true
        },
    })
</script>

<style lang="scss">
    .ks-restart-tooltip--no-pointer {
        pointer-events: none;
    }
</style>

<style scoped lang="scss">
.modal-header {
    .modal-title {
        font-size: var(--ks-font-size-base);
        font-weight: 600;
        margin: 0;
        color: var(--ks-text-primary);
    }
}
.execution-description {
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
}

.section-title {
    font-size: var(--ks-font-size-sm);
    font-weight: 600;
    margin: 20px 0 12px 0;
    color: var(--ks-text-primary);
}

.radio-vertical {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
}

.modal-header :deep(.kel-divider--horizontal) {
    margin-bottom: 8px;
}

.radio-item {
    :deep(.kel-radio__input) {
        .kel-radio__inner {
            width: 18px;
            height: 18px;

            &::after {
                width: 8px;
                height: 8px;
                background-color: var(--ks-btn-primary-bg-default);
            }
        }
    }

    :deep(.kel-radio__label) {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-primary);
        padding-left: 8px;
    }


    &.is-checked {
        :deep(.kel-radio__input) {
            .kel-radio__inner {
                border-color: var(--ks-btn-primary-bg-default);
                background-color: var(--ks-btn-primary-bg-default);

                &::after {
                    background-color: var(--ks-white);
                }
            }
        }

        :deep(.kel-radio__label) {
            color: var(--ks-text-primary) !important;
        }
    }
}
</style>
