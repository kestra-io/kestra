<template>
    <div class="task-list w-100 mb-4">
        <div class="label pb-2">
            {{ fieldKey }}
        </div>

        <div
            v-for="(_, index) in modelValue"
            :key="index"
            class="item mb-4"
        >
            <div class="item-header d-flex justify-content-between align-items-center mb-2 px-2">
                <span class="index-tag">#{{ index + 1 }}</span>
                <KsButton
                    link
                    type="danger"
                    @click="remove(index)"
                >
                    <DeleteOutline />
                </KsButton>
            </div>
            <TaskObjectInline
                :modelValue="modelValue?.[index]"
                :parentPath="`${root}[${index}]`"
                :blockSchemaPath="taskSchemaPath"
                @update:model-value="val => update(index, val)"
            />
        </div>

        <TaskObjectInline
            :key="creationKey"
            :parentPath="`${root}[${(modelValue?.length ?? 0)}]`"
            :blockSchemaPath="taskSchemaPath"
            @update:model-value="add"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import TaskObjectInline from "./TaskObjectInline.vue"
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue"

    const modelValue = defineModel<any[]>()

    defineProps<{
        fieldKey: string;
        root: string;
        taskSchemaPath: string;
    }>()

    const creationKey = ref(0)

    const update = (index: number, val: any) => {
        const newVal = [...(modelValue.value ?? [])]
        newVal[index] = val
        modelValue.value = newVal
    }

    const add = (val: any) => {
        if (!val || Object.keys(val).length === 0) return
        modelValue.value = [...(modelValue.value ?? []), val]
        creationKey.value++
    }

    const remove = (index: number) => {
        const newVal = [...(modelValue.value ?? [])]
        newVal.splice(index, 1)
        modelValue.value = newVal.length ? newVal : undefined
    }
</script>


<style scoped lang="scss">
.task-list {
    .label {
        font-family: var(--kel-font-family-monospace);
        color: var(--ks-content-primary);
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
    }

    .index-tag {
        font-size: var(--ks-font-size-xs);
        font-weight: 700;
        color: var(--ks-content-tertiary);
        text-transform: uppercase;
    }
}
</style>
