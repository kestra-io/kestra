<template>
    <el-tooltip
        effect="light"
        v-if="isReplay || enabled"
        :persistent="false"
        transition=""
        :hide-after="0"
        :content="tooltip"
        raw-content
        :placement="tooltipPosition"
    >
        <component
            :is="component"
            v-bind="$attrs"
            :icon="!isReplay ? RestartIcon : PlayBoxMultiple"
            @click="isOpen = !isOpen"
            v-if="component !== 'el-dropdown-item'"
            :disabled="!enabled"
            :class="!isReplay ? 'restart me-1' : ''"
        >
            {{ $t(replayOrRestart) }}
        </component>
        <span v-else-if="component === 'el-dropdown-item'">
            <component
                :is="component"
                v-bind="$attrs"
                :icon="!isReplay ? RestartIcon : PlayBoxMultiple"
                @click="isOpen = !isOpen"
                :disabled="!enabled"
                :class="!isReplay ? 'restart me-1' : ''"
            >
                {{ $t(replayOrRestart) }}
            </component>
        </span>
    </el-tooltip>
    <el-dialog v-if="enabled && isOpen" v-model="isOpen" destroy-on-close :append-to-body="true">
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>

        <template #footer>
            <el-button @click="isOpen = false">
                {{ $t('cancel') }}
            </el-button>
            <el-button @click="restartLastRevision()">
                {{ $t( replayOrRestart + ' latest revision') }}
            </el-button>
            <el-button type="primary" @click="restart()">
                {{ $t('ok') }}
            </el-button>
        </template>

        <p v-html="$t(replayOrRestart + ' confirm', {id: execution.id})" />
        <inputs-form :initial-inputs="initialInputs.inputs" :flow="initialInputs" v-model="inputs" :execute-clicked="executeClicked" @confirm="onSubmit($refs.form)" />
        <div class="bottom-buttons" v-if="!embed">
            <div class="left-align">
                <el-form-item>
                    <el-button :icon="ContentCopy" @click="fillInputsFromExecution">
                        {{ $t('prefill inputs') }}
                    </el-button>
                </el-form-item>
            </div>
        </div>

        <el-form v-if="revisionsOptions && revisionsOptions.length > 1">
            <p class="execution-description">
                {{ $t("restart change revision") }}
            </p>
            <el-form-item :label="$t('revisions')">
                <el-select v-model="revisionsSelected">
                    <el-option
                        v-for="item in revisionsOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>
        </el-form>
    </el-dialog>
</template>

<script setup>
    import RestartIcon from "vue-material-design-icons/Restart.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";
</script>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import State from "../../utils/state";
    import ExecutionUtils from "../../utils/executionUtils";
    import InputsForm from "../../components/inputs/InputsForm.vue";
    import Inputs from "../../utils/inputs";
    import {inputsToFormDate} from "../../utils/submitTask"

    export default {
        components: {InputsForm},
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
            },
            tooltipPosition: {
                type: String,
                default: "bottom"
            },
            initialInputs: {
                type: Array,
                default: undefined
            },
            flow: {
                type: Object,
                default: undefined,
            }
        },
        emits: ["follow"],
        watch: {
            isOpen(newValue) {
                if (newValue) {
                    this.loadRevision()
                    setTimeout(() => {
                        this.fillInputsFromExecution()
                    },800   ) 
                }
            }
        },
        methods: {
            fillInputsFromExecution(){
                const nonEmptyInputNames = Object.keys(this.execution.inputs);
                this.initialInputs.inputs
                    .filter(input => nonEmptyInputNames.includes(input.id))
                    .forEach(input => {
                        let value = this.execution.inputs[input.id];
                        this.inputs[input.id] = Inputs.normalize(input.type, value);
                    });
            },
            purgeInputs(inputs){
                for (let input in inputs) {
                    if (inputs[input] === undefined || inputs[input] === "") {
                        delete inputs[input];
                    }
                }
                return inputs;
            },
            loadRevision() {
                this.revisionsSelected = this.execution.flowRevision
                this.$store
                    .dispatch("flow/loadRevisions", {
                        namespace: this.execution.namespace,
                        id: this.execution.flowId
                    })
            },
            restartLastRevision() {
                this.revisionsSelected = this.revisions[this.revisions.length - 1].revision;
                this.restart();
            },
            restart() {
                this.isOpen = false
                const inputs = this.purgeInputs(this.inputs)
                const formData = inputsToFormDate(this, this.initialInputs.inputs, inputs);

                this.$store
                    .dispatch(`execution/${this.replayOrRestart}Execution`, {
                        formData: formData,
                        executionId: this.execution.id,
                        taskRunId: this.taskRun && this.isReplay ? this.taskRun.id : undefined,
                        revision: this.sameRevision(this.revisionsSelected) ? undefined : this.revisionsSelected
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
                                }});
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
                return (this.revisions || [])
                    .map((revision) => {
                        return {
                            value: revision.revision,
                            text: revision.revision + (this.sameRevision(revision.revision) ? " (" + this.$t("current") + ")" : ""),
                        };
                    })
                    .reverse();
            },
            enabled() {
                if (this.isReplay && !(this.user && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.execution.namespace))) {
                    return false;
                }

                if (!this.isReplay && !(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                if (this.isReplay && (this.taskRun?.attempts !== undefined && this.taskRun.attempts.length - 1 !== this.attemptIndex)) {
                    return false;
                }

                if (State.isRunning(this.execution.state.current)) {
                    return false;
                }

                return (this.isReplay && !State.isRunning(this.execution.state.current)) ||
                    (!this.isReplay && this.execution.state.current === State.FAILED);
            },
            tooltip(){
                if(this.isReplay){
                    return this?.taskRun?.id ? this.$t("replay from task tooltip", {taskId: this.taskRun.taskId}) :  this.$t("replay from beginning tooltip");
                }

                return this.$t("restart tooltip", {state: this.execution.state.current})
            }
        },
        data() {
            return {
                inputs: {},
                revisionsSelected: undefined,
                isOpen: false,
            };
        },
    };
</script>
<style scoped>
.execution-description {
    color: var(--bs-gray-700); 
}
</style>