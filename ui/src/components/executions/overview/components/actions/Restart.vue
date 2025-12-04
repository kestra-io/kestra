<template>
    <el-tooltip
        v-if="isReplay || enabled"
        :placement="tooltipPosition"
        :persistent="false"
        :hideAfter="0"
        :content="tooltip"
        rawContent
        transition=""
        effect="light"
    >
        <component
            v-if="component !== 'el-dropdown-item'"
            v-bind="$attrs"
            :is="component"
            :icon="icon"
            :disabled="!enabled"
            :class="componentClass"
            @click="isOpen = !isOpen"
        >
            {{ t(replayOrRestart) }}
        </component>
        <span v-else-if="component === 'el-dropdown-item'">
            <component
                v-bind="$attrs"
                :is="component"
                :icon="icon"
                :disabled="!enabled"
                :class="componentClass"
                @click="isOpen = !isOpen"
            >
                {{ t(replayOrRestart) }}
            </component>
        </span>
    </el-tooltip>
    <el-dialog v-if="enabled && isOpen" v-model="isOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <h5>{{ t("confirmation") }}</h5>
        </template>

        <template #footer>
            <el-button @click="isOpen = false">
                {{ t('cancel') }}
            </el-button>
            <el-button v-if="isReplay && hasInputs" @click="openReplayWithInputsDialog" type="default" :icon="PlayBoxMultiple">
                {{ t('replay with inputs') }}
            </el-button>
            <el-button @click="restartLastRevision()">
                {{ buttonText }}
            </el-button>
            <el-button type="primary" @click="restart()">
                {{ t('ok') }}
            </el-button>
        </template>

        <p v-html="confirmText" />

        <el-form v-if="revisionsOptions && revisionsOptions.length > 1">
            <p class="execution-description">
                {{ t("restart change revision") }}
            </p>
            <el-form-item :label="t('revisions')">
                <el-select v-model="revisionsSelected">
                    <el-option
                        v-for="item in revisionsOptions"
                        :key="item.value"
                        :label="item.text"
                        :value="item.value"
                    />
                </el-select>
            </el-form-item>
        </el-form>
    </el-dialog>

    <el-dialog v-if="isReplayWithInputsOpen" v-model="isReplayWithInputsOpen" destroyOnClose :appendToBody="true" width="60%">
        <template #header>
            <span v-html="t('replay the execution', {executionId: execution.id, flowId: execution.flowId})" />
        </template>
        <ReplayWithInputs
            :execution
            :taskRun="taskRun"
            :revision="revisionsSelected"
            @execution-trigger="closeReplayWithInputsModal"
        />
    </el-dialog>
</template>

