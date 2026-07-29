<template>
    <div class="flow-properties-edit" data-test="flow-properties-edit">
        <header v-if="!hideHeader" class="flow-properties-head">
            <KsIconButton :tooltip="t('back')" data-test="flow-properties-back" @click="emit('close')">
                <ChevronLeft />
            </KsIconButton>
            <span class="flow-properties-title">{{ t("no_code.sections.flow") }}</span>
        </header>

        <div class="flow-properties-body">
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
    import {computed, inject, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import {KsForm, KsIconButton} from "@kestra-io/design-system"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"

    import Wrapper from "../components/tasks/Wrapper.vue"
    import TaskObjectField from "../components/tasks/TaskObjectField.vue"
    import {useFlowFields} from "../utils/useFlowFields"
    import {removeNullAndUndefined} from "../utils/cleanUp"
    import {FULL_SOURCE_INJECTION_KEY, UPDATE_YAML_FUNCTION_INJECTION_KEY} from "../injectionKeys"

    const {t} = useI18n()

    defineProps<{hideHeader?: boolean}>()

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
        // quotas is deliberately absent: the OSS schema advertises it but the
        // OSS executor rejects it at runtime (EE feature) in a way that
        // crash-loops the server — don't offer it until the backend guards it.
        // pluginDefaults is deliberately absent too: managing plugin defaults
        // belongs to the namespace-level Plugin Defaults surface, not the
        // no-code flow editor.
        "workerSelector",
        "disabled",
    ]

    const flowYaml = inject(FULL_SOURCE_INJECTION_KEY, ref(""))
    const updateYaml = inject(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {})

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
    overflow-y: auto;
    padding: var(--ks-spacing-4);
}
</style>
