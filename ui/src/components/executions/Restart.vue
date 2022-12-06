<template>
    <component
        :is="component"
        :icon="!isReplay ? RestartIcon : PlayBoxMultiple"
        @click="isOpen = !isOpen"
        v-if="isReplay || enabled"
        :disabled="!enabled"
        :class="!isReplay ? 'restart me-1' : ''"
    >
        {{ $t(replayOrRestart) }}
    </component>

    <el-dialog v-if="enabled && isOpen" v-model="isOpen" @open="loadRevision" destroy-on-close :append-to-body="true">
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>

        <template #footer>
            <el-button @click="isOpen = false">
                Cancel
            </el-button>
            <el-button type="primary" @click="restart()">
                OK
            </el-button>
        </template>

        <p v-html="$t(replayOrRestart + ' confirm', {id: execution.id})" />

        <el-form class="text-muted">
            <p>{{ $t("restart change revision") }}</p>
            <el-form-item :label="$t('revisions')">
                <el-select
                    v-model="revisionsSelected"
                    filterable
                    :persistent="false"
                    :placeholder="$t('revisions')"
                >
                    <el-option
                        v-for="item in revisionsOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    >
                        {{ item.value }}
                    </el-option>
                </el-select>
            </el-form-item>
        </el-form>
    </el-dialog>
</template>

<script setup>
    import RestartIcon from "vue-material-design-icons/Restart";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple";
</script>

<script>

    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import State from "../../utils/state";
    import ExecutionUtils from "../../utils/executionUtils";

    export default {
        components: {RestartIcon, PlayBoxMultiple},
        props: {
            component: {
                type: String,
                default: "el-button"
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
            restart() {
                this.isOpen = false

                this.$store
                    .dispatch(`execution/${this.replayOrRestart}Execution`, {
                        executionId: this.execution.id,
                        taskRunId: this.taskRun && this.isReplay ? this.taskRun.id : undefined,
                        revision: this.sameRevision(this.revisionsSelected) ? undefined : this.revisionsSelected
                    })
                    .then(response => {
                        if (response.data.id === this.execution.id) {
                            return ExecutionUtils.waitForState(this.$http, response.data);
                        } else {
                            return response.data;
                        }
                    })
                    .then((execution) => {
                        this.$store.commit("execution/setExecution", execution)
                        if (execution.id === this.execution.id) {
                            this.$emit("follow")
                        } else {
                            this.$router.push({name: "executions/update", params: {...{namespace: execution.namespace, flowId: execution.flowId, id: execution.id}, ...{tab: "gantt"}}});
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
                isOpen: false,
            };
        },
    };
</script>
