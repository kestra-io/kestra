<template>
    <Suspense>
        <TaskEdit
            :task="task"
            :taskRaw="taskRaw"
            :section="section"
            :flowId="flowId"
            :namespace="namespace"
            :editorKey="editorKey"
            :taskPath="taskPath"
            presentation="panel"
            :isHidden="true"
            :hideTabstrip="true"
            :outsideFlow="outsideFlow"
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
        FIELD_VALIDATION_ERRORS_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
    } from "../injectionKeys"
    import {blockPath, useFieldValidationErrors} from "../utils/useFieldValidationErrors"
    import {modalItemPathOf} from "./useTaskEditModalStack"

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
        editedPath?: string
        outsideFlow?: boolean
    }>()

    const emit = defineEmits<{
        (e: "update:task", value: string): void
        (e: "close"): void
        (e: "select-nested", parentPath: string, blockSchemaPath: string, refPath: number | undefined, split?: boolean): void
    }>()

    const taskPath = computed(() => modalItemPathOf(props))

    provide(PARENT_PATH_INJECTION_KEY, props.parentPath)
    provide(REF_PATH_INJECTION_KEY, props.refPath)
    provide(EDITING_TASK_INJECTION_KEY, true)
    provide(FIELD_VALIDATION_ERRORS_INJECTION_KEY, useFieldValidationErrors(() => props.outsideFlow ? undefined : props.editedPath ?? blockPath(props.parentPath, props.refPath)))
    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => props.blockSchemaPath))
    provide(EDIT_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath, split) => {
        emit("select-nested", parentPath, blockSchemaPath, refPath, split)
    })
</script>
