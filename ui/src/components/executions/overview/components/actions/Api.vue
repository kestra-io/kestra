<template>
    <el-button
        v-if="isAllowedEdit"
        :icon="Api"
        tag="a"
        :href="`${apiUrl()}/executions/${props.execution.id}`"
        target="_blank"
        rel="noopener noreferrer"
    >
        {{ $t("api") }}
    </el-button>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    import {apiUrl} from "override/utils/route";

    import {Execution} from "../../../../../stores/executions";
    import {useAuthStore} from "override/stores/auth";

    import permission from "../../../../../models/permission";
    import action from "../../../../../models/action";

    import Api from "vue-material-design-icons/Api.vue";

    const props = defineProps<{ execution: Execution }>();

    const isAllowedEdit = computed(() => {
        return (
            props.execution &&
            useAuthStore().user?.isAllowed(
                permission.FLOW,
                action.UPDATE,
                props.execution.namespace,
            )
        );
    });
</script>
