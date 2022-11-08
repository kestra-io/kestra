<template>
    <component
        :is="component"
        @click="$bvModal.show(uuid)"
        v-if="isReplay || enabled"
        :disabled="!enabled"
        :class="!isReplay ? 'btn-info restart mr-1' : ''"
    >
        <kicon :tooltip="$t(replayOrRestart)">
            <restart-icon v-if="!isReplay" />
            <play-box-multiple v-if="isReplay" />
            {{ (isReplay ? '' : $t(replayOrRestart)) }}
        </kicon>

        <span v-if="component !== 'b-button'">{{ $t(replayOrRestart) }}</span>

        <b-modal v-if="enabled" :id="uuid" @show="loadRevision">
            <template #modal-header>
                <h5>{{ $t("confirmation") }}</h5>
            </template>

            <template #default>
                <p v-html="$t(replayOrRestart + ' confirm', {id: execution.id})" />

                <b-form class="text-muted">
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
    </component>
</template>
<script>
    import RestartIcon from "vue-material-design-icons/Restart";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import State from "../../utils/state";
    import Kicon from "../Kicon"
    import ExecutionUtils from "../../utils/executionUtils";

    export default {
        components: {RestartIcon, PlayBoxMultiple, Kicon},
        props: {
            component: {
                type: String,
                default: "b-button"
            },
            isReplay: {
                type: Boolean,
                default: false
            },
            isButton: {
                type: Boolean,
                default: true
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
                    .dispatch(`execution/${this.replayOrRestart}Execution`, {
                        executionId: this.execution.id,
                        taskRunId: this.taskRun && this.isReplay ? this.taskRun.id : undefined,
                        revision: this.sameRevision(this.revisionsSelected) ? undefined : this.revisionsSelected
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

                        this.$toast().success(this.$t(this.replayOrRestart + "ed"));
                    })
            },
            sameRevision(revision) {
                return this.execution.flowRevision === revision;
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("flow", ["revisions"]),
            replayOrRestart() {
                return this.isReplay ? "replay" : "restart";
            },
            revisionsOptions() {
                return (this.revisions || []).map((revision) => {
                    return {
                        value: revision.revision,
                        text: revision.revision + (this.sameRevision(revision.revision) ? " (" + this.$t("current") + ")" : ""),
                    };
                });
            },
            uuid() {
                return "restart-" + this.execution.id + (this.taskRun ? "-" + this.taskRun.id : "");
            },
            enabled() {
                if (this.isReplay && !(this.user && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.execution.namespace))) {
                    return false;
                }

                if (!this.isReplay && !(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                if (this.isReplay && (this.taskRun.attempts !== undefined && this.taskRun.attempts.length - 1 !== this.attemptIndex)) {
                    return false;
                }

                if (State.isRunning(this.execution.state.current)) {
                    return false;
                }

                return (this.isReplay && !State.isRunning(this.execution.state.current)) ||
                    (!this.isReplay && this.execution.state.current === State.FAILED);
            }
        },
        data() {
            return {
                revisionsSelected: undefined,
            };
        },
    };
</script>
