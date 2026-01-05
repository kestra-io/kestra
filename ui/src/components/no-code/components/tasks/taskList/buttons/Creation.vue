<template>
    <el-button @click.prevent.stop="handleClick()" type="default" :icon="Plus">
        {{ $t("add") }}
    </el-button>
</template>

<script setup lang="ts">
    import {inject} from "vue";
    import {
        CREATE_TASK_FUNCTION_INJECTION_KEY,
    } from "../../../../injectionKeys";
    import {Plus} from "../../../../utils/icons";

    const props = defineProps<{
        parentPathComplete: string;
        blockSchemaPath: string;
        refPath?: number;
    }>()

    const createTask = inject(CREATE_TASK_FUNCTION_INJECTION_KEY, () => {});

    const handleClick = () => {
        createTask(props.parentPathComplete, props.blockSchemaPath, props.refPath);
    };
</script>
