<template>
    <el-row
        v-for="(element, index) in items"
        :key="'array-' + index"
        :gutter="10"
        class="w-100"
    >
        <el-col :span="2" class="d-flex flex-column mt-1 mb-2 reorder">
            <ChevronUp @click.prevent.stop="moveItem(index, 'up')" />
            <ChevronDown @click.prevent.stop="moveItem(index, 'down')" />
        </el-col>
        <el-col :span="20">
            <InputText
                :model-value="element"
                @update:model-value="(v) => handleInput(v, index)"
                :placeholder="$t('value')"
                class="w-100"
            />
        </el-col>
        <el-col :span="2" class="col align-self-center delete">
            <DeleteOutline @click="removeItem(index)" />
        </el-col>
    </el-row>
    <Add @add="addItem()" />
</template>

<script setup lang="ts">
    import {ref} from "vue";

    import {DeleteOutline, ChevronUp, ChevronDown} from "../../code/utils/icons";

    import InputText from "../../code/components/inputs/InputText.vue";
    import Add from "../../code/components/Add.vue";

    defineOptions({inheritAttrs: false});

    const emits = defineEmits(["update:modelValue"]);
    const props = defineProps({modelValue: {type: Array, default: undefined}});

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
</style>
