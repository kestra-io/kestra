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
            <Element
                section="tasks"
                :element="element"
                @update:model-value="(v) => handleInput(v, index)"
                :placeholder="$t('value')"
                class="w-100"
            />
        </el-col>
        <el-col :span="2" class="d-flex align-items-center justify-content-center delete">
            <DeleteOutline @click="removeItem(index)" />
        </el-col>
    </el-row>
    <Add @add="addItem()" />
</template>

<script setup lang="ts">
    import {ref} from "vue";

    import {DeleteOutline, ChevronUp, ChevronDown} from "../../code/utils/icons";
    import Element from "../../code/components/collapse/Element.vue";
    import Add from "../../code/components/Add.vue";

    defineOptions({inheritAttrs: false});

    interface Task {id:string, type:string}


    const emits = defineEmits(["update:modelValue"]);
    const props = withDefaults(defineProps<{
        modelValue?: Task[]
    }>(), {
        modelValue: () => []
    });

    const items = ref(
        !Array.isArray(props.modelValue) ? [props.modelValue] : props.modelValue,
    );

    const handleInput = (value: Task, index: number) => {
        items.value[index] = value;
        emits("update:modelValue", items.value);
    };

    const addItem = () => {

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
