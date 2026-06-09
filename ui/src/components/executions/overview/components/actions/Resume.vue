<template>
    <NavBarAction
        v-if="enabled"
        :icon="Play"
        @click="click"
    >
        {{ $t('resume') }}
    </NavBarAction>

    <KsDialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <span v-html="$t('resumed title', {id: execution.id})" />
        </template>
        <KsForm :model="inputs" labelPosition="top" ref="form" @submit.prevent="false">
            <InputsForm :initialInputs="inputsList" :execution="execution" v-model="inputs" />
        </KsForm>
        <template #footer>
            <KsButton :icon="PlayBox" type="primary" @click="resumeWithInputs(form)" nativeType="submit">
                {{ $t('resume') }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, getCurrentInstance} from "vue"
    import {useI18n} from "vue-i18n"
    import Play from "vue-material-design-icons/Play.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"
    import PlayBox from "vue-material-design-icons/PlayBox.vue"
    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"
    import {State} from "@kestra-io/design-system"
    import * as FlowUtils from "../../../../../utils/flowUtils"
    import * as ExecutionUtils from "../../../../../utils/executionUtils"
    import InputsForm from "../../../../../components/inputs/InputsForm.vue"
    import {inputsToFormData} from "../../../../../utils/submitTask"
    import {useExecutionsStore} from "../../../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../../../../utils/toast"

    const props = withDefaults(defineProps<{
        // FIXME: any - execution is an untyped domain object
        execution: any // FIXME: any
        component?: string
    }>(), {
        component: "el-button",
    })

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()
    const toast = useToast()
    const instance = getCurrentInstance()
    // FIXME: any - $moment is registered as a global property via Vue plugin
    const $moment = instance?.appContext.config.globalProperties.$moment as any // FIXME: any

    const inputs = ref<Record<string, unknown>>({})
    const isDrawerOpen = ref(false)
    // FIXME: any - form ref type from element-plus
    const form = ref<any>(null) // FIXME: any

    const enabled = computed(() => {
        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace))) {
            return false
        }

        return State.isPaused(props.execution.state.current)
    })

    // FIXME: any - findTaskRunsByState and findTaskById return untyped objects
    const inputsList = computed<any[]>(() => { // FIXME: any
        const findTaskRunByState = ExecutionUtils.findTaskRunsByState(props.execution, State.PAUSED) as any[] // FIXME: any
        if (findTaskRunByState.length === 0) {
            return []
        }

        const findTaskById = FlowUtils.findTaskById(executionsStore.flow as any, findTaskRunByState[0].taskId) as {inputs?: any[]} | undefined // FIXME: any

        return findTaskById && findTaskById.inputs !== null ? findTaskById.inputs ?? [] : []
    })

    const needInputs = computed(() => inputsList.value?.length > 0)

    onMounted(() => {
        if (enabled.value) {
            loadDefinition()
        }
    })

    function click() {
        if (needInputs.value) {
            isDrawerOpen.value = true
            return
        }

        toast.confirm(t("resumed confirm", {id: props.execution.id}), () => {
            return resume() as unknown as Promise<void>
        })
    }

    function resumeWithInputs(formRef: {validate: (cb: (valid: boolean) => void) => void} | null) {
        if (formRef) {
            formRef.validate((valid: boolean) => {
                if (!valid) {
                    return false
                }

                const formData = inputsToFormData({$moment} as any, inputsList.value, inputs.value) // FIXME: any
                resume(formData)
            })
        }
    }

    function resume(formData?: FormData) {
        executionsStore
            .resume({
                id: props.execution.id,
                formData: formData,
            })
            .then(() => {
                isDrawerOpen.value = false
                toast.success(t("resumed done"))
            })
    }

    function loadDefinition() {
        executionsStore.loadFlowForExecution({
            flowId: props.execution.flowId,
            namespace: props.execution.namespace,
            store: true,
        })
    }
</script>
