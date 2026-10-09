<template>
    <NavBarAction
        v-if="enabled"
        v-bind="$attrs"
        :icon="Play"
        @click="click"
    >
        {{ $t('resume') }}
    </NavBarAction>

    <KsDialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroyOnClose :appendToBody="true" scrollable>
        <template #header>
            <span v-html="$t('resumed title', {id: escapeHtml(execution.id)})" />
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
    import {ref, computed, onMounted} from "vue"
    import {useI18n} from "vue-i18n"
    import Play from "vue-material-design-icons/Play.vue"
    import NavBarAction from "../../../../layout/NavBarAction.vue"
    import PlayBox from "vue-material-design-icons/PlayBox.vue"
    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"
    import {State, escapeHtml, type FormInstance} from "@kestra-io/design-system"
    import * as FlowUtils from "../../../../../utils/flowUtils"
    import * as ExecutionUtils from "../../../../../utils/executionUtils"
    import InputsForm from "../../../../../components/inputs/InputsForm.vue"
    import {inputsToFormData} from "../../../../../utils/submitTask"
    import {Execution, InputMetaData, useExecutionsStore} from "../../../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"
    import {useToast} from "../../../../../utils/toast"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        execution: Execution
    }>()

    const {t} = useI18n()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()
    const toast = useToast()

    const inputs = ref<Record<string, unknown>>({})
    const isDrawerOpen = ref(false)
    const form = ref<FormInstance | null>(null)

    const enabled = computed(() => {
        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace))) {
            return false
        }

        return State.isPaused(props.execution.state.current)
    })

    const inputsList = computed<InputMetaData[]>(() => {
        const findTaskRunByState = ExecutionUtils.findTaskRunsByState(props.execution, State.PAUSED)
        if (findTaskRunByState.length === 0 || !findTaskRunByState[0].taskId) {
            return []
        }

        const findTaskById = FlowUtils.findTaskById(executionsStore.flow, findTaskRunByState[0].taskId) as {inputs?: InputMetaData[]} | undefined

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

    function resumeWithInputs(formRef: FormInstance | null) {
        if (formRef) {
            formRef.validate((valid: boolean) => {
                if (!valid) {
                    return
                }

                const formData = inputsToFormData(inputsList.value, inputs.value)
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
