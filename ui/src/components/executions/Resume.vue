<template>
    <component
        :is="component"
        :icon="PlayBox"
        @click="click"
        v-if="enabled"
        class="ms-0 me-1"
    >
        {{ $t('resume') }}
    </component>

    <el-dialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroy-on-close :append-to-body="true">
        <template #header>
            <span v-html="$t('resumed title', {id: execution.id})" />
        </template>
        <el-form :model="inputs" label-position="top" ref="form" @submit.prevent="false">
            <inputs-form :initial-inputs="inputsList" :execution="execution" v-model="inputs" />
        </el-form>
        <template #footer>
            <el-button @click="cancel()">
                {{ $t('cancel') }}
            </el-button>
            <el-button :icon="PlayBox" type="primary" @click="resumeWithInputs($refs.form)" native-type="submit">
                {{ $t('resume') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
    import PlayBox from "vue-material-design-icons/PlayBox.vue";
</script>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State} from "@kestra-io/ui-libs"
    import FlowUtils from "../../utils/flowUtils";
    import ExecutionUtils from "../../utils/executionUtils";
    import InputsForm from "../../components/inputs/InputsForm.vue";
    import {inputsToFormDate} from "../../utils/submitTask";

    export default {
        components: {InputsForm},
        props: {
            execution: {
                type: Object,
                required: true
            },
            component: {
                type: String,
                default: "el-button"
            },
        },
        data() {
            return {
                inputs: {},
                isDrawerOpen: false,
            };
        },
        created() {
            if (this.enabled) {
                this.loadDefinition();
            }
        },
        methods: {
            click() {
                if (this.needInputs) {
                    this.isDrawerOpen = true;
                    return;
                }

                this.$toast()
                    .confirm(this.$t("resumed confirm", {id: this.execution.id}), () => {
                        return this.resume();
                    });
            },
            resumeWithInputs(formRef) {
                if (formRef) {
                    formRef.validate((valid) => {
                        if (!valid) {
                            return false;
                        }

                        const formData = inputsToFormDate(this, this.inputsList, this.inputs);
                        this.resume(formData);
                    });
                }

            },
            resume(formData) {
                this.$store
                    .dispatch("execution/resume", {
                        id: this.execution.id,
                        formData: formData
                    })
                    .then(() => {
                        this.isDrawerOpen = false;
                        this.$toast().success(this.$t("resumed done"));
                    });
            },
            cancel() {
                this.$store
                    .dispatch("execution/kill", {
                        id: this.execution.id,
                    })
                    .then(() => {
                        this.isDrawerOpen = false;
                        this.$toast().success(this.$t("killed done"));
                    });
            },
            loadDefinition() {
                this.$store.dispatch("execution/loadFlowForExecution", {
                    flowId: this.execution.flowId,
                    namespace: this.execution.namespace
                });
            },
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("execution", ["flow"]),
            enabled() {
                if (!(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                return State.isPaused(this.execution.state.current);
            },
            inputsList() {
                const findTaskRunByState = ExecutionUtils.findTaskRunsByState(this.execution, State.PAUSED);
                if (findTaskRunByState.length === 0) {
                    return [];
                }

                const findTaskById = FlowUtils.findTaskById(this.flow, findTaskRunByState[0].taskId);

                return findTaskById && findTaskById.inputs !== null ? findTaskById.inputs : [];
            },
            needInputs() {
                return this.inputsList?.length > 0;
            }
        },
    };
</script>

<style lang="scss" scoped>
    button.el-button {
        cursor: pointer !important;
    }
</style>
