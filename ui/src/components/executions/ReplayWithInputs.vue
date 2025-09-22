<template>
    <FlowRun
        flow
        execution
        buttonText="replay"
        :buttonIcon="PlayBoxMultiple"
        :replaySubmit="handleReplaySubmit"
        buttonTestId="replay-dialog-button"
        @execution-trigger="$emit('executionTrigger')"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useToast} from "../../utils/toast";
    import {useRouter, useRoute} from "vue-router";
    // @ts-expect-error no types yet
    import {inputsToFormData} from "../../utils/submitTask";
    import {useExecutionsStore} from "../../stores/executions";
    import * as ExecutionUtils from "../../utils/executionUtils";
    // @ts-expect-error no types yet
    import FlowRun from "../../components/flows/FlowRun.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";
    import {useAxios} from "../../utils/axios";

    const {t} = useI18n();
    const toast = useToast();
    const route = useRoute();
    const router = useRouter();

    const props = defineProps({
        execution: {type: Object, required: true},
        taskRun: {type: Object, required: false, default: undefined},
        revision: {type: Number, required: false, default: undefined}
    });

    const emit = defineEmits(["executionTrigger"]);

    const executionsStore = useExecutionsStore();

    const flow = computed(() => executionsStore.flow);

    const axios = useAxios()

    const handleReplaySubmit = async ({inputs}: any) => {

        const formData = inputsToFormData({}, flow.value.inputs, inputs);
        let response = await executionsStore.replayExecutionWithInputs({
            executionId: props.execution.id,
            taskRunId: props.taskRun?.id,
            revision: props.revision,
            formData
        });

        if (response.data.id === props.execution.id) {
            response = await ExecutionUtils.waitForState(axios, response.data) as any;
        }

        const execution = response.data;
        executionsStore.execution = execution;
        await router.push({
            name: "executions/update",
            params: {
                namespace: execution.namespace,
                flowId: execution.flowId,
                id: execution.id,
                tab: "gantt",
                tenant: route.params.tenant
            }
        });

        toast.success(t("replayed"));
        emit("executionTrigger");
    };
</script>


