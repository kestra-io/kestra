<template>
    <template v-if="flow">
        <KsAlert v-if="flow.disabled" type="warning" showIcon :closable="false">
            <strong>{{ $t('disabled flow title') }}</strong><br>
            {{ $t('disabled flow desc') }}
        </KsAlert>
        <div class="flow-execution-checks-alerts">
            <KsAlert v-for="alert in checks || []" :type="alert.style.toLowerCase()" showIcon :closable="false" :key="alert">
                {{ alert.message }}
            </KsAlert>
        </div>
        <KsForm labelPosition="top" :model="inputs" ref="form" @submit.prevent="false">
            <InputsForm
                ref="inputsFormRef"
                :initialInputs="flow.inputs"
                :selectedTrigger="selectedTrigger"
                :flow="flow"
                v-model="inputs"
                :executeClicked="executeClicked"
                @confirm="onSubmit($refs.form)"
                @update:model-value-no-default="values => inputsNoDefaults=values"
                @update:checks="values => checks=values"
            />

            <KsCollapse v-model="collapseName">
                <KsCollapseItem :title="$t('advanced configuration')" name="advanced">
                    <KsFormItem
                        :label="$t('execution labels')"
                    >
                        <LabelInput
                            :key="executionLabels"
                            v-model:labels="executionLabels"
                        />
                    </KsFormItem>
                    <KsFormItem
                        :label="$t('scheduleDate')"
                    >
                        <KsDatePicker
                            v-model="scheduleDate"
                            type="datetime"
                        />
                    </KsFormItem>
                </KsCollapseItem>
                <KsCollapseItem :title="$t('curl.command')" name="curl">
                    <Curl :flow="flow" :executionLabels="executionLabels" :inputs="inputs" />
                </KsCollapseItem>
                <KsCollapseItem v-if="hasWebhookTriggers" :title="$t('webhook.curl_command')" name="webhook-curl">
                    <WebhookCurl :flow="flow" />
                </KsCollapseItem>
            </KsCollapse>

            <div class="bottom-buttons" v-if="!embed">
                <div class="left-align">
                    <KsFormItem>
                        <KsButton v-if="execution && (execution.inputs || hasExecutionLabels())" :icon="ContentCopy" @click="fillInputsFromExecution">
                            {{ $t('prefill inputs') }}
                        </KsButton>
                    </KsFormItem>
                </div>
                <div class="right-align">
                    <KsFormItem class="submit">
                        <span data-onboarding-target="flow-execute-confirm-button">
                            <KsButton
                                :icon="buttonIcon"
                                :disabled="!flowCanBeExecuted || hasBlockingChecks()"
                                class="flow-run-trigger-button"
                                type="primary"
                                nativeType="submit"
                                @click.prevent="onSubmit($refs.form); executeClicked = true;"
                            >
                                {{ $t(buttonText) }}
                            </KsButton>
                        </span>
                        <KsText v-if="haveBadLabels" type="danger" size="small">
                            {{ $t('wrong labels') }}
                        </KsText>
                    </KsFormItem>
                </div>
            </div>
        </KsForm>
    </template>
</template>

<script setup>
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import Play from "vue-material-design-icons/Play.vue"
</script>

