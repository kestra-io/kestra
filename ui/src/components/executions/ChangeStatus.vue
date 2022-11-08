<template>
    <component
        :is="component"
        @click="$bvModal.show(uuid)"
        :disabled="!enabled"
    >
        <kicon :tooltip="$t('change status')">
            <state-machine />
        </kicon>

        <span v-if="component !== 'b-button'">{{ $t('change status') }}</span>

        <b-modal v-if="enabled" :id="uuid">
            <template #modal-header>
                <h5>{{ $t("confirmation") }}</h5>
            </template>

            <template #default>
                <p v-html="$t('change status confirm', {id: execution.id, task: taskRun.taskId})" />

                <p>
                    Current status is : <status size="sm" class="mr-1" :status="this.taskRun.state.current" />
                </p>

                <v-select
                    :required="true"
                    v-model="selectedStatus"
                    :options="states"
                    :reduce="value => value.code"
                    :selectable="(option) => !option.disabled"
                >
                    <template #selected-option="{code, label}">
                        <status size="sm" :label="false" class="mr-1" :status="code" />
                        <span v-html="label" />
                    </template>
                    <template #option="{code, label}">
                        <status size="sm" :label="false" class="mr-1" :status="code" />
                        <span v-html="label" />
                    </template>
                </v-select>


                <div v-if="selectedStatus" class="alert alert-info alert-status-change mt-2" role="alert">
                    <ul>
                        <li v-for="(text, i) in $t('change status hint')[this.selectedStatus]" :key="i">
                            {{ text }}
                        </li>
                    </ul>
                </div>
            </template>

            <template #modal-footer="{ok, cancel}">
                <b-button @click="cancel()">
                    Cancel
                </b-button>
                <b-button
                    variant="primary"
                    @click="changeStatus(ok)"
                    :disabled="selectedStatus === taskRun.state.current || selectedStatus === null"
                >
                    OK
                </b-button>
            </template>
        </b-modal>
    </component>
</template>
<script>
    import StateMachine from "vue-material-design-icons/StateMachine";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Kicon from "../Kicon"
    import State from "../../utils/state";
    import Status from "../../components/Status";
    import ExecutionUtils from "../../utils/executionUtils";

    export default {
        components: {StateMachine, Status, Kicon},
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
            changeStatus(closeCallback) {
                closeCallback()

                this.$store
                    .dispatch("execution/changeStatus", {
                        executionId: this.execution.id,
                        taskRunId: this.taskRun.id,
                        state: this.selectedStatus
                    })
                    .then(response => {
                        if (response.data.id === this.execution.id) {
                            return ExecutionUtils.waitForState(response.data);
                        } else {
                            return response.data;
                        }
                    })
                    .then((execution) => {
                        this.$store.commit("execution/setExecution", execution)
                        if (execution.id === this.execution.id) {
                            this.$emit("follow")
                        } else {
                            this.$router.push({name: "executions/update", params: {...execution, ...{tab: "gantt"}}});
                        }

                        this.$toast().success(this.$t("change status done"));
                    })
            },
        },
        computed: {
            ...mapState("auth", ["user"]),
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
                if (!(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                if (this.taskRun.attempts !== undefined && this.taskRun.attempts.length - 1 !== this.attemptIndex) {
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
            };
        },
    };
</script>

<style lang="scss">

.alert-status-change  {
    ul {
        margin-bottom: 0;
        padding-left: 10px;
    }
}
</style>
