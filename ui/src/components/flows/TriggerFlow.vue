<template>
    <div class="trigger-flow-wrapper">
        <el-button id="execute-button" :icon="icon.Flash" :type="type" :disabled="isDisabled()" @click="onClick()">
            {{ $t("execute") }}
        </el-button>
        <el-dialog id="execute-flow-dialog" v-if="isOpen" v-model="isOpen" destroy-on-close :show-close="!guidedProperties.tourStarted" :before-close="(done) => beforeClose(done)" :append-to-body="true">
            <template #header>
                <span v-html="$t('execute the flow', {id: flowId})" />
            </template>
            <flow-run @execution-trigger="closeModal" :redirect="true" />
        </el-dialog>
        <el-dialog v-if="isSelectFlowOpen" v-model="isSelectFlowOpen" destroy-on-close :before-close="() => reset()" :append-to-body="true">
            <el-form
                label-position="top"
            >
                <el-form-item :label="$t('namespace')">
                    <el-select
                        v-model="localNamespace"
                    >
                        <el-option
                            v-for="np in namespaces"
                            :key="np"
                            :label="np"
                            :value="np"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item
                    v-if="localNamespace && flowsExecutable.length > 0"
                    :label="$t('flow')"
                >
                    <el-select
                        v-model="localFlow"
                    >
                        <el-option
                            v-for="flow in flowsExecutable"
                            :key="flow.id"
                            :label="flow.id"
                            :value="flow"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item v-if="localFlow" :label="$t('inputs')">
                    <flow-run @execution-trigger="closeModal" :redirect="true" />
                </el-form-item>
            </el-form>
        </el-dialog>
    </div>
</template>


<script>
    import FlowRun from "./FlowRun.vue";
    import {mapState} from "vuex";
    import Flash from "vue-material-design-icons/Flash.vue";
    import {shallowRef} from "vue";
    import {pageFromRoute} from "../../utils/eventsRouter";

    export default {
        components: {
            FlowRun
        },
        props: {
            flowId: {
                type: String,
                default: undefined
            },
            namespace: {
                type: String,
                default: undefined
            },
            disabled: {
                type: Boolean,
                default: false
            },
            type: {
                type: String,
                default: "primary"
            }
        },
        data() {
            return {
                isOpen: false,
                isSelectFlowOpen: false,
                localFlow: undefined,
                localNamespace: undefined,
                icon: {
                    Flash: shallowRef(Flash)
                }
            };
        },
        methods: {
            onClick() {
                if (this.$tours["guidedTour"].isRunning.value) {
                    this.$tours["guidedTour"].nextStep();
                    this.$store.dispatch("api/events", {
                        type: "ONBOARDING",
                        onboarding: {
                            step: this.$tours["guidedTour"].currentStep._value,
                            action: "next",
                            template: this.guidedProperties.template
                        },
                        page: pageFromRoute(this.$router.currentRoute.value)
                    });
                    this.isOpen = !this.isOpen;
                    return;
                } else if (this.computedNamespace !== undefined && this.computedFlowId !== undefined) {
                    this.isOpen = !this.isOpen;
                } else {
                    this.$store.dispatch("execution/loadNamespaces");
                    this.isSelectFlowOpen = !this.isSelectFlowOpen;
                }
            },
            closeModal() {
                this.isOpen = false;
            },
            isDisabled() {
                return this.disabled || this.flow?.deleted;
            },
            loadDefinition() {
                this.$store.dispatch("execution/loadFlowForExecution", {
                    flowId: this.flowId,
                    namespace: this.namespace
                });
            },
            reset() {
                this.isOpen = false;
                this.isSelectFlowOpen = false;
                this.localFlow = undefined;
                this.localNamespace = undefined;
            },
            beforeClose(done){
                if(this.guidedProperties.tourStarted) return;
                   
                this.reset();
                done()
            }
        },
        computed: {
            ...mapState("flow", ["executeFlow"]),
            ...mapState("core", ["guidedProperties"]),
            ...mapState("execution", ["flow", "namespaces", "flowsExecutable"]),
            ...mapState("auth", ["user"]),
            computedFlowId() {
                return this.flowId || this.localFlow?.id;
            },
            computedNamespace() {
                return this.namespace || this.localNamespace;
            }
        },
        watch: {
            guidedProperties: {
                handler() {
                    if (this.guidedProperties.executeFlow) {
                        this.onClick();
                    }
                },
                deep: true
            },
            executeFlow: {
                handler() {
                    if (this.executeFlow && !this.isDisabled()) {
                        this.$store.commit("flow/executeFlow", false);
                        this.onClick();
                    }
                }
            },
            flowId: {
                handler() {
                    if (!this.flowId) {
                        return;
                    }

                    this.loadDefinition();
                },
                immediate: true
            },
            localNamespace: {
                handler() {
                    if (!this.localNamespace) {
                        return;
                    }
                    this.$store.dispatch("execution/loadFlowsExecutable", {
                        namespace: this.localNamespace
                    });
                },
                immediate: true
            },
            localFlow: {
                handler() {
                    if (!this.localFlow) {
                        return;
                    }
                    this.$store.commit("execution/setFlow", this.localFlow);
                },
                immediate: true
            },
            isOpen: {
                handler() {
                    if (this.isOpen && this.flowId && this.namespace) {
                        this.loadDefinition();
                    }
                }
            }
        }
    };
</script>

<style scoped>
    .trigger-flow-wrapper {
        display: inline;
    }
</style>