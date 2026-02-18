<template>
    <el-tooltip
        v-if="isReplay || enabled"
        :placement="tooltipPosition"
        :enterable="false"
        :persistent="false"
        :hideAfter="0"
        :content="tooltip"
        popperClass="ks-restart-tooltip--no-pointer"
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
            {{ $t(replayOrRestart) }}
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
                {{ $t(replayOrRestart) }}
            </component>
        </span>
    </el-tooltip>

    <el-dialog
        v-if="enabled && isOpen"
        v-model="isOpen"
        destroyOnClose
        :appendToBody="true"
        width="600px"
    >
        <template #header>
            <div class="modal-header m-0">
                <h3 class="modal-title">
                    {{ t("replay execution title") }}
                </h3>
                <el-divider />
            </div>
        </template>

        <div class="p-3 pt-0">
            <p class="mb-0">
                {{ t("replay execution description") }}
            </p>
            <Id :value="execution.id" :shrink="false" />

            <h4 class="section-title">
                {{ t("replay using") }}:
            </h4>

            <el-radio-group v-model="replayRevisionMode" class="radio-vertical">
                <el-radio label="original" class="radio-item">
                    {{ t("flow revision original") }}
                </el-radio>
                <el-radio label="latest" class="radio-item">
                    {{ t("flow revision latest") }}
                </el-radio>
                <el-radio label="specific" class="radio-item">
                    {{ t("flow revision specific") }}
                </el-radio>
            </el-radio-group>

            <el-form
                v-if="replayRevisionMode === 'specific' && revisionsOptions?.length"
                class="mt-2"
            >
                <el-form-item>
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

            <h4 class="section-title">
                {{ t("replay inputs") }}:
            </h4>

            <el-radio-group v-model="inputMode" class="radio-vertical">
                <el-radio label="reuse" class="radio-item">
                    {{ t("reuse original inputs") }}
                </el-radio>
                <el-radio label="modify" class="radio-item">
                    {{ t("modify inputs") }}
                </el-radio>
            </el-radio-group>
        </div>

        <template #footer>
            <el-button @click="isOpen = false">
                {{ t("cancel") }}
            </el-button>
            <el-button type="primary" @click="handleReplayExecute">
                {{ t("execute") }}
            </el-button>
        </template>
    </el-dialog>

    <el-dialog
        v-if="isReplayWithInputsOpen"
        v-model="isReplayWithInputsOpen"
        destroyOnClose
        :appendToBody="true"
        width="60%"
    >
        <template #header>
            <span
                v-html="t('replay the execution', {
                    executionId: execution.id,
                    flowId: execution.flowId
                })"
            />
        </template>

        <ReplayWithInputs
            :execution="execution"
            :taskRun="taskRun"
            :revision="revisionsSelected"
            @execution-trigger="closeReplayWithInputsModal"
        />
    </el-dialog>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
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
    import Id from "../../../../Id.vue"
    import {useAxios} from "../../../../../utils/axios"

    defineOptions({inheritAttrs: false})

    const props = defineProps({
        component: {type: String, default: "el-button"},
        isReplay: {type: Boolean, default: false},
        isButton: {type: Boolean, default: true},
        execution: {type: Object, required: true},
        taskRun: {type: Object, required: false, default: undefined},
        attemptIndex: {type: Number, required: false, default: undefined},
        tooltipPosition: {type: String, default: "bottom"},
    })

    const emit = defineEmits(["follow"])

    const {t} = useI18n()
    const toast = useToast()
    const router = useRouter()
    const flowStore = useFlowStore()
    const authStore = useAuthStore()
    const executionsStore = useExecutionsStore()
    const $http = useAxios()

    const isOpen = ref(false)
    const isReplayWithInputsOpen = ref(false)
    const revisionsSelected = ref<number | undefined>(undefined)

    const replayRevisionMode = ref<"original" | "latest" | "specific">("original")
    const inputMode = ref<"reuse" | "modify">("reuse")

    const icon = computed(() => !props.isReplay ? RestartIcon : PlayBoxMultiple)
    const componentClass = computed(() => !props.isReplay ? "restart me-1" : "")
    const replayOrRestart = computed(() => props.isReplay ? "replay" : "restart")

    const revisionsOptions = computed(() =>
        (flowStore.revisions || [])
            .map((revision) => ({
                value: revision.revision,
                text:
                    revision.revision +
                    (revision.revision === props.execution.flowRevision
                        ? ` (${t("current")})`
                        : "")
            }))
            .reverse()
    )

    const enabled = computed(() => {
        const hasPermission = props.isReplay
            ? authStore.user?.isAllowed(permission.EXECUTION, action.CREATE, props.execution.namespace)
            : authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, props.execution.namespace)

        if (!hasPermission) return false

        if (
            props.isReplay &&
            props.taskRun?.attempts &&
            props.taskRun.attempts.length - 1 !== props.attemptIndex
        ) {
            return false
        }

        const isRunning = State.isRunning(props.execution.state.current)
        return props.isReplay ? !isRunning : props.execution.state.current === State.FAILED
    })

    const tooltip = computed(() =>
        props.isReplay
            ? props.taskRun?.id
                ? t("replay from task tooltip", {taskId: props.taskRun.taskId})
                : t("replay from beginning tooltip")
            : t("restart tooltip", {state: props.execution.state.current})
    )

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

    const handleReplayExecute = () => {
        isOpen.value = false

        if (inputMode.value === "modify") {
            openReplayWithInputsDialog()
            return
        }

        if (replayRevisionMode.value === "latest") {
            restartLastRevision()
            return
        }

        if (replayRevisionMode.value === "original") {
            revisionsSelected.value = props.execution.flowRevision
        }

        restart()
    }

    const restart = async () => {
        const method = `${replayOrRestart.value}Execution` as keyof typeof executionsStore
        const response = await (executionsStore[method] as any)({
            executionId: props.execution.id,
            taskRunId: props.taskRun && props.isReplay ? props.taskRun.id : undefined,
            revision: revisionsSelected.value
        })

        const execution =
            response.data.id === props.execution.id
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

        toast.success(t("replayed"))
    }

    watch(isOpen, (newValue) => newValue && loadRevision())
