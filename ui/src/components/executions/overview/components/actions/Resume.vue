<template>
    <el-button
        v-if="enabled"
        :icon="Play"
        @click="click"
    >
        {{ $t('resume') }}
    </el-button>

    <el-dialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <span v-html="$t('resumed title', {id: execution.id})" />
        </template>
        <el-form :model="inputs" labelPosition="top" ref="form" @submit.prevent="false">
            <InputsForm :initialInputs="inputsList" :execution="execution" v-model="inputs" />
        </el-form>
        <template #footer>
            <el-button :icon="PlayBox" type="primary" @click="resumeWithInputs($refs.form)" nativeType="submit">
                {{ $t('resume') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
    import Play from "vue-material-design-icons/Play.vue";
</script>

<script>
    import permission from "../../../../../models/permission";
    import action from "../../../../../models/action";
    import {State} from "@kestra-io/ui-libs"
    import FlowUtils from "../../../../../utils/flowUtils";
    import * as ExecutionUtils from "../../../../../utils/executionUtils";
    import InputsForm from "../../../../../components/inputs/InputsForm.vue";
    import {inputsToFormData} from "../../../../../utils/submitTask";
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../../../../stores/executions";
    import {useAuthStore} from "override/stores/auth"

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
                    }, () => {}, false);
            },
            resumeWithInputs(formRef) {
                if (formRef) {
                    formRef.validate((valid) => {
                        if (!valid) {
                            return false;
                        }

                        const formData = inputsToFormData(this, this.inputsList, this.inputs);
                        this.resume(formData);
                    });
                }

            },
            resume(formData) {
                this.executionsStore
                    .resume({
                        id: this.execution.id,
                        formData: formData
                    })
                    .then(() => {
                        this.isDrawerOpen = false;
                        this.$toast().success(this.$t("resumed done"));
                    });
            },
            loadDefinition() {
                this.executionsStore.loadFlowForExecution({
                    flowId: this.execution.flowId,
                    namespace: this.execution.namespace,
                    store: true
                });
            },
        },
        computed: {
            ...mapStores(useExecutionsStore, useAuthStore),
            enabled() {
                if (!(this.authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                return State.isPaused(this.execution.state.current);
            },
            inputsList() {
                const findTaskRunByState = ExecutionUtils.findTaskRunsByState(this.execution, State.PAUSED);
                if (findTaskRunByState.length === 0) {
                    return [];
                }

                const findTaskById = FlowUtils.findTaskById(this.executionsStore.flow, findTaskRunByState[0].taskId);

                return findTaskById && findTaskById.inputs !== null ? findTaskById.inputs : [];
            },
            needInputs() {
                return this.inputsList?.length > 0;
            }
        },
    };
</script>
