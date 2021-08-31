<template>
    <span>
        <b-button
            @click="$bvModal.show(uuid)"
            v-if="enabled"
            :class="!isButtonGroup ? 'rounded-lg btn-info restart mr-1' : ''"
        >
            <kicon :tooltip="$t('restart')">
                <restart-icon />
                {{ (isButtonGroup ? '' : $t("restart")) }}
            </kicon>
        </b-button>

        <b-modal :id="uuid" @show="loadRevision">
            <template #modal-header>
                <h5>{{ $t("confirmation") }}</h5>
            </template>

            <template>
                <p v-html="$t('restart confirm', {id: execution.id})" />

                <b-form>
                    <p>{{ $t("restart change revision") }}</p>
                    <b-form-group label-cols-sm="3" label-cols-lg="3" :label="$t('revisions')" label-for="input-revision">
                        <b-form-select v-model="revisionsSelected" :options="revisionsOptions" />
                    </b-form-group>
                </b-form>
            </template>

            <template #modal-footer="{ok, cancel}">
                <b-button @click="cancel()">
                    Cancel
                </b-button>
                <b-button variant="primary" @click="restart(ok)">
                    OK
                </b-button>
            </template>
        </b-modal>
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
            loadRevision() {
                this.revisionsSelected = this.execution.flowRevision
                this.$store
                    .dispatch("flow/loadRevisions", {
                        namespace: this.execution.namespace,
                        id: this.execution.flowId
                    })
            },
            restart(closeCallback) {
                closeCallback()

                this.$store
                    .dispatch("execution/restartExecution", {
                        executionId: this.execution.id,
                        taskId: this.task ? this.task.taskId : undefined,
                        revision: this.sameRevision(this.revisionsSelected) ? undefined : this.revisionsSelected
                    })
                    .then(response => {
                        this.$store.commit("execution/setExecution", response.data)
                        if (response.data.id === this.execution.id) {
                            // @TODO: we need to wait that the execution is updated by indexer
                        }
                        this.$router.push({name: "executions/update", params: response.data, query: {tab: "gantt"}});
                    })
                    .then(() => {
                        this.$toast().success(this.$t("restarted"));
                    })
            },
            sameRevision(revision) {
                return this.execution.flowRevision === revision;
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("flow", ["revisions"]),
            revisionsOptions() {
                return (this.revisions || []).map((revision) => {
                    return {
                        value: revision.revision,
                        text: revision.revision + (this.sameRevision(revision.revision) ? " (" + this.$t("current") + ")" : ""),
                    };
                });
            },
            uuid() {
                return this.execution.id + (this.task ? "-" + this.task.id : "");
            },
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
        },
        data() {
            return {
                revisionsSelected: undefined,
            };
        },
    };
</script>
