<template>
    <component
        :is="component"
        :icon="icon.StateMachine"
        @click="visible = !visible"
        :disabled="!enabled"
    >
        <span v-if="component !== 'el-button'">{{ $t('change state') }}</span>

        <el-dialog v-if="enabled && visible" v-model="visible" :id="uuid" destroyOnClose :appendToBody="true">
            <template #header>
                <h5>{{ $t("confirmation") }}</h5>
            </template>

            <template #default>
                <p v-html="$t('change state confirm', {id: execution.id, task: taskRun.taskId})" />

                <p>
                    {{ $t('change state current state') }} <Status size="small" class="me-1" :status="taskRun.state.current" />
                </p>

                <el-select
                    :required="true"
                    v-model="selectedStatus"
                    :persistent="false"
                >
                    <el-option
                        v-for="item in states"
                        :key="item.code"
                        :value="item.code"
                        :disabled="item.disabled"
                    >
                        <template #default>
                            <Status size="small" :label="true" class="me-1" :status="item.code" />
                            <span v-html="item.label" />
                        </template>
                    </el-option>
                </el-select>

                <div v-if="selectedStatus" class="alert alert-info alert-status-change mt-2" role="alert">
                    <ul>
                        <li v-for="(text, i) in $t('change status hint')[selectedStatus]" :key="i">
                            {{ text }}
                        </li>
                    </ul>
                </div>
            </template>

            <template #footer>
                <el-button @click="visible = false">
                    {{ $t('cancel') }}
                </el-button>
                <el-button
                    type="primary"
                    @click="changeStatus()"
                    :disabled="selectedStatus === taskRun.state.current || selectedStatus === null"
                >
                    {{ $t('ok') }}
                </el-button>
            </template>
        </el-dialog>
    </component>
</template>

<script>
    import StateMachine from "vue-material-design-icons/StateMachine.vue";
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State, Status} from "@kestra-io/ui-libs"
    import * as ExecutionUtils from "../../utils/executionUtils";
    import {shallowRef} from "vue";
    import {useAuthStore} from "override/stores/auth"

    export default {
        components: {StateMachine, Status},
        props: {
            component: {
                type: String,
                default: "b-button"
            },
            execution: {
                type: Object,
                required: true
            },
            taskRun: {
                type: Object,
                required: false,
                default: undefined
            },
            attemptIndex: {
                type: Number,
                required: false,
                default: undefined
            }
        },
        emits: ["follow"],
        methods: {
            changeStatus() {
                this.visible = false;

                this.executionsStore
                    .changeStatus({
                        executionId: this.execution.id,
                        taskRunId: this.taskRun.id,
                        state: this.selectedStatus
                    })
                    .then(response => {
                        if (response.data.id === this.execution.id) {
                            return ExecutionUtils.waitForState(this.$http, response.data);
                        } else {
                            return response.data;
                        }
                    })
                    .then((execution) => {
                        this.executionsStore.execution = execution;
                        if (execution.id === this.execution.id) {
                            this.$emit("follow")
                        } else {
                            this.$router.push({
                                name: "executions/update",
                                params: {
                                    namespace: execution.namespace,
                                    flowId: execution.flowId,
                                    id: execution.id,
                                    tab: "gantt",
                                    tenant: this.$route.params.tenant
                                }
                            });
                        }

                        this.$toast().success(this.$t("change state done"));
                    })
            },
        },
        computed: {
            ...mapStores(useExecutionsStore, useAuthStore),
            uuid() {
                return "changestatus-" + this.execution.id + (this.taskRun ? "-" + this.taskRun.id : "");
            },
            states() {
                return (this.taskRun.state.current === "PAUSED" ?
                    [
                        State.FAILED,
                        State.RUNNING,
                    ] :
                    [
                        State.FAILED,
                        State.SUCCESS,
                        State.WARNING,
                    ]
                )
                    .filter(value => value !== this.taskRun.state.current)
                    .map(value => {
                        return {
                            code: value,
                            label: this.$t("mark as", {status: value}),
                            disabled: value === this.taskRun.state.current
                        };
                    })
            },
            enabled() {
                if (!(this.authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                if (this.taskRun.attempts !== undefined && this.taskRun.attempts.length - 1 !== this.attemptIndex) {
                    return false;
                }

                if (this.taskRun.state.current === "PAUSED" || this.taskRun.state.current === "CREATED") {
                    return true;
                }

                if (State.isRunning(this.execution.state.current)) {
                    return false;
                }

                return true;
            }
        },
        data() {
            return {
                selectedStatus: undefined,
                visible: false,
                icon: {StateMachine: shallowRef(StateMachine)}
            };
        },
    };
</script>

<style lang="scss">
    .alert-status-change {
        ul {
            margin-bottom: 0;
            padding-left: 10px;
        }
    }
</style>
