<template>
    <component
        :is="component"
        :icon="icon.StateMachine"
        @click="visible = !visible"
        :disabled="!enabled"
    >
        <span v-if="component !== 'el-button'">{{ $t('change state') }}</span>

        <KsDialog v-if="enabled && visible" v-model="visible" :id="uuid" destroyOnClose :appendToBody="true">
            <template #header>
                <h5>{{ $t("confirmation") }}</h5>
            </template>

            <template #default>
                <p v-html="$t('change state confirm', {id: execution.id, task: taskRun.taskId})" />

                <p>
                    {{ $t('change state current state') }} <KsExecutionStatus size="small" class="me-1" :status="taskRun.state.current" />
                </p>

                <KsSelect
                    :required="true"
                    v-model="selectedStatus"
                >
                    <KsOption
                        v-for="item in states"
                        :key="item.code"
                        :value="item.code"
                        :disabled="item.disabled"
                    >
                        <template #default>
                            <KsExecutionStatus size="small" :label="true" class="me-1" :status="item.code" />
                            <span v-html="item.label" />
                        </template>
                    </KsOption>
                </KsSelect>

                <div v-if="selectedStatus" class="alert alert-info alert-status-change mt-2" role="alert">
                    <ul>
                        <li v-for="(text, i) in $t('change status hint')[selectedStatus]" :key="i">
                            {{ text }}
                        </li>
                    </ul>
                </div>
            </template>

            <template #footer>
                <KsButton @click="visible = false">
                    {{ $t('cancel') }}
                </KsButton>
                <KsButton
                    type="primary"
                    @click="changeStatus()"
                    :disabled="selectedStatus === taskRun.state.current || selectedStatus === null"
                >
                    {{ $t('ok') }}
                </KsButton>
            </template>
        </KsDialog>
    </component>
</template>

<script>
    import StateMachine from "vue-material-design-icons/StateMachine.vue";
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";
    import resource from "../../models/resource";
    import action from "../../models/action";
    import {State} from "@kestra-io/design-system"
    import {shallowRef, ref} from "vue";
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast";
    import {useI18n} from "vue-i18n";

    export default {
        components: {StateMachine},
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
        setup(props, {emit}) {
            const visible = ref(false);
            const selectedStatus = ref(undefined);

            const {t} = useI18n();

            const executionsStore = useExecutionsStore();
            const toast = useToast();

            function changeStatus() {
                visible.value = false;

                executionsStore
                    .changeStatus({
                        executionId: props.execution.id,
                        taskRunId: props.taskRun.id,
                        state: selectedStatus.value
                    })
                    .then(() => executionsStore.waitForStateChange(props.execution))
                    .then((execution) => {
                        executionsStore.execution = execution;
                        emit("follow")

                        toast.success(t("change state done"));
                    })
            }

            return {
                visible,
                selectedStatus,
                changeStatus
            }
        },
        computed: {
            ...mapStores(useAuthStore),
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
                if (!(this.authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                if (this.taskRun.attempts !== undefined && this.taskRun.attempts.length - 1 !== this.attemptIndex) {
                    return false;
                }

                if (this.taskRun.state.current === "PAUSED" || this.taskRun.state.current === "CREATED") {
                    return true;
                }

                if (State.isRunning(this.execution.state.current)) {
                    return false;
                }

                return true;
            }
        },
        data() {
            return {
                icon: {StateMachine: shallowRef(StateMachine)}
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