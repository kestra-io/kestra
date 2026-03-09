<template>
    <div class="trigger-flow-wrapper">
        <el-button v-if="playgroundStore.enabled" id="run-all-button" :icon="icon.Play" class="el-button--playground" :disabled="isDisabled() || !playgroundStore.readyToStart" @click="playgroundStore.runUntilTask()">
            {{ $t("playground.run_all_tasks") }}
        </el-button>
        <span v-else data-onboarding-target="flow-execute-button">
            <el-button
                id="execute-button"
                :icon="icon.Play"
                :type="type"
                :disabled="isDisabled()"
                @click="onClick()"
            >
                {{ $t("execute") }}
            </el-button>
        </span>
        <el-dialog
            id="execute-flow-dialog"
            v-model="isOpen"
            destroyOnClose
            :showClose="true"
            :beforeClose="(done) => beforeClose(done)"
            :appendToBody="true"
            :width="dialogWidth"
        >
            <template #header>
                <span v-html="$t('execute the flow', {id: flowId})" />
            </template>
            <FlowRun @execution-trigger="handleExecutionStart" :redirect="!playgroundStore.enabled" />
        </el-dialog>
        <el-dialog
            v-if="isSelectFlowOpen"
            v-model="isSelectFlowOpen"
            destroyOnClose
            :beforeClose="() => reset()"
            :appendToBody="true"
            :width="dialogWidth"
        >
            <el-form
                labelPosition="top"
            >
                <el-form-item :label="$t('namespace')">
                    <el-select
                        v-model="localNamespace"
                    >
                        <el-option
                            v-for="np in executionsStore.namespaces"
                            :key="np"
                            :label="np"
                            :value="np"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item
                    v-if="localNamespace && executionsStore.flowsExecutable.length > 0"
                    :label="$t('flow')"
                >
                    <el-select
                        v-model="localFlow"
                        valueKey="id"
                    >
                        <el-option
                            v-for="exFlow in executionsStore.flowsExecutable"
                            :key="exFlow.id"
                            :label="exFlow.id"
                            :value="exFlow"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item v-if="localFlow" :label="$t('inputs')">
                    <div class="w-100">
                        <FlowRun @execution-trigger="handleExecutionStart" :redirect="!playgroundStore.enabled" />
                    </div>
                </el-form-item>
            </el-form>
        </el-dialog>
    </div>
</template>


<script>
    import FlowRun from "./FlowRun.vue";
    import Play from "vue-material-design-icons/Play.vue";
    import {shallowRef} from "vue";
    import {useMediaQuery} from "@vueuse/core";
    import FlowWarningDialog from "./FlowWarningDialog.vue";
    import {mapStores} from "pinia";
    import {useApiStore} from "../../stores/api";
    import {useExecutionsStore} from "../../stores/executions";
    import {usePlaygroundStore} from "../../stores/playground";
    import {useFlowStore} from "../../stores/flow";

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
            },
            flowSource: {
                type: String,
                default: null
            }
        },
        data() {
            return {
                isOpen: false,
                isSelectFlowOpen: false,
                localFlow: undefined,
                localNamespace: undefined,
                isLargeScreen: useMediaQuery("(min-width: 768px)"),
                icon: {
                    Play: shallowRef(Play)
                }
            };
        },
        methods: {
            trackExecutionAction(action) {
                this.apiStore.posthogEvents({
                    type: "FLOW_EXECUTION",
                    action,
                });
            },
            async handleExecutionStart() {
                this.closeModal();
                this.$toast().success(this.$t("execution_started"));
            },
            onClick() {
                this.trackExecutionAction("open_modal");
                if (this.checkForTrigger) {
                    this.$toast().confirm(FlowWarningDialog, () => (this.toggleModal()), true, null);
                }
                else if (this.computedNamespace !== undefined && this.computedFlowId !== undefined) {
                    this.toggleModal(true)
                }
                else {
                    this.executionsStore.loadNamespaces();
                    this.isSelectFlowOpen = !this.isSelectFlowOpen;
                }
            },
            async toggleModal(newValue) {
                if (newValue === undefined) {
                    newValue = !this.isOpen;
                }
                if (newValue && this.flowId && this.namespace) {
                    // wait for flow to be set before opening the dialog
                    await this.loadDefinition();
                }
                this.isOpen = newValue;
            },
            closeModal() {
                this.isOpen = false;
            },
            isDisabled() {
                return this.disabled || this.executionsStore.flow?.deleted;
            },
            async loadDefinition() {
                await this.executionsStore.loadFlowForExecution({
                    flowId: this.flowId,
                    namespace: this.namespace,
                    store: true
                });
            },
            reset() {
                this.isOpen = false;
                this.isSelectFlowOpen = false;
                this.localFlow = undefined;
                this.localNamespace = undefined;
            },
            beforeClose(done){
                this.reset();
                done()
            }
        },
        computed: {
            ...mapStores(useApiStore, useExecutionsStore, usePlaygroundStore, useFlowStore),
            dialogWidth() {
                return this.isLargeScreen ? "50%" : "90%";
            },
            computedFlowId() {
                return this.flowId || this.localFlow?.id;
            },
            computedNamespace() {
                return this.namespace || this.localNamespace;
            },
            checkForTrigger() {
                if (this.flowSource) {
                    const triggerRegex = /\{\{\s*\(?\s*(\|\||&&)?\s*trigger\s*(\.\w+|\|\s*\w+)?\s*\}\}/;
                    return triggerRegex.test(this.flowSource);
                }
                return false;
            }
        },
        watch: {
            "flowStore.executeFlow": {
                handler(value) {
                    if (value && !this.isDisabled()) {
                        this.flowStore.executeFlow = false;
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
                    this.executionsStore.loadFlowsExecutable({
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
                    this.executionsStore.flow = this.localFlow;
                },
                immediate: true
            }
        }
    };
</script>

<style scoped>
    .trigger-flow-wrapper {
        display: inline;
    }
</style>
