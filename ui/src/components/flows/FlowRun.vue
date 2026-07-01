<template>
    <template v-if="flow">
        <KsAlert v-if="flow.disabled" type="warning" :closable="false">
            <strong>{{ $t('disabled flow title') }}</strong><br>
            {{ $t('disabled flow desc') }}
        </KsAlert>
        <div class="flow-execution-checks-alerts">
            <KsAlert v-for="alert in checks || []" :type="toAlertType(alert.style)" :closable="false" :key="alert.message">
                {{ alert.message }}
            </KsAlert>
        </div>
        <KsForm labelPosition="top" :model="inputs" ref="form" @submit.prevent="false">
            <KsTabs v-model="openTab" type="segmented">
                <KsTabPane name="inputs" :label="$t('inputs')" class="execution-pane">
                    <InputsForm
                        v-if="flow.inputs?.length"
                        ref="inputsFormRef"
                        :initialInputs="flow.inputs"
                        :selectedTrigger="selectedTrigger"
                        :flow="flow"
                        mode="wizard"
                        v-model="inputs"
                        :executeClicked="executeClicked"
                        @confirm="onSubmit"
                        @ready="onInputsFormReady"
                        @update:model-value-no-default="values => inputsNoDefaults=values"
                        @update:checks="onChecksUpdate"
                        @update:on-recap="value => inputsOnRecap = value"
                    />
                    <KsText v-else type="info">
                        {{ $t('no inputs') }}
                    </KsText>
                </KsTabPane>
                <KsTabPane name="labels" :label="$t('advanced configuration')" class="execution-pane">
                    <KsFormItem
                        :label="$t('execution labels')"
                    >
                        <LabelInput
                            v-model:labels="executionLabels"
                        />
                        <KsText v-if="haveBadLabels" type="danger" size="small">
                            {{ $t('wrong labels') }}
                        </KsText>
                        <KsText v-if="haveForbiddenSystemLabels" type="danger" size="small">
                            {{ $t('forbidden system labels') }}
                        </KsText>
                    </KsFormItem>
                    <KsFormItem
                        :label="$t('scheduleDate')"
                    >
                        <KsDatePicker
                            v-model="scheduleDate"
                            type="datetime"
                        />
                    </KsFormItem>
                    <KsFormItem
                        :label="$t('breakpoints')"
                    >
                        <KsSelect
                            v-model="breakpoints"
                            multiple
                            filterable
                            :placeholder="$t('breakpoints')"
                        >
                            <KsOption
                                v-for="taskId in taskIds"
                                :key="taskId"
                                :label="taskId"
                                :value="taskId"
                            />
                        </KsSelect>
                    </KsFormItem>
                </KsTabPane>
                <KsTabPane name="curl" :label="$t('curl.command')" class="execution-pane">
                    <Curl :flow="flow" :executionLabels="executionLabels" :inputs="inputs" />
                </KsTabPane>
                <KsTabPane v-if="hasWebhookTriggers" name="webhookCurl" :label="$t('webhook.curl_command')" class="execution-pane">
                    <WebhookCurl :flow="flow" />
                </KsTabPane>
            </KsTabs>

            <div class="bottom-buttons" v-if="!embed">
                <div class="left-align">
                    <KsFormItem>
                        <KsButton v-if="execution && (execution.inputs || hasExecutionLabels())" :icon="ContentCopy" @click="fillInputsFromExecution">
                            {{ $t('prefill inputs') }}
                        </KsButton>
                    </KsFormItem>
                </div>
                <div class="right-align" v-if="!hasFormInputs || inputsOnRecap">
                    <KsFormItem class="submit">
                        <span data-onboarding-target="flow-execute-confirm-button">
                            <KsButton
                                :icon="buttonIcon"
                                :disabled="!flowCanBeExecuted || hasBlockingChecks"
                                :data-test="buttonTestId"
                                class="flow-run-trigger-button"
                                type="primary"
                                nativeType="submit"
                                @click.prevent="() => { onSubmit(); executeClicked = true; }"
                            >
                                {{ $t(buttonText) }}
                            </KsButton>
                        </span>
                    </KsFormItem>
                </div>
            </div>
        </KsForm>
    </template>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import type {Component} from "vue"
    import {useRouter, useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {useToast} from "../../utils/toast"
    import moment from "moment-timezone"
    import {useCoreStore} from "../../stores/core"
    import {useApiStore} from "../../stores/api"
    import {useMiscStore} from "override/stores/misc"
    import {useExecutionsStore} from "../../stores/executions"
    import type {Label, Execution, Check} from "../../stores/executions"
    import type {Flow} from "../../stores/flow"
    import {buildExecutionLabelStrings, hasForbiddenUserSystemLabels} from "../../utils/executionLabels"
    import {executeTask} from "../../utils/submitTask"
    import {getAllTaskIds} from "../../utils/flowUtils"
    import {executeFlowBehaviours, storageKeys} from "../../utils/constants"
    import {WEBHOOK_TRIGGER_TYPE} from "../../utils/webhook"
    import {normalize, flattenInputs} from "../../utils/inputs"
    import type {InputType} from "../../utils/inputs"
    import get from "lodash/get"
    import type {FormInstance} from "@kestra-io/design-system"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import Play from "vue-material-design-icons/Play.vue"
    import Curl from "./Curl.vue"
    import WebhookCurl from "./WebhookCurl.vue"
    import InputsForm from "../../components/inputs/InputsForm.vue"
    import LabelInput from "../../components/labels/LabelInput.vue"

    type AlertType = "success" | "warning" | "info" | "error"
    
    function toAlertType(style: string): AlertType {
        return style.toLowerCase() as AlertType
    }

    interface ReplaySubmitOptions {
        formRef: FormInstance
        id: string
        namespace: string
        inputs: Record<string, unknown>
        labels: string[]
        scheduleDate: string | undefined
        breakpoints?: string[]
    }

    export interface SelectedTrigger {
        inputs?: Record<string, unknown>
    }

    const props = withDefaults(defineProps<{
        redirect?: boolean
        embed?: boolean
        replaySubmit?: ((options: ReplaySubmitOptions) => void | Promise<void>) | null
        selectedTrigger?: SelectedTrigger
        buttonText?: string
        buttonIcon?: Component
        buttonTestId?: string
        autoPrefill?: boolean
    }>(), {
        redirect: true,
        embed: false,
        replaySubmit: null,
        selectedTrigger: undefined,
        buttonText: "launch execution",
        buttonIcon: () => Play as Component,
        buttonTestId: "execute-dialog-button",
        autoPrefill: false,
    })

    const emit = defineEmits<{
        executionTrigger: []
        updateInputs: [inputs: Record<string, unknown>]
        updateInputsNoDefault: [inputs: Record<string, unknown>]
        updateLabels: [labels: Label[]]
    }>()

    const {t} = useI18n({useScope: "global"})
    const toast = useToast()
    const router = useRouter()
    const route = useRoute()
    const apiStore = useApiStore()
    const coreStore = useCoreStore()
    const miscStore = useMiscStore()
    const executionsStore = useExecutionsStore()

    const openTab = ref("inputs")
    const inputs = ref<Record<string, unknown>>({})
    const inputsNoDefaults = ref<Record<string, unknown>>({})
    const executionLabels = ref<Label[]>([])
    const scheduleDate = ref<string | undefined>(undefined)
    const breakpoints = ref<string[]>([])
    const taskIds = computed(() => {
        return getAllTaskIds(flow.value)
    })
    const newTab = ref(localStorage.getItem(storageKeys.EXECUTE_FLOW_BEHAVIOUR) === executeFlowBehaviours.NEW_TAB)
    const executeClicked = ref(false)
    const checks = ref<Check[]>([])
    // wizard recap state: the footer Execute button only appears once every step is filled
    const inputsOnRecap = ref(false)

    const form = ref<FormInstance | null>(null)
    const inputsFormRef = ref<InstanceType<typeof InputsForm> | null>(null)

    const flow = computed<Flow | undefined>(() => executionsStore.flow as Flow | undefined)
    const execution = computed<Execution | undefined>(() => executionsStore.execution)

    // a flow with FORM inputs renders the wizard; the footer Execute is gated on the recap step
    const hasFormInputs = computed(() => (flow.value?.inputs ?? []).some((input: {type?: string}) => input.type === "FORM"))

    const haveBadLabels = computed(() =>
        executionLabels.value.some(label => (label.key && !label.value) || (!label.key && label.value)),
    )

    const haveForbiddenSystemLabels = computed(() =>
        hasForbiddenUserSystemLabels(executionLabels.value),
    )

    const flowCanBeExecuted = computed(() =>
        Boolean(flow.value && !flow.value.disabled && !haveBadLabels.value && !haveForbiddenSystemLabels.value),
    )

    const isDirty = computed(() =>
        Object.keys(inputsNoDefaults.value).length > 0 ||
        executionLabels.value.some(label => label.key || label.value) ||
        scheduleDate.value !== undefined,
    )

    const hasWebhookTriggers = computed(() => {
        if (!flow.value?.triggers) {
            return false
        }
        return flow.value.triggers.some(trigger =>
            trigger.type === WEBHOOK_TRIGGER_TYPE &&
            ("disabled" in trigger ? trigger.disabled === undefined || trigger.disabled === false : true),
        )
    })

    const hasBlockingChecks = computed(() =>
        checks.value.some(check => check.behavior === "BLOCK_EXECUTION"),
    )

    // The dialog-footer Execute (FlowRunActions) must follow the wizard: hidden until the recap when
    // the flow has FORM inputs, so only one primary button shows (Next in-step, Execute on recap).
    const showExecuteButton = computed(() => !hasFormInputs.value || inputsOnRecap.value)

    const canPrefill = computed(() =>
        Boolean(execution.value && (execution.value.inputs || hasExecutionLabels())),
    )

    function submit() {
        onSubmit()
        executeClicked.value = true
    }

    defineExpose({
        isDirty,
        submit,
        prefill: fillInputsFromExecution,
        canPrefill,
        flowCanBeExecuted,
        hasBlockingChecks,
        showExecuteButton,
        buttonText: props.buttonText,
        buttonIcon: props.buttonIcon,
        buttonTestId: props.buttonTestId,
    })

    function getExecutionLabels(): Label[] {
        if (!execution.value?.labels) {
            return []
        }
        // flow.labels at runtime is Label[] for the execution-context flow
        const flowLabels = flow.value?.labels as unknown as Label[] | undefined
        if (!flowLabels) {
            return execution.value.labels
        }
        return execution.value.labels.filter(label =>
            !flowLabels.some(flowLabel => flowLabel.key === label.key && flowLabel.value === label.value),
        )
    }

    function hasExecutionLabels() {
        return getExecutionLabels().length > 0
    }

    function onChecksUpdate(values: Check[]) {
        checks.value = values
    }

    let autoPrefilled = false
    function onInputsFormReady() {
        if (props.autoPrefill && !autoPrefilled && canPrefill.value) {
            autoPrefilled = true
            fillInputsFromExecution()
        }
    }

    function fillInputsFromExecution() {
        // Add all labels except the one from flow to prevent duplicates
        const toIgnore: string[] = miscStore.configs?.hiddenLabelsPrefixes ?? []
        executionLabels.value = getExecutionLabels().filter(item => !toIgnore.some(prefix => item.key.startsWith(prefix)))

        const inputsForm = inputsFormRef.value
        if (!inputsForm || !flow.value?.inputs) {
            return
        }

        // execution.inputs is nested (FORM groups -> {environment:{region:"EU"}}); the model is
        // flat-keyed by dotted leaf id, so walk each leaf's dotted path into the nested inputs.
        const executionInputs = execution.value?.inputs ?? {}
        flattenInputs(flow.value.inputs)
            .forEach(leaf => {
                const value = get(executionInputs, leaf.id)
                if (value === undefined) {
                    return
                }
                inputsForm.inputsValues[leaf.id] = normalize(leaf.type as InputType, value)
                const meta = inputsForm.inputsMetaData.find(m => m.id === leaf.id)
                if (meta) {
                    meta.isDefault = false
                }
            })
    }

    // Adapter object for the legacy executeTask utility
    const submitor = {
        $moment: moment,
        $router: router,
        $route: route,
        $toast: () => toast,
        $t: t,
    }

    function onSubmit() {
        if (form.value && flowCanBeExecuted.value) {
            apiStore.posthogEvents({
                type: "FLOW_EXECUTION",
                action: "submit",
            })
            checks.value = []
            executeClicked.value = false
            coreStore.message = undefined
            form.value.validate(async (valid: boolean) => {
                if (!valid) {
                    return
                }

                const mergedInputs = props.selectedTrigger?.inputs
                    ? {...props.selectedTrigger.inputs, ...inputsNoDefaults.value}
                    : inputsNoDefaults.value

                const labelStrings = buildExecutionLabelStrings(executionLabels.value)

                try {
                    if (props.replaySubmit) {
                        await props.replaySubmit({
                            formRef: form.value!,
                            id: flow.value!.id,
                            namespace: flow.value!.namespace,
                            inputs: mergedInputs,
                            labels: labelStrings,
                            scheduleDate: scheduleDate.value,
                            breakpoints: breakpoints.value,
                        })
                    } else {
                        const shouldShowOnboardingSuccessAnimation = route.query.onboardingPreset === "true"
                        if (flow.value) {
                            await executeTask(submitor, flow.value, mergedInputs, {
                                redirect: props.redirect,
                                newTab: newTab.value,
                                id: flow.value.id,
                                namespace: flow.value.namespace,
                                labels: labelStrings,
                                scheduleDate: moment(scheduleDate.value)
                                    .tz(localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) ?? moment.tz.guess())
                                    .toISOString(true),
                                nextStep: true,
                                query: shouldShowOnboardingSuccessAnimation ? {
                                    autoExpandGantt: "true",
                                    onboardingSuccess: "true",
                                } : undefined,
                                breakpoints: breakpoints.value,
                            })
                        }
                    }
                    executeClicked.value = true
                    emit("executionTrigger")
                } catch {
                    // API errors are surfaced by the global axios error handler
                }
            })
        }
    }

    watch(inputs, () => {
        emit("updateInputs", inputs.value)
    }, {deep: true})

    watch(inputsNoDefaults, () => {
        emit("updateInputsNoDefault", inputsNoDefaults.value)
    }, {deep: true})

    watch(executionLabels, () => {
        emit("updateLabels", executionLabels.value)
    }, {deep: true})
</script>

<style scoped lang="scss">
    .flow-execution-checks-alerts {
        margin-bottom: 1rem;
    }
    :deep(.kel-collapse) {
        border-radius: var(--kel-border-radius-round);
        border: 1px solid var(--ks-border-default);
        background: var(--ks-bg-tag);

        .kel-collapse-item__header {
            background: transparent;
            border-bottom: 1px solid var(--ks-border-default);
            font-size: var(--ks-font-size-sm);
        }

        .kel-collapse-item__content {
            background: var(--ks-bg-tag);
            border-bottom: 1px solid var(--ks-border-default);
        }

        .kel-collapse-item__header, .kel-collapse-item__content {
            &:last-child {
                border-bottom-left-radius: var(--kel-border-radius-round);
                border-bottom-right-radius: var(--kel-border-radius-round);
            }
        }
    }

    .onboarding-glow {
        animation: glowAnimation 1s infinite alternate;
    }

    @keyframes glowAnimation {
        0% {
            box-shadow: 0px 0px 0px 0px #8405FF;
        }
        100% {
            box-shadow: 0px 0px 50px 2px #8405FF;
        }
    }

    .right-align{
        text-align: right;
    }

    .execution-pane {
        margin-top: 1rem;
    }
</style>
