<template>
    <el-button
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
    import {apiUrl} from "override/utils/route";

    import {Execution} from "../../../../../stores/executions";

    import Api from "vue-material-design-icons/Api.vue";

    const props = defineProps<{ execution: Execution }>();
</script>
