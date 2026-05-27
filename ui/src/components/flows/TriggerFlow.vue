<template>
    <div class="trigger-flow-wrapper">
        <span data-onboarding-target="flow-execute-button">
            <KsButton
                v-if="iconOnly"
                :id="actionId"
                class="execute-icon-only"
                type="success"
                :icon="PlayOutlineIcon"
                :disabled="actionDisabled"
                :aria-label="actionLabel"
                @click="runAction()"
            />
            <KsButton
                v-else
                id="execute-button"
                :icon="PlayOutlineIcon"
                :type="type"
                :disabled="isDisabled()"
                @click="onClick()"
            >
                {{ t("execute") }}
            </KsButton>
        </span>
        <KsDialog
            id="execute-flow-dialog"
            v-model="isOpen"
            destroyOnClose
            :showClose="true"
            :beforeClose="beforeClose"
            :appendToBody="true"
            :width="dialogWidth"
        >
            <template #header>
                <span v-html="t('execute the flow', {id: flowId})" />
            </template>
            <FlowRun @execution-trigger="handleExecutionStart" :redirect="!playgroundStore.enabled" />
        </KsDialog>
        <KsDialog
            v-if="isSelectFlowOpen"
            v-model="isSelectFlowOpen"
            destroyOnClose
            :beforeClose="() => reset()"
            :appendToBody="true"
            :width="dialogWidth"
        >
            <KsForm
                labelPosition="top"
            >
                <KsFormItem :label="t('namespace')">
                    <KsSelect
                        v-model="localNamespace"
                    >
                        <KsOption
                            v-for="np in executionsStore.namespaces"
                            :key="np"
                            :label="np"
                            :value="np"
                        />
                    </KsSelect>
                </KsFormItem>
                <KsFormItem
                    v-if="localNamespace && executionsStore.flowsExecutable.length > 0"
                    :label="t('flow')"
                >
                    <KsSelect
                        v-model="localFlow"
                        valueKey="id"
                    >
                        <KsOption
                            v-for="exFlow in executionsStore.flowsExecutable"
                            :key="exFlow.id"
                            :label="exFlow.id"
                            :value="exFlow"
                        />
                    </KsSelect>
                </KsFormItem>
                <KsFormItem v-if="localFlow" :label="t('inputs')">
                    <div class="w-100">
                        <FlowRun @execution-trigger="handleExecutionStart" :redirect="!playgroundStore.enabled" />
                    </div>
                </KsFormItem>
            </KsForm>
        </KsDialog>
    </div>
</template>


