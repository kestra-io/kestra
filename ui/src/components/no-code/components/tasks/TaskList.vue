<template>
    <div class="tasks-wrapper">
        <KsCollapse v-model="expanded" class="collapse">
            <KsCollapseItem
                :name="section"
                :disabled="merge"
                :class="{merge}"
            >
                <template #title>
                    <span :class="{required}">{{ `${section}${elements ? ` (${elements.length})` : ''}` }}</span>
                </template>
                <template #icon>
                    <Creation
                        :parentPathComplete
                        :refPath="elements?.length ? elements.length - 1 : -1"
                        :blockSchemaPath
                    />
                </template>

                <div class="block-section-list" @dragend="handleDragEnd">
                    <LeafBlockCard
                        v-for="(element, elementIndex) in filteredElements"
                        :key="elementIndex"
                        :block="element"
                        :path="`${parentPathComplete}[${elementIndex}]`"
                        :label="cardLabel(element)"
                        :draggable="filteredElements.length > 1"
                        :dragOver="dragOverIndex === elementIndex"
                        :runnable="playgroundStore.enabled && hasId(element)"
                        :showDuplicate="hasId(element)"
                        :icons="pluginsStore.icons"
                        @select="onSelect(elementIndex)"
                        @open-split="onSelect(elementIndex, true)"
                        @delete="onDelete(elementIndex)"
                        @duplicate="onDuplicate(elementIndex)"
                        @run="onRun(element)"
                        @drag-start="handleDragStart($event, elementIndex)"
                        @drag-over="handleDragOver($event, elementIndex)"
                        @drop="onDrop($event, elementIndex)"
                        @drag-end="handleDragEnd"
                    />
                </div>
            </KsCollapseItem>
        </KsCollapse>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue"
    import Creation from "./taskList/buttons/Creation.vue"
    import LeafBlockCard from "../../blocks/LeafBlockCard.vue"

    import {CollapseItem} from "../../utils/types"

    import {
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        EDIT_TASK_FUNCTION_INJECTION_KEY,
        FULL_SCHEMA_INJECTION_KEY,
        FULL_SOURCE_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
        UPDATE_YAML_FUNCTION_INJECTION_KEY,
    } from "../../injectionKeys"
    import {getValueAtJsonPath} from "../../../../utils/utils"
    import {usePluginsStore} from "../../../../stores/plugins"
    import {useDragAndDrop} from "../../../../composables/useDragAndDrop"
    import {usePlaygroundRun} from "../../../../composables/playground/usePlaygroundRun"
    import {
        deleteBlockAtPath,
        displayTaskOf,
        duplicateBlockAtPath,
        reorderAtPath,
    } from "../../../../utils/flowableBlockOps"
    import {useI18n} from "vue-i18n"


    const blockSchemaPathInjected = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref(""))

    const schemaAtBlockPathInjected = computed(() => getValueAtJsonPath(fullSchema.value, blockSchemaPathInjected.value))

    const blockSchemaPath = computed(() => {
        const rootParts = props.root ? props.root.split(".") : []
        if(rootParts.length > 1){
            // if second part is a property not defined in properties,
            // it can only be defined by additionalProperties
            const s = schemaAtBlockPathInjected.value?.properties?.[rootParts[0]]
            if(s && s.properties?.[rootParts[1]] === undefined && s.additionalProperties){
                rootParts[1] = "additionalProperties"
            } else {
                rootParts.splice(1, 0, "properties")
            }
        }
        return [blockSchemaPathInjected.value, "properties", ...rootParts, "items"].join("/")
    })

    defineOptions({
        inheritAttrs: false,
    })

    interface Task {
        id: string;
        type: string;
        [key: string]: unknown;
    }

    defineEmits(["update:modelValue"])
    const props = withDefaults(defineProps<{
        modelValue?: Task[],
        root?: string;
        merge?: boolean;
        required?: boolean;
    }>(), {
        modelValue: () => [],
        root: undefined,
        merge: false,
        required: false,
    })

    const elements = computed(() =>
        !Array.isArray(props.modelValue) ? [props.modelValue] : props.modelValue,
    )

    const {t} = useI18n()

    const section = computed(() => {
        if(props.merge){
            return t("tasks")
        }
        return props.root ?? t("tasks")
    })

    const flow = inject(FULL_SOURCE_INJECTION_KEY, ref(""))

    const filteredElements = computed(() => elements.value?.filter(Boolean) ?? [])
    const expanded = props.merge ? computed(() => section.value) : ref<CollapseItem["title"]>(props.root ?? "tasks")

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "")
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined)
    const creatingTask = inject(CREATING_TASK_INJECTION_KEY, false)

    const parentPathComplete = computed(() => {
        return `${[
            [
                parentPath,
                creatingTask && refPath !== undefined
                    ? `[${refPath + 1}]`
                    : refPath !== undefined
                        ? `[${refPath}]`
                        : undefined,
            ].filter(Boolean).join(""),
            section.value,
        ].filter(p => p.length).join(".")}`
    })

    const updateYaml = inject(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {})
    const editTask = inject(EDIT_TASK_FUNCTION_INJECTION_KEY, () => {})

    const pluginsStore = usePluginsStore()
    const {runTask, playgroundStore} = usePlaygroundRun()
    const {dragOverIndex, handleDragStart, handleDragOver, handleDrop, handleDragEnd} = useDragAndDrop()

    const hasId = (element: Record<string, any>) => displayTaskOf(element).id != null

    const cardLabel = (element: Record<string, any>) => {
        const task = displayTaskOf(element)
        if (task.id != null) return String(task.id)
        const typeValue = task[typeFieldSchema.value]
        return typeof typeValue === "string" ? typeValue.split(".").pop() : undefined
    }

    const onSelect = (index: number, split = false) => {
        editTask(parentPathComplete.value, blockSchemaPath.value, index, split)
    }

    const onDelete = (index: number) => {
        updateYaml(deleteBlockAtPath(flow.value, `${parentPathComplete.value}[${index}]`))
    }

    const onDuplicate = (index: number) => {
        updateYaml(duplicateBlockAtPath(flow.value, `${parentPathComplete.value}[${index}]`))
    }

    const onRun = (element: Record<string, any>) => {
        const id = displayTaskOf(element).id
        if (id != null) runTask(String(id))
    }

    const onDrop = (event: DragEvent, targetIndex: number) => {
        handleDrop(event, targetIndex, (from, to) => {
            updateYaml(reorderAtPath(flow.value, parentPathComplete.value, from, to))
        })
    }

    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref<Record<string, any>>({}))

    const blockSchema = computed(() => getValueAtJsonPath(fullSchema.value, blockSchemaPath.value) ?? {})

    // resolve parentPathComplete field schema from pluginsStore
    const typeFieldSchema = computed(() => blockSchema.value?.type ? "type" : blockSchema.value?.on ? "on" : "type")
</script>

<style scoped lang="scss">
.tasks-wrapper {
    width: 100%;
}

.block-section-list {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-2);
}

.required::after {
    content: "*";
    color: var(--ks-text-error);
    margin-left: var(--ks-spacing-1);
}

.merge :deep(.kel-collapse-item__header){
    cursor: default;
}
</style>
