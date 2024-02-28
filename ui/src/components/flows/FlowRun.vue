<template>
    <template v-if="flow">
        <el-alert v-if="flow.disabled" type="warning" show-icon :closable="false">
            <strong>{{ $t('disabled flow title') }}</strong><br>
            {{ $t('disabled flow desc') }}
        </el-alert>

        <el-form label-position="top" :model="inputs" ref="form" @submit.prevent="false">
            <el-form-item
                v-for="input in flow.inputs || []"
                :key="input.id"
                :label="input.id"
                :required="input.required !== false"
                :prop="input.id"
            >
                <editor
                    :full-height="false"
                    :input="true"
                    :navbar="false"
                    v-if="input.type === 'STRING' || input.type === 'URI'"
                    v-model="inputs[input.id]"
                />
                <el-select
                    :full-height="false"
                    :input="true"
                    :navbar="false"
                    v-if="input.type === 'ENUM'"
                    v-model="inputs[input.id]"
                >
                    <el-option
                        v-for="item in input.values"
                        :key="item"
                        :label="item"
                        :value="item"
                    >
                        {{ item }}
                    </el-option>
                </el-select>
                <el-input
                    type="password"
                    v-if="input.type === 'SECRET'"
                    v-model="inputs[input.id]"
                    show-password
                />
                <el-input-number
                    v-if="input.type === 'INT'"
                    v-model="inputs[input.id]"
                    :step="1"
                />
                <el-input-number
                    v-if="input.type === 'FLOAT'"
                    v-model="inputs[input.id]"
                    :step="0.001"
                />
                <el-radio-group
                    v-if="input.type === 'BOOLEAN'"
                    v-model="inputs[input.id]"
                >
                    <el-radio-button label="true" />
                    <el-radio-button label="false" />
                    <el-radio-button label="undefined" />
                </el-radio-group>
                <el-date-picker
                    v-if="input.type === 'DATETIME'"
                    v-model="inputs[input.id]"
                    type="datetime"
                />
                <el-date-picker
                    v-if="input.type === 'DATE'"
                    v-model="inputs[input.id]"
                    type="date"
                />
                <el-time-picker
                    v-if="input.type === 'TIME' || input.type === 'DURATION'"
                    v-model="inputs[input.id]"
                    type="time"
                />
                <div class="el-input el-input-file">
                    <div class="el-input__wrapper" v-if="input.type === 'FILE'">
                        <input
                            :id="input.id+'-file'"
                            class="el-input__inner"
                            type="file"
                            @change="onFileChange(input, $event)"
                            autocomplete="off"
                            :style="{display: typeof(inputs[input.id]) === 'string' && inputs[input.id].startsWith('kestra:///') ? 'none': ''}"
                        >
                        <label
                            v-if="typeof(inputs[input.id]) === 'string' && inputs[input.id].startsWith('kestra:///')"
                            :for="input.id+'-file'"
                        >Kestra Internal Storage File</label>
                    </div>
                </div>
                <editor
                    :full-height="false"
                    :input="true"
                    :navbar="false"
                    v-if="input.type === 'JSON'"
                    lang="json"
                    v-model="inputs[input.id]"
                />

                <markdown v-if="input.description" class="markdown-tooltip text-muted" :source="input.description" font-size-var="font-size-xs" />
            </el-form-item>

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
                        <el-button :icon="Flash" class="flow-run-trigger-button" @click="onSubmit($refs.form)" type="primary" native-type="submit" :disabled="flow.disabled || haveBadLabels">
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
    import Editor from "../../components/inputs/Editor.vue";
    import LabelInput from "../../components/labels/LabelInput.vue";
    import Markdown from "../layout/Markdown.vue";
    import {pageFromRoute} from "../../utils/eventsRouter";
    import {executeFlowBehaviours, storageKeys} from "../../utils/constants";

    export default {
        components: {Editor, LabelInput, Markdown,},
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
        created() {
            for (const input of this.flow.inputs || []) {
                this.inputs[input.id] = input.defaults;

                if (input.type === "BOOLEAN" && input.defaults === undefined){
                    this.inputs[input.id] = "undefined";
                }
            }
        },
        mounted() {
            setTimeout(() => {
                const input = this.$el && this.$el.querySelector && this.$el.querySelector("input")
                if (input && !input.className.includes("mx-input")) {
                    input.focus()
                }
            }, 500)

            this._keyListener = function(e) {
                if (e.keyCode === 13 && (e.ctrlKey || e.metaKey))  {
                    e.preventDefault();
                    this.onSubmit(this.$refs.form);
                }
            };

            document.addEventListener("keydown", this._keyListener.bind(this));
        },
        beforeUnmount() {
            document.removeEventListener("keydown", this._keyListener);
        },
        computed: {
            ...mapState("flow", ["flow"]),
            ...mapState("core", ["guidedProperties"]),
            ...mapState("execution", ["execution"]),
            haveBadLabels() {
                return this.executionLabels.some(label => (label.key && !label.value) || (!label.key && label.value));
            },
            // Required to have "undefined" value for boolean
            cleanInputs() {
                var inputs = this.inputs
                for (const input of this.flow.inputs || []) {
                    if (input.type === "BOOLEAN" && inputs[input.id] === "undefined") {
                        inputs[input.id] = undefined;
                    }
                }
                return inputs;
            }
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
                this.inputs = Object.fromEntries(
                    this.flow.inputs.filter(input => nonEmptyInputNames.includes(input.id))
                        .map(input => {
                            const inputName = input.id;
                            const inputType = input.type;
                            let inputValue = this.execution.inputs[inputName];
                            if (inputType === "DATE" || inputType === "DATETIME") {
                                inputValue = this.$moment(inputValue).toISOString()
                            }else if (inputType === "DURATION" || inputType === "TIME") {
                                inputValue = this.$moment().startOf("day").add(inputValue, "seconds").toString()
                            }else if (inputType === "JSON") {
                                inputValue = JSON.stringify(inputValue).toString()
                            }

                            if(inputValue === null) {
                                inputValue = undefined;
                            }

                            return [inputName, inputValue]
                        })
                );
            },
            onSubmit(formRef) {
                if (this.$tours["guidedTour"].isRunning.value) {
                    this.finishTour();
                }
                if (formRef) {
                    formRef.validate((valid) => {
                        if (!valid) {
                            return false;
                        }

                        executeTask(this, this.flow, this.cleanInputs, {
                            redirect: this.redirect,
                            newTab: this.newTab,
                            id: this.flow.id,
                            namespace: this.flow.namespace,
                            labels: this.executionLabels
                                .filter(label => label.key && label.value)
                                .map(label => `${label.key}:${label.value}`)
                        })
                        this.$emit("executionTrigger");
                    });
                }
            },
            finishTour() {
                this.$store.dispatch("api/events", {
                    type: "ONBOARDING",
                    onboarding: {
                        step: this.$tours["guidedTour"].currentStep._value,
                        action: "execute",
                    },
                    page: pageFromRoute(this.$router.currentRoute.value)
                });

                this.$store.dispatch("api/events", {
                    type: "ONBOARDING",
                    onboarding: {
                        step: this.$tours["guidedTour"].currentStep._value,
                        action: "finish",
                    },
                    page: pageFromRoute(this.$router.currentRoute.value)
                });

                localStorage.setItem("tourDoneOrSkip", "true");

                this.$store.commit("core/setGuidedProperties", {
                    tourStarted: false,
                    flowSource: undefined,
                    saveFlow: false,
                    executeFlow: false,
                    validateInputs: false,
                    monacoRange: undefined,
                    monacoDisableRange: undefined
                });

                return this.$tours["guidedTour"].finish();
            },
            onFileChange(input, e) {
                if (!e.target) {
                    return;
                }

                const files = e.target.files || e.dataTransfer.files;
                if (!files.length) {
                    return;
                }
                this.inputs[input.id] = e.target.files[0];
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
            },
            guidedProperties: {
                handler() {
                    if (this.guidedProperties.validateInputs) {
                        this.onSubmit(this.$refs.form);
                    }
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
</style>