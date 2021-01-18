<template>
    <span>
        <b-button
            @click="restart"
            v-if="enabled"
            :class="!isButtonGroup ? 'rounded-lg btn-info restart mr-1' : ''"
        >
            <kicon :tooltip="$t('restart')">
                <restart-icon />
                {{ (isButtonGroup ? '' : $t("restart")) }}
            </kicon>
        </b-button>
    </span>
</template>
<script>
    import RestartIcon from "vue-material-design-icons/Restart";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import State from "../../utils/state";
    import Kicon from "../Kicon"

    export default {
        components: {RestartIcon, Kicon},
        props: {
            isButtonGroup: {
                type: Boolean,
                default: false
            },
            execution: {
                type: Object,
                required: true
            },
            task: {
                type: Object,
                required: false,
                default: undefined
            }
        },
        methods: {
            restart() {
                this.$toast()
                    .confirm(this.$t("restart confirm", {id: this.execution.id}), () => {
                        this.$store
                            .dispatch("execution/restartExecution", {
                                id: this.execution.id,
                                taskId: this.task ? this.task.taskId : null
                            })
                            .then(response => {
                                this.$store.commit("execution/setExecution", response.data);
                                this.$router.push({name: "executionEdit", params: response.data});
                                this.$emit("restart")
                            })
                            .then(() => {
                                this.$toast().success(this.$t("restarted"));
                            })
                    });
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            enabled() {
                // TODO : Add a "restartable" property on task run object (backend side)
                if (!(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                // If a specific task has been passed, we see if it can be restarted
                if (this.task && this.task.taskId) {
                    // We find the taskRun based on its taskId
                    let taskRunIndex = this.execution.taskRunList.findIndex(
                        t => t.taskId === this.task.taskId
                    );

                    if (taskRunIndex === -1) return false;

                    // There can be no taskRun with a failed state before
                    // our specific task for it to be restarted
                    let subList = this.execution.taskRunList.slice(0, taskRunIndex);

                    let indexOfFailedTaskRun = subList.findIndex(
                        t => t.state.current === State.FAILED
                    );

                    return indexOfFailedTaskRun === -1;
                }
                return this.execution.state.current === State.FAILED;
            }
        }
    };
</script>
