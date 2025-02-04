<template>
    <el-tooltip
        effect="light"
        :persistent="false"
        transition=""
        :hide-after="0"
        :content="$t('change state tooltip')"
        raw-content
        :placement="tooltipPosition"
    >
        <component
            :is="component"
            :icon="StateMachine"
            @click="visible = !visible"
            :disabled="!enabled"
            class="ms-0 me-1"
        >
            {{ $t('change state') }}
        </component>
    </el-tooltip>

    <el-dialog v-if="enabled && visible" v-model="visible" :id="uuid" destroy-on-close :append-to-body="true">
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>

        <template #default>
            <p v-html="$t('change execution state confirm', {id: execution.id})" />

            <p>
                Current status is : <status size="small" class="me-1" :status="execution.state.current" />
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
                        <status size="small" :label="true" class="me-1" :status="item.code" />
                        <span v-html="item.label" />
                    </template>
                </el-option>
            </el-select>
        </template>

        <template #footer>
            <el-button @click="visible = false">
                {{ $t('cancel') }}
            </el-button>
            <el-button
                type="primary"
                @click="changeStatus()"
                :disabled="selectedStatus === execution.state.current || selectedStatus === null"
            >
                {{ $t('ok') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
    import StateMachine from "vue-material-design-icons/StateMachine.vue";
</script>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State} from "@kestra-io/ui-libs"
    import Status from "../../components/Status.vue";
    import ExecutionUtils from "../../utils/executionUtils";

    export default {
        components: {StateMachine, Status},
        props: {
            component: {
                type: String,
                default: "el-button"
            },
            execution: {
                type: Object,
                required: true
            },
            tooltipPosition: {
                type: String,
                default: "bottom"
            }
        },
        emits: ["follow"],
        methods: {
            changeStatus() {
                this.visible = false;

                this.$store
                    .dispatch("execution/changeExecutionStatus", {
                        executionId: this.execution.id,
                        state: this.selectedStatus
                    })
                    .then(response => {
                        if (response.data.id === this.execution.id) {
                            return ExecutionUtils.waitForState(this.$http, this.$store, response.data);
                        } else {
                            return response.data;
                        }
                    })
                    .then((execution) => {
                        this.$store.commit("execution/setExecution", execution)
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

                        this.$toast().success(this.$t("change execution state done"));
                    })
            },
        },
        computed: {
            ...mapState("auth", ["user"]),
            uuid() {
                return "changestatus-" + this.execution.id;
            },
            states() {
                return (this.execution.state.current === "PAUSED" ?
                    [
                        State.FAILED,
                        State.RUNNING,
                        State.CANCELLED,
                    ] :
                    [
                        State.FAILED,
                        State.SUCCESS,
                        State.WARNING,
                        State.CANCELLED,
                    ]
                )
                    .filter(value => value !== this.execution.state.current)
                    .map(value => {
                        return {
                            code: value,
                            label: this.$t("mark as", {status: value}),
                            disabled: value === this.execution.state.current
                        };
                    })
            },
            enabled() {
                if (!(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
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
                visible: false
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