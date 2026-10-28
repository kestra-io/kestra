<template>
    <component
        :is="component"
        :icon="StateMachine"
        @click="visible = !visible"
        :disabled="!enabled"
    >
        <span v-if="component !== 'KsButton'">{{ $t('change state') }}</span>

        <KsDialog v-if="enabled && visible" v-model="visible" :id="uuid" destroyOnClose :appendToBody="true">
            <template #header>
                <h5>{{ $t("confirmation") }}</h5>
            </template>

            <template #default>
                <p>
                    <i18n-t keypath="change state confirm" tag="span" scope="global">
                        <template #task><code>{{ taskRun?.taskId ?? '' }}</code></template>
                        <template #id><code>{{ execution.id }}</code></template>
                    </i18n-t>
                    <br>
                    {{ $t('change state confirm restart') }}
                </p>

                <p>
                    {{ $t('change state current state') }} <KsExecutionStatus size="small" class="me-1" :status="taskRun?.state?.current ?? ''" />
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
                            <i18n-t keypath="mark as" tag="span" scope="global">
                                <template #status><code>{{ item.code }}</code></template>
                            </i18n-t>
                        </template>
                    </KsOption>
                </KsSelect>

                <div v-if="selectedStatus" class="alert alert-info alert-status-change mt-2" role="alert">
                    <ul>
                        <li v-for="(text, i) in ($tm('change state hint') as Record<string, string[]>)[selectedStatus]" :key="i">
                            {{ $rt(text) }}
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
                    :disabled="selectedStatus === taskRun?.state?.current || selectedStatus === null"
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
    import {type Execution, useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {State} from "@kestra-io/design-system"
    import type {TaskRun} from "@kestra-io/kestra-sdk"

    const props = withDefaults(defineProps<{
        component?: string
        execution: Execution
        taskRun?: TaskRun
        attemptIndex?: number
    }>(), {
        component: "KsButton",
        taskRun: undefined,
        attemptIndex: undefined,
    })

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()
    const toast = useToast()

    const visible = ref(false)
    const selectedStatus = ref<string | undefined>(undefined)

    const uuid = computed(() =>
        "changestatus-" + props.execution.id + (props.taskRun ? "-" + props.taskRun.id : ""),
    )

    const states = computed(() => {
        const taskRun = props.taskRun
        if (!taskRun?.state?.current) {
            return []
        }
        return [
            State.FAILED,
            State.SUCCESS,
            State.WARNING,
        ]
            .filter((value: string) => value !== taskRun.state.current)
            .map((value: string) => ({code: value, disabled: value === taskRun.state.current}))
    })

    const enabled = computed(() => {
        const execution = props.execution
        const taskRun = props.taskRun

        if (!execution?.namespace || !taskRun?.state?.current) {
            return false
        }

        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, execution.namespace))) {
            return false
        }

        if (taskRun.attempts !== undefined && taskRun.attempts.length - 1 !== props.attemptIndex) {
            return false
        }

        return State.isTerminated(execution.state.current) && execution.state.current !== State.KILLED
    })

    function changeStatus() {
        visible.value = false

        const taskRun = props.taskRun
        if (!taskRun || !selectedStatus.value) {
            return
        }

        executionsStore
            .changeStatus({
                executionId: props.execution.id,
                taskRunId: taskRun.id,
                state: selectedStatus.value,
            })
            .then(() => executionsStore.waitForStateChange(props.execution))
            .then((execution: Execution) => {
                executionsStore.execution = execution
                // Re-subscribe to the execution SSE stream directly via the store
                // instead of bubbling a `follow` event up to the route component.
                executionsStore.followExecution({id: props.execution.id}, t)

                toast.success(t("change state done"))
            })
    }
</script>

<style scoped>
    .alert-status-change {
        ul {
            margin-bottom: 0;
            padding-left: 10px;
        }
    }
</style>