<template>
    <component
        :is="component"
        :icon="iconStateMachine"
        @click="visible = !visible"
        :disabled="!enabled"
    >
        <span v-if="component !== 'el-button'">{{ t('change state') }}</span>

        <KsDialog v-if="enabled && visible" v-model="visible" :id="uuid" destroyOnClose :appendToBody="true">
            <template #header>
                <h5>{{ t("confirmation") }}</h5>
            </template>

            <template #default>
                <template v-if="taskRun">
                    <p v-html="t('change state confirm', {id: execution.id, task: taskRun.taskId})" />

                    <p>
                        {{ t('change state current state') }} <KsExecutionStatus size="small" class="me-1" :status="taskRun.state.current" />
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
                            <li v-for="(text, i) in (t('change status hint') as any)[selectedStatus]" :key="i">
                                {{ text }}
                            </li>
                        </ul>
                    </div>
                </template>
            </template>

            <template #footer>
                <KsButton @click="visible = false">
                    {{ t('cancel') }}
                </KsButton>
                <KsButton
                    type="primary"
                    @click="changeStatus()"
                    :disabled="selectedStatus === taskRun?.state.current || selectedStatus === null"
                >
                    {{ t('ok') }}
                </KsButton>
            </template>
        </KsDialog>
    </component>
</template>

<script setup lang="ts">
    import {ref, computed, shallowRef} from "vue"
    import {useI18n} from "vue-i18n"
    import StateMachine from "vue-material-design-icons/StateMachine.vue"
    import {State} from "@kestra-io/design-system"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"
    import resource from "../../models/resource"
    import action from "../../models/action"

    const props = defineProps<{
        component?: string
        execution: Record<string, // FIXME: type this properly
                          any>
        taskRun?: Record<string, // FIXME: type this properly
                         any>
        attemptIndex?: number
    }>()

    const emit = defineEmits<{
        follow: []
    }>()

    const {t} = useI18n({useScope: "global"})
    const toast = useToast()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const iconStateMachine = shallowRef(StateMachine)
    const visible = ref(false)
    const selectedStatus = ref<string | undefined>(undefined)

    const uuid = computed(() =>
        "changestatus-" + props.execution.id + (props.taskRun ? "-" + props.taskRun.id : ""),
    )

    const states = computed(() =>
        (props.taskRun!.state.current === "PAUSED" ?
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
            .filter(value => value !== props.taskRun!.state.current)
            .map(value => ({
                code: value,
                label: t("mark as", {status: value}),
                disabled: value === props.taskRun!.state.current,
            })),
    )

    const enabled = computed(() => {
        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace))) {
            return false
        }

        if (props.taskRun!.attempts !== undefined && props.taskRun!.attempts.length - 1 !== props.attemptIndex) {
            return false
        }

        if (props.taskRun!.state.current === "PAUSED" || props.taskRun!.state.current === "CREATED") {
            return true
        }

        if (State.isRunning(props.execution.state.current)) {
            return false
        }

        return true
    })

    function changeStatus() {
        visible.value = false

        executionsStore
            .changeStatus({
                executionId: props.execution.id,
                taskRunId: props.taskRun!.id,
                state: selectedStatus.value as string,
            })
            .then(() => executionsStore.waitForStateChange(props.execution as any))
            .then((execution: Record<string, any>) => {
                executionsStore.execution = execution as any
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
