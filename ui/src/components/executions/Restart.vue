<template>
    <span>
        <b-button
            @click="$bvModal.show(uuid)"
            v-if="enabled"
            :class="!isReplay ? 'rounded-lg btn-info restart mr-1' : ''"
        >
            <kicon :tooltip="$t(replayOrRestart)">
                <restart-icon v-if="!isReplay" />
                <play-box-multiple v-if="isReplay" />
                {{ (isReplay ? '' : $t(replayOrRestart)) }}
            </kicon>
        </b-button>

        <b-modal v-if="enabled" :id="uuid" @show="loadRevision">
            <template #modal-header>
                <h5>{{ $t("confirmation") }}</h5>
            </template>

            <template>
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
    </span>
</template>
<script>
    import RestartIcon from "vue-material-design-icons/Restart";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import State from "../../utils/state";
    import Kicon from "../Kicon"

    export default {
        components: {RestartIcon, PlayBoxMultiple, Kicon},
        props: {
            isReplay: {
                type: Boolean,
                default: false
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
                        this.$store.commit("execution/setExecution", response.data)
                        if (response.data.id === this.execution.id) {
                            // @TODO: we need to wait that the execution is updated by indexer
                        }
                        this.$router.push({name: "executions/update", params: response.data, query: {tab: "gantt"}});
                    })
                    .then(() => {
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
                return this.execution.id + (this.taskRun ? "-" + this.taskRun.id : "");
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