</script>

<style lang="scss">
    .ks-restart-tooltip--no-pointer {
        pointer-events: none;
    }
</style>

<style scoped lang="scss">
.modal-header {
    .modal-title {
        font-size: 16px;
        font-weight: 600;
        margin: 0;
        color: var(--ks-color-text-primary);
    }
}
.execution-description {
    font-size: 13px;
    color: var(--ks-color-text-secondary);
}

.section-title {
    font-size: 14px;
    font-weight: 600;
    margin: 20px 0 12px 0;
    color: var(--ks-color-text-primary);
}

.radio-vertical {
    display: flex;
    flex-direction: column;
    align-items: flex-start; 
}

.modal-header :deep(.el-divider--horizontal) {
    margin-bottom: 8px;
}

.radio-item {
    :deep(.el-radio__input) {
        .el-radio__inner {
            width: 18px; 
            height: 18px; 
            
            &::after {
                width: 8px;
                height: 8px;
                background-color: var(--el-color-primary);
            }
        }
    }
    
    :deep(.el-radio__label) {
        font-size: 13px;
        color: var(--el-text-color-regular);
        padding-left: 8px;
    }
    
    
    &.is-checked {
        :deep(.el-radio__input) {
            .el-radio__inner {
                border-color: var(--el-color-primary);
                background-color: var(--el-color-primary);
                
                &::after {
                    background-color: white;
                }
            }
        }
        
        :deep(.el-radio__label) {
            color: var(--el-text-color-regular) !important;
        }
    }
}
</style>
