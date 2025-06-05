<template>
    <el-row
        v-for="(element, index) in items"
        :key="'array-' + index"
        :gutter="10"
        class="w-100"
    >
        <el-col :span="2" class="d-flex flex-column justify-content-center mt-1 mb-2 reorder" v-if="items.length > 1">
            <ChevronUp
                @click.prevent.stop="moveItem(index, 'up')"
                :class="{disabled: index === 0}"
            />
            <ChevronDown
                @click.prevent.stop="moveItem(index, 'down')"
                :class="{disabled: index === items.length - 1}"
            />
        </el-col>
        <el-col :span="items.length > 1 ? 20 : 22" class="pe-2">
            <TaskWrapper :merge="!needWrapper">
                <template #tasks>
                    <component
                        :key="'array-' + index"
                        :is="componentType"
                        :model-value="element"
                        :task="modelValue"
                        root="array"
                        :properties="{}"
                        :schema="props.schema.items"
                        :definitions="props.definitions"
                        @update:model-value="handleInput($event, index)"
                    />
                </template>
            </TaskWrapper>
        </el-col>
        <el-col :span="2" class="d-flex align-items-center justify-content-center delete">
            <DeleteOutline @click="removeItem(index)" />
        </el-col>
    </el-row>
    <Add @add="addItem()" />
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";

    import {DeleteOutline, ChevronUp, ChevronDown} from "../../code/utils/icons";

    import Add from "../../code/components/Add.vue";
    import getTaskComponent from "./getTaskComponent";
    import TaskWrapper from "./TaskWrapper.vue";

    defineOptions({inheritAttrs: false});

    const emits = defineEmits(["update:modelValue"]);
    const props = withDefaults(defineProps<{
        schema: any;
        definitions: any;
        modelValue?: (string | number | boolean | undefined)[] | string | number | boolean;
    }>(), {
        modelValue: undefined,
        schema: () => ({}),
        definitions: () => ({}),
    });

    const componentType = computed(() => {
        return getTaskComponent(props.schema.items, "", props.definitions);
    });

    const needWrapper = computed(() => {
        return componentType.value.ksTaskName !== "string" &&
            componentType.value.ksTaskName !== "number" &&
            componentType.value.ksTaskName !== "boolean" &&
            componentType.value.ksTaskName !== "expression";
    });

    const items = ref(
        !Array.isArray(props.modelValue) ? [props.modelValue] : props.modelValue,
    );

    const handleInput = (value: string, index: number) => {
        items.value[index] = value;
        emits("update:modelValue", items.value);
    };

    const addItem = () => {
        items.value.push(undefined);
        emits("update:modelValue", items.value);
    };
    const removeItem = (index: number) => {
        items.value.splice(index, 1);
        emits("update:modelValue", items.value);
    };

    const moveItem = (index: number, direction: "up" | "down") => {
        if (direction === "up" && index > 0) {
            [items.value[index - 1], items.value[index]] = [
                items.value[index],
                items.value[index - 1],
            ];
        } else if (direction === "down" && index < items.value.length - 1) {
            [items.value[index + 1], items.value[index]] = [
                items.value[index],
                items.value[index + 1],
            ];
        }
        emits("update:modelValue", items.value);
    };
</script>

<style scoped lang="scss">
@import "../../code/styles/code.scss";

.disabled {
    opacity: 0.5;
    pointer-events: none;
    cursor: not-allowed;
}
</style>
