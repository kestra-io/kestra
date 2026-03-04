<template>
    <div class="item creation mt-2">
        <TaskEditor v-model="modelValue" />
    </div>
</template>

<script setup lang="ts">
    import {provide, computed} from "vue";

    import TaskEditor from "../../no-code/components/TaskEditor.vue";
    import {PARENT_PATH_INJECTION_KEY, BLOCK_SCHEMA_PATH_INJECTION_KEY} from "../../no-code/injectionKeys";

    const modelValue = defineModel<Record<string, any>>();

    const props = defineProps<{
        parentPath: string;
        blockSchemaPath: string;
    }>();

    provide(PARENT_PATH_INJECTION_KEY, props.parentPath);
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath));
</script>

<style scoped lang="scss">
    .item {
        background-color: var(--ks-background-card);
        border: 1px solid var(--ks-border-primary);
        border-radius: 8px;
        padding: 0.5rem;

        &.creation {
            border-style: dashed;
            background-color: transparent;
        }
    }
</style>