<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useMediaQuery} from "@vueuse/core"
    import {useI18n} from "vue-i18n"
    import {useToast} from "../../utils/toast"
    import {useApiStore} from "../../stores/api"
    import {useExecutionsStore} from "../../stores/executions"
    import {usePlaygroundStore} from "../../stores/playground"
    import {useFlowStore} from "../../stores/flow"
    import FlowRun from "./FlowRun.vue"
    import FlowWarningDialog from "./FlowWarningDialog.vue"
    import PlayOutlineIcon from "vue-material-design-icons/PlayOutline.vue"

    interface ExecutableFlow {
        id: string
        deleted?: boolean
        [key: string]: unknown
    }

    const props = withDefaults(defineProps<{
        flowId?: string
        namespace?: string
        disabled?: boolean
        type?: "default" | "primary" | "success" | "warning" | "info" | "danger" | "text" | ""
        flowSource?: string | null
        iconOnly?: boolean
    }>(), {
        disabled: false,
        type: "primary",
        flowSource: null,
        iconOnly: false,
    })

    const {t} = useI18n({useScope: "global"})
    const toast = useToast()
    const apiStore = useApiStore()
    const executionsStore = useExecutionsStore()
    const playgroundStore = usePlaygroundStore()
    const flowStore = useFlowStore()

    const isOpen = ref(false)
    const isSelectFlowOpen = ref(false)
    const localFlow = ref<ExecutableFlow | undefined>(undefined)
    const localNamespace = ref<string | undefined>(undefined)
    const isLargeScreen = useMediaQuery("(min-width: 768px)")

    function trackExecutionAction(action: string) {
        apiStore.posthogEvents({
            type: "FLOW_EXECUTION",
            action,
        })
    }

    async function handleExecutionStart() {
        closeModal()
        toast.success(t("execution_started"))
    }

    function isDisabled() {
        return props.disabled || executionsStore.flow?.deleted
    }

    async function loadDefinition() {
        await executionsStore.loadFlowForExecution({
            flowId: props.flowId!,
            namespace: props.namespace!,
            store: true,
        })
    }

    function closeModal() {
        isOpen.value = false
    }

    function reset() {
        isOpen.value = false
        isSelectFlowOpen.value = false
        localFlow.value = undefined
        localNamespace.value = undefined
    }

    function beforeClose(done: () => void) {
        reset()
        done()
    }

    async function toggleModal(newValue?: boolean) {
        if (newValue === undefined) {
            newValue = !isOpen.value
        }
        if (newValue) {
            // wait for flow to be set before opening the dialog
            await loadDefinition()
        }
        isOpen.value = newValue
    }

    function onClick() {
        trackExecutionAction("open_modal")
        if (checkForTrigger.value) {
            toast.confirm(FlowWarningDialog as unknown as string, () => toggleModal(), true as unknown as "warning", null as unknown as boolean)
        } else if (computedNamespace.value !== undefined && computedFlowId.value !== undefined) {
            toggleModal(true)
        } else {
            executionsStore.loadNamespaces()
            isSelectFlowOpen.value = !isSelectFlowOpen.value
        }
    }

    function runAction() {
        if (playgroundStore.enabled) {
            playgroundStore.runUntilTask()
        } else {
            onClick()
        }
    }

    const actionId = computed(() =>
        playgroundStore.enabled ? "run-all-button" : "execute-button",
    )

    const actionDisabled = computed(() => {
        if (playgroundStore.enabled) {
            return isDisabled() || !playgroundStore.readyToStart
        }
        return isDisabled()
    })

    const actionLabel = computed(() =>
        playgroundStore.enabled
            ? t("playground.run_all_tasks")
            : t("execute"),
    )

    const dialogWidth = computed(() =>
        isLargeScreen.value ? "50%" : "90%",
    )

    const computedFlowId = computed(() =>
        props.flowId ?? localFlow.value?.id,
    )

    const computedNamespace = computed(() =>
        props.namespace ?? localNamespace.value,
    )

    const checkForTrigger = computed(() => {
        if (props.flowSource) {
            const triggerRegex = /\{\{\s*\(?\s*(\|\||&&)?\s*trigger\s*(\.\w+|\|\s*\w+)?\s*\}\}/
            return triggerRegex.test(props.flowSource)
        }
        return false
    })

    watch(
        () => flowStore.executeFlow,
        (value) => {
            if (value && !isDisabled()) {
                flowStore.executeFlow = false
                onClick()
            }
        },
    )

    watch(
        () => props.flowId,
        () => {
            if (!props.flowId) {
                return
            }
            
            loadDefinition()
        },
        {immediate: true},
    )

    watch(
        localNamespace,
        () => {
            if (!localNamespace.value) {
                return
            }
            executionsStore.loadFlowsExecutable({
                namespace: localNamespace.value,
            })
        },
        {immediate: true},
    )

    watch(
        localFlow,
        () => {
            if (!localFlow.value) {
                return
            }
            executionsStore.flow = localFlow.value
        },
        {immediate: true},
    )
</script>

<style scoped>
    .trigger-flow-wrapper {
        display: inline;
    }

    .execute-icon-only {
        aspect-ratio: 1 / 1;
        padding-left: 0;
        padding-right: 0;
    }
</style>
