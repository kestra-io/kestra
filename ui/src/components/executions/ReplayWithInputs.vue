<template>
    <FlowRun
        flow
        execution
        autoPrefill
        buttonText="replay"
        :buttonIcon="PlayBoxMultiple"
        :replaySubmit="handleReplaySubmit"
        buttonTestId="replay-dialog-button"
        @execution-trigger="$emit('executionTrigger')"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import moment from "moment-timezone"
    import {useToast} from "../../utils/toast"
    import {useRouter, useRoute} from "vue-router"
    import {inputsToFormData} from "../../utils/submitTask"
    import {useExecutionsStore, type Execution} from "../../stores/executions"
    import {EXECUTION_PARENT_ROUTE} from "./executionTabs"
    import * as ExecutionUtils from "../../utils/executionUtils"
    import FlowRun from "../../components/flows/FlowRun.vue"
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue"
    import {useClient} from "@kestra-io/kestra-sdk"

    const {t} = useI18n()
    const toast = useToast()
    const route = useRoute()
    const router = useRouter()

    const props = defineProps({
        execution: {type: Object, required: true},
        taskRun: {type: Object, required: false, default: undefined},
        revision: {type: Number, required: false, default: undefined},
    })

    const emit = defineEmits(["executionTrigger"])

    const executionsStore = useExecutionsStore()

    const flow = computed(() => executionsStore.flow)

    const axios = useClient()

    const handleReplaySubmit = async ({inputs, breakpoints}: any) => {

        const formData = inputsToFormData({$moment: moment}, flow.value?.inputs, inputs)
        const replayed = await executionsStore.replayExecutionWithInputs({
            executionId: props.execution.id,
            taskRunId: props.taskRun?.id,
            revision: props.revision,
            breakpoints,
            formData,
        })

        // A replay that reuses this execution's id only differs by its next state, so wait for it
        // before navigating - otherwise the page reopens on the state it is already showing.
        const execution = replayed.id === props.execution.id
            ? await ExecutionUtils.waitForState(axios, replayed) as Execution
            : replayed

        executionsStore.execution = execution
        // The parent route resolves the user's default execution tab; naming a tab here ignored it.
        await router.push({
            name: EXECUTION_PARENT_ROUTE,
            params: {
                namespace: execution.namespace,
                flowId: execution.flowId,
                id: execution.id,
                tenant: route.params.tenant,
            },
        })

        toast.success(t("replayed"))
    }
</script>