<script setup lang="ts">
    import {ref, computed, watch, getCurrentInstance} from "vue"
    import {useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {useToast} from "../../../../../utils/toast"
    import {State} from "@kestra-io/ui-libs"
    import {useFlowStore} from "../../../../../stores/flow"
    import {useAuthStore} from "override/stores/auth"
    import {useExecutionsStore} from "../../../../../stores/executions"
    import action from "../../../../../models/action"
    import permission from "../../../../../models/permission"
    import * as ExecutionUtils from "../../../../../utils/executionUtils"
    import ReplayWithInputs from "../../../ReplayWithInputs.vue"
    import RestartIcon from "vue-material-design-icons/Restart.vue"
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue"

    const props = defineProps({
        component: {type: String, default: "el-button"},
        isReplay: {type: Boolean, default: false},
        isButton: {type: Boolean, default: true},
        execution: {type: Object, required: true},
        taskRun: {type: Object, required: false, default: undefined},
        attemptIndex: {type: Number, required: false, default: undefined},
        tooltipPosition: {type: String, default: "bottom"}
    })

    const emit = defineEmits(["follow"])

    const {t} = useI18n()
    const toast = useToast()
    const router = useRouter()
    const flowStore = useFlowStore()
    const authStore = useAuthStore()
    const instance = getCurrentInstance()
    const executionsStore = useExecutionsStore()
    const $http = instance ? (instance.proxy as any).$http : null

    const isOpen = ref(false)
    const isReplayWithInputsOpen = ref(false)
    const revisionsSelected = ref<number | undefined>(undefined)

    const icon = computed(() => !props.isReplay ? RestartIcon : PlayBoxMultiple)
    const hasInputs = computed(() => executionsStore.flow?.inputs?.length > 0)
    const buttonText = computed(() => t(`${replayOrRestart.value} latest revision`))
    const confirmText = computed(() => t(`${replayOrRestart.value} confirm`, {id: props.execution.id}))
    const componentClass = computed(() => !props.isReplay ? "restart me-1" : "")
    const replayOrRestart = computed(() => props.isReplay ? "replay" : "restart")

    const revisionsOptions = computed(() =>
        (flowStore.revisions || [])
            .map((revision) => ({
                value: revision.revision,
                text: revision.revision + (sameRevision(revision.revision) ? ` (${t("current")})` : ""),
            }))
            .reverse()
    )

    const enabled = computed(() => {
        const hasPermission = props.isReplay
            ? authStore.user?.isAllowed(permission.EXECUTION, action.CREATE, props.execution.namespace)
            : authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, props.execution.namespace)

        if (!hasPermission) return false

        if (props.isReplay && props.taskRun?.attempts && props.taskRun.attempts.length - 1 !== props.attemptIndex) {
            return false
        }

        const isRunning = State.isRunning(props.execution.state.current)
        return props.isReplay ? !isRunning : props.execution.state.current === State.FAILED
    })

    const tooltip = computed(() =>
        props.isReplay
            ? (props.taskRun?.id
                ? t("replay from task tooltip", {taskId: props.taskRun.taskId})
                : t("replay from beginning tooltip"))
            : t("restart tooltip", {state: props.execution.state.current})
    )


    const sameRevision = (revision?: number) => revision === props.execution.flowRevision

    const openReplayWithInputsDialog = () => {
        isOpen.value = false
        loadFlowForReplay()
    }

    const closeReplayWithInputsModal = () => {
        isReplayWithInputsOpen.value = false
    }

    const loadFlowForReplay = async () => {
        await executionsStore.loadFlowForExecution({
            flowId: props.execution.flowId,
            namespace: props.execution.namespace,
            store: true
        })
        isReplayWithInputsOpen.value = true
    }

    const loadRevision = () => {
        revisionsSelected.value = props.execution.flowRevision
        flowStore.loadRevisions({
            namespace: props.execution.namespace,
            id: props.execution.flowId
        })
    }

    const restartLastRevision = () => {
        if (flowStore.revisions?.length) {
            revisionsSelected.value = flowStore.revisions[flowStore.revisions.length - 1].revision
        }
        restart()
    }

    const restart = async () => {
        isOpen.value = false

        const method = `${replayOrRestart.value}Execution` as keyof typeof executionsStore
        const response = await (executionsStore[method] as any)({
            executionId: props.execution.id,
            taskRunId: props.taskRun && props.isReplay ? props.taskRun.id : undefined,
            revision: sameRevision(revisionsSelected.value) ? undefined : revisionsSelected.value
        })

        const execution = response.data.id === props.execution.id && $http
            ? await ExecutionUtils.waitForState($http, response.data)
            : response.data

        executionsStore.execution = execution

        if (execution.id === props.execution.id) {
            emit("follow")
        } else {
            router.push({
                name: "executions/update",
                params: {
                    namespace: execution.namespace,
                    flowId: execution.flowId,
                    id: execution.id,
                    tab: "gantt",
                    tenant: router.currentRoute.value.params.tenant
                }
            })
        }

        toast.success(t(`${replayOrRestart.value}ed`))
    }

    watch(isOpen, (newValue) => newValue && loadRevision())
</script>

<style scoped lang="scss">
.execution-description {
    color: var(--ks-content-secondary);
}
</style>