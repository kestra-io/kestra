<template>
    <KsButton
        :disabled="!enabled"
        :icon="SwapHorizontal"
        @click="visible = !visible"
    >
        {{ $t('change state') }}
    </KsButton>

    <KsDialog v-if="enabled && visible" v-model="visible" :id="uuid" destroyOnClose :appendToBody="true">
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>

        <template #default>
            <p v-html="$t('change execution state confirm', {id: execution.id})" />

            <p>
                {{ $t("change state current state") }} <KsExecutionStatus size="small" class="me-1" :status="execution.state.current" />
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
        </template>

        <template #footer>
            <KsButton @click="visible = false">
                {{ $t('cancel') }}
            </KsButton>
            <KsButton
                type="primary"
                @click="changeStatus()"
                :disabled="selectedStatus === execution.state.current || selectedStatus === null"
            >
                {{ $t('ok') }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import {useI18n} from "vue-i18n"

    import SwapHorizontal from "vue-material-design-icons/SwapHorizontal.vue"

    import {State} from "@kestra-io/design-system"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {useToast} from "../../utils/toast"

    import {Execution, useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"

    const props = defineProps<{ execution: Execution }>()

    const emit = defineEmits<{
        follow: [];
    }>()

    const {t} = useI18n({useScope: "global"})
    const toast = useToast()

    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const selectedStatus = ref<string | undefined>(undefined)
    const visible = ref(false)

    const uuid = computed(() => {
        return "changestatus-" + props.execution.id
    })

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
                    disabled: value === props.execution.state.current,
                }
            })
    })

    const enabled = computed(() => {
        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace))) {
            return false
        }

        if (State.isRunning(props.execution.state.current)) {
            return false
        }
        return true
    })

    const changeStatus = async () => {
        visible.value = false

        await executionsStore.changeExecutionStatus({
            executionId: props.execution.id,
            state: selectedStatus.value!,
        })

        const execution = await executionsStore.waitForStateChange(props.execution) as Execution

        executionsStore.execution = execution
        emit("follow")
        toast.success(t("change execution state done"))
    }
</script>

<style lang="scss" scoped>
.alert-status-change {
    ul {
        margin-bottom: 0;
        padding-left: 10px;
    }
}
</style>