<script>
    import moment from "moment-timezone"
    import {mapStores} from "pinia"
    import {useCoreStore} from "../../stores/core"
    import {useApiStore} from "../../stores/api"
    import {useMiscStore} from "override/stores/misc"
    import {useExecutionsStore} from "../../stores/executions"
    import {usePlaygroundStore} from "../../stores/playground"
    import {executeTask} from "../../utils/submitTask"
    import {executeFlowBehaviours, storageKeys} from "../../utils/constants"
    import {normalize} from "../../utils/inputs"
    import Curl from "./Curl.vue"
    import WebhookCurl from "./WebhookCurl.vue"
    import InputsForm from "../../components/inputs/InputsForm.vue"
    import LabelInput from "../../components/labels/LabelInput.vue"

    export default {
        components: {
            LabelInput,
            InputsForm,
            Curl,
            WebhookCurl,
        },
        props: {
            redirect: {type: Boolean, default: true},
            embed: {type: Boolean, default: false},
            replaySubmit: {type: Function, default: null},
            selectedTrigger: {type: Object, default: undefined},
            buttonText: {type: String, default: "launch execution"},
            buttonIcon: {type: [Object, Function], default: () => Play},
            buttonTestId: {type: String, default: "execute-dialog-button"},
        },
        data() {
            return {
                inputs: {},
                inputsNoDefaults: {},
                inputNewLabel: "",
                executionLabels: [],
                scheduleDate: undefined,
                inputVisible: false,
                collapseName: undefined,
                newTab: localStorage.getItem(storageKeys.EXECUTE_FLOW_BEHAVIOUR) === executeFlowBehaviours.NEW_TAB,
                executeClicked: false,
                checks: [],
            }
        },
        emits: ["executionTrigger", "updateInputs", "updateLabels"],
        computed: {
            ...mapStores(useApiStore, useCoreStore, useMiscStore, useExecutionsStore, usePlaygroundStore),
            flow() {
                return this.executionsStore.flow
            },
            execution() {
                return this.executionsStore.execution
            },
            haveBadLabels() {
                return this.executionLabels.some(label => (label.key && !label.value) || (!label.key && label.value))
            },
            flowCanBeExecuted() {
                return this.flow && !this.flow.disabled && !this.haveBadLabels
            },
            hasWebhookTriggers() {
                if (!this.flow?.triggers) {
                    return false
                }
                return this.flow.triggers.some(trigger =>
                    trigger.type === "io.kestra.plugin.core.trigger.Webhook" &&
                    (trigger.disabled === undefined || trigger.disabled === false),
                )
            },
        },
        methods: {
            hasBlockingChecks() {
                return this.checks.filter(check => check.behavior === "BLOCK_EXECUTION").length > 0
            },
            getExecutionLabels() {
                if (!this.execution.labels) {
                    return []
                }
                if (!this.flow.labels) {
                    return this.execution.labels
                }
                return this.execution.labels.filter(label => {
                    return !this.flow.labels.some(flowLabel => flowLabel.key === label.key && flowLabel.value === label.value)
                })
            },
            hasExecutionLabels() {
                return this.getExecutionLabels().length > 0
            },
            fillInputsFromExecution(){
                // Add all labels except the one from flow to prevent duplicates
                const toIgnore = this.miscStore.configs?.hiddenLabelsPrefixes || []
                this.executionLabels = this.getExecutionLabels().filter(item => !toIgnore.some(prefix => item.key.startsWith(prefix)))

                const inputsForm = this.$refs.inputsFormRef
                if (!inputsForm || !this.flow.inputs) {
                    return
                }

                const nonEmptyInputNames = Object.keys(this.execution.inputs)
                this.flow.inputs
                    .filter(input => nonEmptyInputNames.includes(input.id))
                    .forEach(input => {
                        let value = this.execution.inputs[input.id]
                        inputsForm.inputsValues[input.id] = normalize(input.type, value)
                        const meta = inputsForm.inputsMetaData.find(m => m.id === input.id)
                        if (meta) {
                            meta.isDefault = false
                        }
                    })
            },
            onSubmit(formRef) {
                if (formRef && this.flowCanBeExecuted) {
                    this.apiStore.posthogEvents({
                        type: "FLOW_EXECUTION",
                        action: "submit",
                    })
                    this.checks = []
                    this.executeClicked = false
                    this.coreStore.message = null
                    formRef.validate((valid) => {
                        if (!valid) {
                            return false
                        }

                        if (this.replaySubmit) {
                            this.replaySubmit({
                                formRef,
                                id: this.flow.id,
                                namespace: this.flow.namespace,
                                inputs: this.selectedTrigger?.inputs ? {...this.selectedTrigger.inputs, ...this.inputsNoDefaults} : this.inputsNoDefaults,
                                labels: [...new Set(
                                    this.executionLabels
                                        .filter(label => label.key && label.value)
                                        .map(label => `${label.key}:${label.value}`),
                                ), "system.from:ui"],
                                scheduleDate: this.scheduleDate,
                            })
                        } else {
                            const shouldShowOnboardingSuccessAnimation = this.$route.query.onboardingPreset === "true"

                            executeTask(this, this.flow, this.selectedTrigger?.inputs ? {...this.selectedTrigger.inputs, ...this.inputsNoDefaults} : this.inputsNoDefaults, {
                                redirect: this.redirect,
                                newTab: this.newTab,
                                id: this.flow.id,
                                namespace: this.flow.namespace,
                                labels: [...new Set(
                                    this.executionLabels
                                        .filter(label => label.key && label.value)
                                        .map(label => `${label.key}:${label.value}`),
                                ), "system.from:ui"],
                                scheduleDate: this.$moment(this.scheduleDate).tz(localStorage.getItem(storageKeys.TIMEZONE_STORAGE_KEY) ?? moment.tz.guess()).toISOString(true),
                                nextStep: true,
                                query: shouldShowOnboardingSuccessAnimation ? {
                                    autoExpandGantt: "true",
                                    onboardingSuccess: "true",
                                } : undefined,
                            })
                        }
                        this.executeClicked = true
                        this.$emit("executionTrigger")
                    })
                }
            },
            state(input) {
                const required = input.required === undefined ? true : input.required

                if (!required && input.value === undefined) {
                    return null
                }

                if (required && input.value === undefined) {
                    return false
                }

                return true
            },
        },
        watch: {
            inputs: {
                handler() {
                    this.$emit("updateInputs", this.inputs)
                },
                deep: true,
            },
            executionLabels: {
                handler() {
                    this.$emit("updateLabels", this.executionLabels)
                },
                deep: true,
            },
        },
    }
</script>

<style scoped lang="scss">
    .flow-execution-checks-alerts {
        margin-bottom: 1rem;
    }
    :deep(.kel-collapse) {
        border-radius: var(--kel-border-radius-round);
        border: 1px solid var(--ks-border-primary);
        background: var(--ks-tag-background);

        .kel-collapse-item__header {
            background: transparent;
            border-bottom: 1px solid var(--ks-border-primary);
            font-size: var(--ks-font-size-sm);
        }

        .kel-collapse-item__content {
            background: var(--ks-tag-background);
            border-bottom: 1px solid var(--ks-border-primary);
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
</style>
