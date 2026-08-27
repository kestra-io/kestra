<template>
    <div class="flow-properties-edit" data-test="flow-properties-edit">
        <header v-if="!hideHeader" class="flow-properties-head">
            <KsIconButton :tooltip="$t('back')" data-test="flow-properties-back" @click="emit('close')">
                <ChevronLeft />
            </KsIconButton>
            <span class="flow-properties-title">{{ $t("no_code.sections.flow") }}</span>
        </header>

        <div v-if="createTarget" class="flow-properties-body" data-test="flow-properties-create">
            <FieldNavBreadcrumb
                class="flow-properties-crumb"
                :frames="[{path: createTarget.parentPath, label: createTarget.label}]"
                :rootLabel="$t('no_code.sections.flow')"
                @navigate="closeCreate"
                @back="closeCreate"
            />
            <BlockCreateForm
                v-if="!createTarget.created"
                :key="createTarget.editorKey"
                :parentPath="createTarget.parentPath"
                :refPath="createTarget.refPath"
                :blockSchemaPath="createTarget.blockSchemaPath"
                @created="onCreated"
                @close="closeCreate"
            />
            <TaskEditModalForm
                v-else
                :key="`${createTarget.editorKey}:edit`"
                section="tasks"
                :flowId="flowId"
                :namespace="namespace"
                :editorKey="`${createTarget.editorKey}:edit`"
                :parentPath="createTarget.created.parentPath"
                :refPath="createTarget.created.refPath"
                :blockSchemaPath="createTarget.created.blockSchemaPath"
                :taskRaw="createdRaw"
                @update:task="onCreatedEdited"
                @close="closeCreate"
            />
        </div>

        <div v-else class="flow-properties-body">
            <KsForm labelPosition="top">
                <Wrapper
                    v-for="field in fields"
                    :key="field.fieldKey"
                    :merge="shouldMerge(field.schema)"
                >
                    <template #tasks>
                        <TaskObjectField
                            v-bind="field"
                            @update:model-value="(val) => onUpdateField(field.fieldKey, val)"
                        />
                    </template>
                </Wrapper>
            </KsForm>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, provide, ref} from "vue"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import {KsForm, KsIconButton} from "@kestra-io/design-system"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"

    import Wrapper from "../components/tasks/Wrapper.vue"
    import TaskObjectField from "../components/tasks/TaskObjectField.vue"
    import FieldNavBreadcrumb from "../components/FieldNavBreadcrumb.vue"
    import BlockCreateForm from "./BlockCreateForm.vue"
    import TaskEditModalForm from "./TaskEditModalForm.vue"
    import {useFlowStore} from "../../../stores/flow"
    import {useFlowFields} from "../utils/useFlowFields"
    import {removeNullAndUndefined} from "../utils/cleanUp"
    import {
        CREATE_TASK_FUNCTION_INJECTION_KEY,
        FULL_SOURCE_INJECTION_KEY,
        UPDATE_YAML_FUNCTION_INJECTION_KEY,
    } from "../injectionKeys"

    const flowStore = useFlowStore()

    const props = defineProps<{hideHeader?: boolean; hostedInModal?: boolean}>()

    const emit = defineEmits<{(e: "close"): void}>()

    const FLOW_FIELDS = [
        "id",
        "namespace",
        "description",
        "labels",
        "inputs",
        "variables",
        "outputs",
        "concurrency",
        "retry",
        "sla",
        "checks",
        "workerSelector",
        "disabled",
    ]

    const bubbleCreate = inject(CREATE_TASK_FUNCTION_INJECTION_KEY, () => {})
    const flowYaml = inject(FULL_SOURCE_INJECTION_KEY, ref(""))
    const updateYaml = inject(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {})

    const flowId = computed(() => flowStore.flow?.id ?? "")
    const namespace = computed(() => flowStore.flow?.namespace ?? "")

    interface CreatedTarget {
        parentPath: string
        blockSchemaPath: string
        refPath?: number
    }

    interface CreateTarget {
        parentPath: string
        blockSchemaPath: string
        refPath?: number
        label: string
        editorKey: string
        /** Set once the entry exists in the flow, so the form switches from building it to editing it. */
        created?: CreatedTarget
    }

    const createTarget = ref<CreateTarget>()

    const createdPath = computed(() => {
        const created = createTarget.value?.created
        if (!created) return undefined
        return created.refPath !== undefined ? `${created.parentPath}[${created.refPath}]` : created.parentPath
    })

    const createdRaw = computed(() =>
        createdPath.value
            ? YAML_UTILS.extractBlockWithPath({source: flowYaml.value, path: createdPath.value}) ?? undefined
            : undefined,
    )

    function onCreated(parentPath: string, blockSchemaPath: string, refPath: number | undefined) {
        if (!createTarget.value) return
        createTarget.value = {...createTarget.value, created: {parentPath, blockSchemaPath, refPath}}
    }

    function onCreatedEdited(newContent: string) {
        if (!createdPath.value) return
        updateYaml(YAML_UTILS.replaceBlockWithPath({
            source: flowYaml.value,
            path: createdPath.value,
            newContent,
        }))
    }

    // A list here holds flow-level entries (inputs, outputs, sla…), never tasks. Inside the
    // modal the creation form belongs in this same modal behind a crumb; in the panel the
    // editor opens it as a tab, the way editing a task does there.
    provide(CREATE_TASK_FUNCTION_INJECTION_KEY, (parentPath, blockSchemaPath, refPath) => {
        if (!props.hostedInModal) {
            bubbleCreate(parentPath, blockSchemaPath, refPath)
            return
        }
        createTarget.value = {
            parentPath,
            blockSchemaPath,
            refPath,
            label: parentPath.split(".").pop() ?? parentPath,
            editorKey: `flow-properties-create:${parentPath}:${Date.now()}`,
        }
    })

    function closeCreate() {
        createTarget.value = undefined
    }

    const {fieldsFromSchemaTop, fieldsFromSchemaRest} = useFlowFields(computed(() => flowYaml.value))

    const fields = computed(() => {
        const all = [...fieldsFromSchemaTop.value, ...fieldsFromSchemaRest.value]
        return FLOW_FIELDS
            .map((key) => all.find((field) => field.fieldKey === key))
            .filter((field): field is NonNullable<typeof field> => Boolean(field))
    })

    function shouldMerge(schema: any): boolean {
        const complexObject = ["object", "array"].includes(schema?.type) || schema?.$ref || schema?.oneOf || schema?.anyOf || schema?.allOf
        return !complexObject
    }

    function onUpdateField(key: string, val: any) {
        const realValue = val === null || val === undefined
            ? undefined
            : typeof val === "object" && !Array.isArray(val)
                ? removeNullAndUndefined(val)
                : val

        updateYaml(YAML_UTILS.replaceBlockWithPath({
            source: flowYaml.value,
            path: key,
            newContent: YAML_UTILS.stringify(realValue),
        }))
    }
</script>

<style scoped lang="scss">
.flow-properties-edit {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
}

.flow-properties-head {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-3) var(--ks-spacing-4);
    border-bottom: 1px solid var(--ks-border-subtle);
}

.flow-properties-title {
    font-size: var(--ks-font-size-base);
    font-weight: 600;
    color: var(--ks-text-primary);
}

.flow-properties-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: var(--ks-spacing-4);
}

.flow-properties-crumb {
    margin-bottom: var(--ks-spacing-3);
}
</style>
