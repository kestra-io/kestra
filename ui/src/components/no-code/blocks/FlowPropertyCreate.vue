<template>
    <Task />
</template>

<script setup lang="ts">
    import {computed, provide} from "vue"
    import Task from "../segments/Task.vue"
    import {
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        CLOSE_TASK_FUNCTION_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        EDIT_TASK_FUNCTION_INJECTION_KEY,
        EDITING_TASK_INJECTION_KEY,
        FIELDNAME_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        POSITION_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
    } from "../injectionKeys"

    const props = defineProps<{
        parentPath: string
        refPath?: number
        blockSchemaPath: string
    }>()

    const emit = defineEmits<{
        (e: "created", parentPath: string, blockSchemaPath: string, refPath: number | undefined): void
        (e: "close"): void
    }>()

    // segments/Task is the only surface that INSERTS a new entry; the task-edit panel
    // only replaces what is already at a path, which is why creation has to come here.
    provide(PARENT_PATH_INJECTION_KEY, props.parentPath)
    provide(REF_PATH_INJECTION_KEY, props.refPath)
    provide(POSITION_INJECTION_KEY, "after")
    provide(CREATING_TASK_INJECTION_KEY, true)
    provide(EDITING_TASK_INJECTION_KEY, false)
    provide(FIELDNAME_INJECTION_KEY, undefined)
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath))
    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath) => {
        emit("created", parentPath, blockSchemaPath, refPath)
    })
    provide(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => emit("close"))
</script>
