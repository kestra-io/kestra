<template>
    <FlowRun
        flow
        execution
        button-text="replay"
        :button-icon="PlayBoxMultiple"
        :replay-submit="handleReplaySubmit"
        button-test-id="replay-dialog-button"
        @execution-trigger="$emit('executionTrigger')"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useToast} from "../../utils/toast";
    import {useRouter, useRoute} from "vue-router";
    import {inputsToFormData} from "../../utils/submitTask";
    import {useExecutionsStore} from "../../stores/executions";
    import ExecutionUtils from "../../utils/executionUtils";
    import FlowRun from "../../components/flows/FlowRun.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";

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

    const handleReplaySubmit = async ({inputs}: any) => {
        
        const formData = inputsToFormData({$http: null, $store: null}, flow.value.inputs, inputs);
        let response = await executionsStore.replayExecutionWithInputs({
            executionId: props.execution.id,
            taskRunId: props.taskRun?.id,
            revision: props.revision,
            formData
        });

        if (response.data.id === props.execution.id) {
            response = await ExecutionUtils.waitForState(null, null, response.data);
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


