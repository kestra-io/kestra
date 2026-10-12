<template>
    <KsSelect
        :modelValue="values"
        @update:model-value="onInput"
        filterable
        clearable
        allowCreate
        :placeholder="task?.namespace ? $t('select') : $t('no_code.select.namespace_first')"
        :disabled="!task?.namespace"
    >
        <KsOption
            v-for="item in flowIds"
            :key="item"
            :label="item"
            :value="item"
        />
    </KsSelect>
</template>
<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {collapseEmptyValues} from "../utils/collapseEmptyValues"
    import {useFlowStore} from "../../../../stores/flow"

    type ModelValue = object | string | number | boolean | unknown[]

    const props = withDefaults(defineProps<{
        modelValue?: ModelValue
        schema?: Record<string, unknown>
        required?: boolean
        task?: Record<string, unknown>
        root?: string
        definitions?: Record<string, unknown>
    }>(), {
        modelValue: undefined,
        schema: undefined,
        required: false,
        task: undefined,
        root: undefined,
        definitions: undefined,
    })

    const emit = defineEmits<{
        "update:modelValue": [value: ModelValue | undefined]
    }>()

    const flowStore = useFlowStore()

    const flowIds = ref<string[]>([])

    const values = computed(() => props.modelValue ?? (props.schema as Record<string, unknown> | undefined)?.default)

    const namespace = computed(() => {
        return (props.task?.namespace as string | undefined) ?? flowStore.flow?.namespace
    })

    watch(namespace, async () => {
        if (!namespace.value) return
        flowIds.value = ((await flowStore.flowsByNamespace(namespace.value)) as {id: string}[])
            .map((flow: {id: string}) => flow.id)

        if (namespace.value === flowStore.flow?.namespace) {
            flowIds.value = flowIds.value.filter(id => id !== flowStore.flow?.id)
        }
    }, {immediate: true})

    function onInput(value: ModelValue) {
        emit("update:modelValue", collapseEmptyValues(value))
    }
</script>
