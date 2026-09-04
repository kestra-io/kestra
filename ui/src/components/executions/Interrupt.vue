<template>
    <component
        :is="component"
        :icon="Stop"
        @click="visible = !visible"
        :disabled="!enabled"
    >
        <span v-if="component !== 'KsButton'">{{ $t('interrupt') }}</span>

        <KsDialog v-if="enabled && visible" v-model="visible" :id="uuid" destroyOnClose :appendToBody="true">
            <template #header>
                <h5>{{ $t("confirmation") }}</h5>
            </template>

            <template #default>
                <p v-html="$t('interrupt confirm', {id: escape(execution.id), task: escape(taskRun.taskId)})" />

                <p>
                    {{ $t('change state current state') }} <KsExecutionStatus size="small" class="me-1" :status="taskRun.state.current" />
                </p>

                <KsSelect
                    :required="true"
                    v-model="selectedStatus"
                    :placeholder="$t('interrupt target state')"
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
                    @click="interrupt()"
                    :disabled="selectedStatus === null || selectedStatus === undefined"
                >
                    {{ $t('ok') }}
                </KsButton>
            </template>
        </KsDialog>
    </component>
</template>

<script setup lang="ts">
    import Stop from "vue-material-design-icons/Stop.vue"
    import {computed, ref} from "vue"
    import escape from "lodash/escape"
    import {useI18n} from "vue-i18n"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {State} from "@kestra-io/design-system"

    const props = withDefaults(defineProps<{
        component?: string
        execution: any
        taskRun?: any
        attemptIndex?: number
    }>(), {
        component: "KsButton",
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
        "interrupt-" + (props.execution as {id: string}).id + (props.taskRun ? "-" + (props.taskRun as {id: string}).id : ""),
    )

    const states = computed(() => {
        return [
            State.FAILED,
            State.CANCELLED,
        ].map((value: string) => {
            return {
                code: value,
                label: t("mark as", {status: value}),
                disabled: false,
            }
        })
    })

    const enabled = computed(() => {
        const execution = props.execution as any
        const taskRun = props.taskRun as any

        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, execution.namespace))) {
            return false
        }

        if (taskRun.attempts !== undefined && taskRun.attempts.length - 1 !== props.attemptIndex) {
            return false
        }

        return taskRun.state.current === "RUNNING"
    })

    function interrupt() {
        visible.value = false

        const taskRun = props.taskRun as any
        executionsStore
            .interrupt({
                executionId: (props.execution as {id: string}).id,
                taskRunId: taskRun.id,
                state: selectedStatus.value as string,
            })
            .then((execution: unknown) => {
                ;(executionsStore as any).execution = execution
                emit("follow")

                toast.success(t("interrupt done"))
            })
    }
</script>
