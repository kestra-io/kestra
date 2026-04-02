<template>
    <div class="wrapper">
        <MultilineSecret
            v-model="modelValue"
            :placeholder="root ?? ''"
            pebble
        />
    </div>
</template>

<script lang="ts" setup>
    import MultilineSecret from "../../../../components/secrets/MultilineSecret.vue";
    import {Schema} from "./getTaskComponent";

    defineOptions({inheritAttrs: false});

    defineProps<{
        disabled?: boolean;
        schema?: Schema;
        root?: string;
        task?: any;
    }>();

    const modelValue = defineModel<string>({default: ""});
</script>

<style scoped lang="scss">
.wrapper {
    display: flex;
    align-items: stretch;
    justify-content: stretch;
    border-radius: 0.25rem;
    border: 1px solid var(--ks-border-primary);
    width: 100%;

    :deep(.el-input__wrapper),
    :deep(.editor-container) {
        box-shadow: none;
    }

    :deep(.ks-editor) {
        flex: 1;
    }
}
</style>
