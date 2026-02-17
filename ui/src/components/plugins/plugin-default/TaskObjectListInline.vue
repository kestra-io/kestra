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
                <el-button
                    link
                    type="danger"
                    @click="remove(index)"
                >
                    <DeleteOutline />
                </el-button>
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
    import {ref} from "vue";
    import TaskObjectInline from "./TaskObjectInline.vue";
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";

    const modelValue = defineModel<any[]>();

    defineProps<{
        fieldKey: string;
        root: string;
        taskSchemaPath: string;
    }>();

    const creationKey = ref(0);

    const update = (index: number, val: any) => {
        const newVal = [...(modelValue.value ?? [])];
        newVal[index] = val;
        modelValue.value = newVal;
    };

    const add = (val: any) => {
        if (!val || Object.keys(val).length === 0) return;
        modelValue.value = [...(modelValue.value ?? []), val];
        creationKey.value++;
    };

    const remove = (index: number) => {
        const newVal = [...(modelValue.value ?? [])];
        newVal.splice(index, 1);
        modelValue.value = newVal.length ? newVal : undefined;
    };
</script>


<style scoped lang="scss">
.task-list {
    .label {
        font-family: var(--bs-font-monospace);
        color: var(--ks-content-primary);
        font-size: 0.875rem;
        font-weight: 600;
    }

    .index-tag {
        font-size: 0.75rem;
        font-weight: 700;
        color: var(--ks-content-tertiary);
        text-transform: uppercase;
    }
}
</style>
