<template>
    <el-button @click.prevent.stop="handleClick()" type="primary" :icon="Plus">
        {{ t("add") }}
    </el-button>
</template>

<script setup lang="ts">
    import {inject} from "vue";
    import {
        CREATE_TASK_FUNCTION_INJECTION_KEY,
    } from "../../../injectionKeys";
    import {Plus} from "../../../utils/icons";
    import {BlockType} from "../../../utils/types";


    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const props = defineProps<{
        blockType: BlockType | "pluginDefaults";
        parentPathComplete: string;
        refPath?: number;
    }>()

    const createTask = inject(CREATE_TASK_FUNCTION_INJECTION_KEY, () => {});

    const handleClick = () => {
        createTask(props.blockType, props.parentPathComplete, props.refPath);
    };
</script>
