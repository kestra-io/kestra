<template>
    <KsSelect
        :modelValue="values"
        @update:model-value="onInput"
        filterable
        clearable
        allowCreate
        :placeholder="task?.namespace ? 'Select' : 'Select namespace first'"
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
    import {ref, computed, watch} from "vue"
    import {collapseEmptyValues} from "./MixinTask"
    import {useFlowStore} from "../../../../stores/flow"

    const props = defineProps<{
        modelValue?: unknown
        schema?: Record<string, unknown>
        required?: boolean
        task?: Record<string, unknown>
        root?: string
        definitions?: Record<string, unknown>
    }>()

    const emit = defineEmits<{
        "update:modelValue": [unknown]
    }>()

    function onInput(value: unknown) {
        emit("update:modelValue", collapseEmptyValues(value))
    }

    const values = computed(() =>
        props.modelValue !== undefined ? props.modelValue : props.schema?.default,
    )

    const flowStore = useFlowStore()
    const flowIds = ref<string[]>([])

    const namespace = computed<string | undefined>(() =>
        (props.task?.namespace as string | undefined) ?? flowStore.flow?.namespace,
    )

    watch(namespace, async () => {
        if (!namespace.value) {
            return
        }
        flowIds.value = (await flowStore.flowsByNamespace(namespace.value))
            .map((flow: { id: string }) => flow.id)

        if (namespace.value === flowStore.flow?.namespace) {
            flowIds.value = flowIds.value.filter(id => id !== flowStore.flow?.id)
        }
    }, {immediate: true})
</script>
