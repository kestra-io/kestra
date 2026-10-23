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
                <p>
                    <i18n-t keypath="interrupt confirm" tag="span" scope="global">
                        <template #task><code>{{ taskRun.taskId }}</code></template>
                        <template #id><code>{{ execution.id }}</code></template>
                    </i18n-t>
                    <br>
                    {{ $t("interrupt note") }}
                </p>

                <p>
                    {{ $t('change state current state') }} <KsExecutionStatus size="small" class="status-badge" :status="taskRun.state.current" />
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
                            <KsExecutionStatus size="small" :label="true" class="status-badge" :status="item.code" />
                            <i18n-t keypath="mark as" tag="span" scope="global">
                                <template #status><code>{{ item.code }}</code></template>
                            </i18n-t>
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
    import {useI18n} from "vue-i18n"
    import {useExecutionsStore} from "../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../utils/toast"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {State} from "@kestra-io/design-system"
    import type {Execution} from "../../stores/executions"
    import type {ExecutionTaskRun} from "./executionTaskRun"

    const props = withDefaults(defineProps<{
        component?: string
        execution: Execution
        taskRun: ExecutionTaskRun
        attemptIndex?: number
    }>(), {
        component: "KsButton",
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

    const uuid = computed(() => "interrupt-" + props.execution.id + "-" + props.taskRun.id)

    const states = computed(() => {
        return [
            State.FAILED,
            State.CANCELLED,
        ].map((value: string) => {
            return {
                code: value,
                disabled: false,
            }
        })
    })

    const enabled = computed(() => {
        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace))) {
            return false
        }

        // Only the last attempt is the one still held by a Worker, so only that one can be interrupted.
        if (props.taskRun.attempts !== undefined && props.taskRun.attempts.length - 1 !== props.attemptIndex) {
            return false
        }

        return props.taskRun.state.current === State.RUNNING
    })

    function interrupt() {
        visible.value = false

        executionsStore
            .interrupt({
                executionId: props.execution.id,
                taskRunId: props.taskRun.id,
                state: selectedStatus.value as string,
            })
            .then(() => {
                // The interrupt is applied asynchronously by the Worker: re-follow the stream to pick up the new state.
                emit("follow")

                toast.success(t("interrupt done"))
            })
    }
</script>
<style scoped>
.status-badge {
    margin-inline-end: var(--ks-spacing-1);
}
</style>
