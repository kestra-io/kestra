<template>
    <el-button
        :disabled="!enabled"
        :icon="SwapHorizontal"
        @click="visible = !visible"
    >
        {{ $t('change state') }}
    </el-button>

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
    import {ref, computed} from "vue";
    import {useRouter, useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";

    import SwapHorizontal from "vue-material-design-icons/SwapHorizontal.vue";

    import {State, Status} from "@kestra-io/ui-libs";
    import * as ExecutionUtils from "../../utils/executionUtils";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {useToast} from "../../utils/toast";
    import {useAxios} from "../../utils/axios";

    import {Execution, useExecutionsStore} from "../../stores/executions";
    import {useAuthStore} from "override/stores/auth";

    const props = defineProps<{ execution: Execution }>();

    const emit = defineEmits<{
        follow: [];
    }>();

    const {t} = useI18n({useScope: "global"});
    const toast = useToast();
    const router = useRouter();
    const route = useRoute();
    const axios = useAxios();

    const executionsStore = useExecutionsStore();
    const authStore = useAuthStore();

    const selectedStatus = ref<string | undefined>(undefined);
    const visible = ref(false);

    const uuid = computed(() => {
        return "changestatus-" + props.execution.id;
    });

    const states = computed(() => {
        return (props.execution.state.current === "PAUSED" ?
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
            .filter(value => value !== props.execution.state.current)
            .map(value => {
                return {
                    code: value,
                    label: t("mark as", {status: value}),
                    disabled: value === props.execution.state.current
                };
            });
    });

    const enabled = computed(() => {
        if (!(authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, props.execution.namespace))) {
            return false;
        }

        if (State.isRunning(props.execution.state.current)) {
            return false;
        }
        return true;
    });

    const changeStatus = async () => {
        visible.value = false;

        const response = await executionsStore.changeExecutionStatus({
            executionId: props.execution.id,
            state: selectedStatus.value!
        });

        let execution;
        if (response.data.id === props.execution.id) {
            execution = await ExecutionUtils.waitForState(axios, response.data);
        } else {
            execution = response.data;
        }

        executionsStore.execution = execution;
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
                    tenant: route.params.tenant
                }
            });
        }
        toast.success(t("change execution state done"));
    };
</script>

<style lang="scss" scoped>
.alert-status-change {
    ul {
        margin-bottom: 0;
        padding-left: 10px;
    }
}
</style>