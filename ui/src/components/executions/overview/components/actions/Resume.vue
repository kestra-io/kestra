<template>
    <KsButton
        v-if="enabled"
        :icon="Play"
        @click="click"
    >
        {{ t('resume') }}
    </KsButton>

    <KsDialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <span v-html="t('resumed title', {id: execution.id})" />
        </template>
        <KsForm :model="inputs" labelPosition="top" ref="form" @submit.prevent="false">
            <InputsForm :initialInputs="inputsList" :execution="(execution as any)" v-model="inputs" />
        </KsForm>
        <template #footer>
            <KsButton :icon="PlayBox" type="primary" @click="resumeWithInputs(form)" nativeType="submit">
                {{ t('resume') }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {ref, computed, useTemplateRef} from "vue"
    import {useI18n} from "vue-i18n"
    import moment from "moment"
    import Play from "vue-material-design-icons/Play.vue"
    import PlayBox from "vue-material-design-icons/PlayBox.vue"
    import {State} from "@kestra-io/design-system"
    import {useToast} from "../../../../../utils/toast"
    import resource from "../../../../../models/resource"
    import action from "../../../../../models/action"
    // @ts-ignore - no type declarations for JS utility
    import * as FlowUtils from "../../../../../utils/flowUtils"
    import * as ExecutionUtils from "../../../../../utils/executionUtils"
    import InputsForm from "../../../../../components/inputs/InputsForm.vue"
    // @ts-ignore - no type declarations for JS utility
    import {inputsToFormData} from "../../../../../utils/submitTask"
    import {useExecutionsStore} from "../../../../../stores/executions"
    import {useAuthStore} from "override/stores/auth"

    const props = defineProps<{
        execution: Record<string, any> // FIXME: type this properly
        component?: string
    }>()

    const {t} = useI18n({useScope: "global"})
    const toast = useToast()
    const executionsStore = useExecutionsStore()
    const authStore = useAuthStore()

    const inputs = ref<Record<string, unknown>>({})
    const isDrawerOpen = ref(false)
    const form = useTemplateRef("form")

    const enabled = computed(() => {
        if (!(authStore.user?.isAllowed(resource.EXECUTION, action.UPDATE, props.execution.namespace))) {
            return false
        }
        return State.isPaused(props.execution.state.current)
    })

    const inputsList = computed(() => {
        const findTaskRunByState = ExecutionUtils.findTaskRunsByState(props.execution as any, State.PAUSED)
        if (findTaskRunByState.length === 0) {
            return []
        }

        const findTaskById = FlowUtils.findTaskById(executionsStore.flow, (findTaskRunByState[0] as any).taskId)

        return findTaskById && findTaskById.inputs !== null ? findTaskById.inputs : []
    })

    const needInputs = computed(() => inputsList.value?.length > 0)

    if (enabled.value) {
        loadDefinition()
    }

    function click() {
        if (needInputs.value) {
            isDrawerOpen.value = true
            return
        }

        toast.confirm(t("resumed confirm", {id: props.execution.id}), async () => {
            return resume()
        })
    }

    function resumeWithInputs(formRef: { validate: (cb: (valid: boolean) => void) => void } | null) {
        if (formRef) {
            formRef.validate((valid: boolean) => {
                if (!valid) {
                    return false
                }

                const formData = inputsToFormData({$moment: moment}, inputsList.value, inputs.value)
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
