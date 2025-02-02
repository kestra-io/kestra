<template>
    <el-row
        v-for="(element, index) in items"
        :key="'array-' + index"
        :gutter="10"
        class="w-100 mb-2"
    >
        <el-col :span="22">
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

    import {DeleteOutline} from "../../code/utils/icons";

    import InputText from "../../code/components/inputs/InputText.vue";
    import Add from "../../code/components/Add.vue";

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
</script>
