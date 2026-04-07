<template>
    <el-dropdown-item :icon="LocationExit" @click="redirect">
        {{ $t("outputs") }}
    </el-dropdown-item>
</template>

<script setup lang="ts">
    import {type PropType} from "vue";
    import {useRouter} from "vue-router";

    import LocationExit from "vue-material-design-icons/LocationExit.vue";

    const props = defineProps({
        outputs: {
            type: Object as PropType<any>,
            default: () => ({}),
        },
        execution: {
            type: Object as PropType<any>,
            required: true,
        },
        taskRunId: {
            type: String,
            required: true
        }
    });

    const router = useRouter();

    const redirect = () => {
        router.push({
            name: "executions/update",
            params: {
                namespace: props.execution.namespace,
                flowId: props.execution.flowId,
                id: props.execution.id,
                tab: "outputs"
            },
            query: {
                q: props.taskRunId
            }
        });
    };
</script>