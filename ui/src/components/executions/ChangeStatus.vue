<template>
    <component
        :is="component"
        :icon="StateMachine"
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
                        <li v-for="(text, i) in ($t('change status hint') as any)[selectedStatus]" :key="i">
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

<script setup lang="ts">
    import StateMachine from "vue-material-design-icons/StateMachine.vue"
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {State} from "@kestra-io/design-system"

    // FIXME: any - execution/taskRun are untyped domain objects
    const props = withDefaults(defineProps<{
        component?: string
        execution: any // FIXME: any
        taskRun?: any // FIXME: any
        attemptIndex?: number
    }>(), {
        component: "b-button",
        taskRun: undefined,
        attemptIndex: undefined,
    })

    const emit = defineEmits<{
        follow: []
    }>()

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()
    const toast = useToast()

    const visible = ref(false)
    const selectedStatus = ref<string | undefined>(undefined)

    const uuid = computed(() =>
        "changestatus-" + (props.execution as {id: string}).id + (props.taskRun ? "-" + (props.taskRun as {id: string}).id : ""),
    )

    // FIXME: any - execution/taskRun are untyped domain objects
    const states = computed(() => {
        const taskRun = props.taskRun as any // FIXME: any
        return (taskRun.state.current === "PAUSED" ?
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
            .filter((value: string) => value !== taskRun.state.current)
            .map((value: string) => {
                return {
                    code: value,
                    label: t("mark as", {status: value}),
                    disabled: value === taskRun.state.current,
                }
            })
    })

    const enabled = computed(() => {
        const execution = props.execution as any // FIXME: any
        const taskRun = props.taskRun as any // FIXME: any

        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, execution.namespace))) {
            return false
        }

        if (taskRun.attempts !== undefined && taskRun.attempts.length - 1 !== props.attemptIndex) {
            return false
        }

        if (taskRun.state.current === "PAUSED" || taskRun.state.current === "CREATED") {
            return true
        }

        if (State.isRunning(execution.state.current)) {
            return false
        }

        return true
    })

    function changeStatus() {
        visible.value = false

        const taskRun = props.taskRun as any // FIXME: any
        executionsStore
            .changeStatus({
                executionId: (props.execution as {id: string}).id,
                taskRunId: taskRun.id,
                state: selectedStatus.value as string,
            })
            .then(() => executionsStore.waitForStateChange(props.execution as any)) // FIXME: any
            .then((execution: unknown) => {
                // FIXME: any
                ;(executionsStore as any).execution = execution // FIXME: any
                emit("follow")

                toast.success(t("change state done"))
            })
    }
</script>

<style lang="scss">
    .alert-status-change {
        ul {
            margin-bottom: 0;
            padding-left: 10px;
        }
    }
</style>