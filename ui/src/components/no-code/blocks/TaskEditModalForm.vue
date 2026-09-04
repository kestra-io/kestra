<template>
    <Suspense>
        <TaskEdit
            :task="task"
            :taskRaw="taskRaw"
            :section="section"
            :flowId="flowId"
            :namespace="namespace"
            :editorKey="editorKey"
            presentation="panel"
            :isHidden="true"
            :hideTabstrip="true"
            @update:task="emit('update:task', $event)"
            @close="emit('close')"
        />
    </Suspense>
</template>

<script setup lang="ts">
    import {computed, provide} from "vue"
    import TaskEdit from "../../flows/TaskEdit.vue"
    import type {BlockSection} from "../../../utils/flowableBlockOps"
    import {
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        EDIT_TASK_FUNCTION_INJECTION_KEY,
        EDITING_TASK_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
    } from "../injectionKeys"

    const props = defineProps<{
        task?: Record<string, unknown>
        taskRaw?: string
        section: BlockSection
        flowId: string
        namespace: string
        editorKey: string
        parentPath: string
        refPath?: number
        blockSchemaPath: string
    }>()

    const emit = defineEmits<{
        (e: "update:task", value: string): void
        (e: "close"): void
        (e: "select-nested", parentPath: string, blockSchemaPath: string, refPath: number | undefined, split?: boolean): void
    }>()

    provide(PARENT_PATH_INJECTION_KEY, props.parentPath)
    provide(REF_PATH_INJECTION_KEY, props.refPath)
    provide(EDITING_TASK_INJECTION_KEY, true)
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath))
    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath, split) => {
        emit("select-nested", parentPath, blockSchemaPath, refPath, split)
    })
</script>
