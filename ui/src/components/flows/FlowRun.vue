<template>
    <template v-if="flow">
        <el-alert v-if="flow.disabled" type="warning" show-icon :closable="false">
            <strong>{{ $t('disabled flow title') }}</strong><br>
            {{ $t('disabled flow desc') }}
        </el-alert>

        <el-form label-position="top" :model="inputs" ref="form" @submit.prevent="false">
            <inputs-form :inputs-list="flow.inputs" v-model="inputs" />
            <el-collapse class="mt-4" v-model="collapseName">
                <el-collapse-item :title="$t('advanced configuration')" name="advanced">
                    <el-form-item
                        :label="$t('execution labels')"
                    >
                        <label-input
                            :key="executionLabels"
                            v-model:labels="executionLabels"
                        />
                    </el-form-item>
                </el-collapse-item>
            </el-collapse>

            <div class="bottom-buttons" v-if="!embed">
                <div class="left-align">
                    <el-form-item>
                        <el-button v-if="execution && (execution.inputs || hasExecutionLabels())" :icon="ContentCopy" @click="fillInputsFromExecution">
                            {{ $t('prefill inputs') }}
                        </el-button>
                    </el-form-item>
                </div>
                <div class="right-align">
                    <el-form-item class="submit">
                        <el-button
                            data-test-id="execute-dialog-button"
                            :icon="Flash"
                            class="flow-run-trigger-button"
                            :class="{'onboarding-glow': guidedProperties.tourStarted}"
                            @click="onSubmit($refs.form)"
                            type="primary"
                            native-type="submit"
                            :disabled="flow.disabled || haveBadLabels"
                        >
                            {{ $t('launch execution') }}
                        </el-button>
                        <el-text v-if="haveBadLabels" type="danger" size="small">
                            {{ $t('wrong labels') }}
                        </el-text>
                    </el-form-item>
                </div>
            </div>
        </el-form>
    </template>
</template>

<script setup>
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Flash from "vue-material-design-icons/Flash.vue";
</script>

<script>
    import {mapState} from "vuex";
    import {executeTask} from "../../utils/submitTask"
    import InputsForm from "../../components/inputs/InputsForm.vue";
    import LabelInput from "../../components/labels/LabelInput.vue";
    import {executeFlowBehaviours, storageKeys} from "../../utils/constants";
    import Inputs from "../../utils/inputs";

    export default {
        components: {LabelInput, InputsForm},
        props: {
            redirect: {
                type: Boolean,
                default: true
            },
            embed: {
                type: Boolean,
                default: false
            }
        },
        data() {
            return {
                inputs: {},
                inputNewLabel: "",
                executionLabels: [],
                inputVisible: false,
                collapseName: undefined,
                newTab: localStorage.getItem(storageKeys.EXECUTE_FLOW_BEHAVIOUR) === executeFlowBehaviours.NEW_TAB
            };
        },
        emits: ["executionTrigger", "updateInputs", "updateLabels"],
        computed: {
            ...mapState("execution", ["flow", "execution"]),
            ...mapState("core", ["guidedProperties"]),
            haveBadLabels() {
                return this.executionLabels.some(label => (label.key && !label.value) || (!label.key && label.value));
            },
        },
        methods: {
            getExecutionLabels() {
                if (!this.execution.labels) {
                    return [];
                }
                if (!this.flow.labels) {
                    return this.execution.labels;
                }
                return this.execution.labels.filter(label => {
                    return !this.flow.labels.some(flowLabel => flowLabel.key === label.key && flowLabel.value === label.value);
                });
            },
            hasExecutionLabels() {
                return this.getExecutionLabels().length > 0;
            },
            fillInputsFromExecution(){
                // Add all labels except the one from flow to prevent duplicates
                this.executionLabels = this.getExecutionLabels();

                if (!this.flow.inputs) {
                    return;
                }

                const nonEmptyInputNames = Object.keys(this.execution.inputs);
                this.flow.inputs
                    .filter(input => nonEmptyInputNames.includes(input.id))
                    .forEach(input => {
                        let value = this.execution.inputs[input.id];
                        this.inputs[input.id] =  Inputs.normalize(input.type, value);
                    });
            },
            purgeInputs(inputs){
                for (let input in inputs) {
                    if (inputs[input] === undefined || inputs[input] === "") {
                        inputs[input] = null;
                    }
                }
                return inputs;
            },
            onSubmit(formRef) {
                if (formRef) {
                    formRef.validate((valid) => {
                        if (!valid) {
                            return false;
                        }

                        const inputs = this.purgeInputs(this.inputs)
                        executeTask(this, this.flow, inputs, {
                            redirect: this.redirect,
                            newTab: this.newTab,
                            id: this.flow.id,
                            namespace: this.flow.namespace,
                            labels: this.executionLabels
                                .filter(label => label.key && label.value)
                                .map(label => `${label.key}:${label.value}`),
                            nextStep: true
                        })
                        this.$emit("executionTrigger");
                    });
                }
            },

            state(input) {
                const required = input.required === undefined ? true : input.required;

                if (!required && input.value === undefined) {
                    return null;
                }

                if (required && input.value === undefined) {
                    return false;
                }

                return true;
            },
        },
        watch: {
            inputs: {
                handler() {
                    this.$emit("updateInputs", this.inputs);
                },
                deep: true
            },
            executionLabels: {
                handler() {
                    this.$emit("updateLabels", this.executionLabels);
                },
                deep: true
            }
        }
    };
</script>

<style scoped lang="scss">
    :deep(.el-collapse) {
        border-radius: var(--bs-border-radius);
        .el-collapse-item__header {
            border: 0;
            font-size: var(--el-font-size-extra-small);
            background: transparent;
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