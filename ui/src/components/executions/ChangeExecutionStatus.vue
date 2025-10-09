<template>
    <el-tooltip
        effect="light"
        :persistent="false"
        transition=""
        :hideAfter="0"
        :content="$t('change state tooltip')"
        rawContent
        :placement="tooltipPosition"
    >
        <component
            :is="component"
            :icon="StateMachine"
            @click="visible = !visible"
            :disabled="!enabled"
            class="ms-0 me-1"
        >
            {{ $t('change state') }}
        </component>
    </el-tooltip>

    <el-dialog v-if="enabled && visible" v-model="visible" :id="uuid" destroyOnClose :appendToBody="true">
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>

        <template #default>
            <p v-html="$t('change execution state confirm', {id: execution.id})" />

            <p>
                {{ $t("change state current state") }} <Status size="small" class="me-1" :status="execution.state.current" />
            </p>

            <el-select
                :required="true"
                v-model="selectedStatus"
                :persistent="false"
            >
                <el-option
                    v-for="item in states"
                    :key="item.code"
                    :value="item.code"
                    :disabled="item.disabled"
                >
                    <template #default>
                        <Status size="small" :label="true" class="me-1" :status="item.code" />
                        <span v-html="item.label" />
                    </template>
                </el-option>
            </el-select>
        </template>

        <template #footer>
            <el-button @click="visible = false">
                {{ $t('cancel') }}
            </el-button>
            <el-button
                type="primary"
                @click="changeStatus()"
                :disabled="selectedStatus === execution.state.current || selectedStatus === null"
            >
                {{ $t('ok') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useStore} from "vuex";
    import {useRouter, useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import StateMachine from "vue-material-design-icons/StateMachine.vue";

</script>

<script>
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State} from "@kestra-io/ui-libs";
    import Status from "../../components/Status.vue";
    import ExecutionUtils from "../../utils/executionUtils";
    import {useToast} from "../../utils/toast";

    import * as ExecutionUtils from "../../utils/executionUtils";
    import {useAuthStore} from "override/stores/auth"

    interface ExecutionLike {
        id: string;
        namespace: string;
        flowId: string;
        state: { current: string; histories?: unknown[] };
    }

    const props = defineProps({
        component: {
            type: String,
            default: "el-button",
        },
        execution: {
            type: Object as () => ExecutionLike,
            required: true,
        },
        tooltipPosition: {
            type: String,
            default: "bottom",
        },
    });

    const emit = defineEmits<{(e: "follow"): void}>();

    const store = useStore();
    const router = useRouter();
    const route = useRoute();
    const {t} = useI18n({useScope: "global"});
    const toast = useToast();

    const visible = ref(false);
    const selectedStatus = ref<string | null | undefined>(undefined);

    const user = computed(() => store.state.auth.user);

        emits: ["follow"],
        methods: {
            changeStatus() {
                this.visible = false;

                this.executionsStore
                    .changeExecutionStatus({
                        executionId: this.execution.id,
                        state: this.selectedStatus
                    })
                    .then(response => {
                        if (response.data.id === this.execution.id) {
                            return ExecutionUtils.waitForState(this.$http, response.data);
                        } else {
                            return response.data;
                        }
                    })
                    .then((execution) => {
                        this.executionsStore.execution = execution;
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
                                }
                            });
                        }

                        this.$toast().success(this.$t("change execution state done"));
                    })
            },
        },
        computed: {
            ...mapStores(useExecutionsStore, useAuthStore),
            uuid() {
                return "changestatus-" + this.execution.id;
            },
            states() {
                return (this.execution.state.current === "PAUSED" ?
                    [
                        State.FAILED,
                        State.RUNNING,
                        State.CANCELLED,
                    ] :
                    [
                        State.FAILED,
                        State.SUCCESS,
                        State.WARNING,
                        State.CANCELLED,
                    ]
                )
                    .filter(value => value !== this.execution.state.current)
                    .map(value => {
                        return {
                            code: value,
                            label: this.$t("mark as", {status: value}),
                            disabled: value === this.execution.state.current
                        };
                    })
            },
            enabled() {
                if (!(this.authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

    const uuid = computed(() => "changestatus-" + props.execution.id);

    const states = computed(() => {
        const list = (props.execution.state.current === "PAUSED"
                ? [State.FAILED, State.RUNNING, State.CANCELLED]
                : [State.FAILED, State.SUCCESS, State.WARNING, State.CANCELLED]
        )
            .filter((value: string) => value !== props.execution.state.current)
            .map((value: string) => ({
                code: value,
                label: t("mark as", {status: value}),
                disabled: value === props.execution.state.current,
            }));

        return list as Array<{ code: string; label: string; disabled: boolean }>;
    });

    const enabled = computed(() => {
        if (!(user.value && user.value.isAllowed(permission.EXECUTION, action.UPDATE, props.execution.namespace))) {
            return false;
        }
        if (State.isRunning(props.execution.state.current)) {
            return false;
        }
        return true;
    });

    const changeStatus = () => {
        visible.value = false;

        store
            .dispatch("execution/changeExecutionStatus", {
                executionId: props.execution.id,
                state: selectedStatus.value,
            })
            .then((response: any) => {
                if (response.data.id === props.execution.id) {
                    const http = (store as any).$http;
                    return ExecutionUtils.waitForState(http, store, response.data);
                } else {
                    return response.data;
                }
            })
            .then((execution: ExecutionLike) => {
                store.commit("execution/setExecution", execution);
                if (execution.id === props.execution.id) {
                    emit("follow");
                } else {
                    router.push({
                        name: "executions/update",
                        params: {
                            namespace: execution.namespace,
                            flowId: execution.flowId,
                            id: execution.id,
                            tab: "gantt",
                            tenant: (route.params as any).tenant,
                        },
                    });
                }

                toast.success(t("change execution state done"));
            });
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