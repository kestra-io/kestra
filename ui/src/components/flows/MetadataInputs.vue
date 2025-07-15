<template>
    <span v-if="required" class="me-1 text-danger">*</span>
    <span class="label">{{ label }}</span>
    <div class="mt-1 mb-2 wrapper">
        <el-row
            v-for="(input, index) in newInputs"
            :key="index"
            @click="selectInput(input, index)"
        >
            <el-col :span="24" class="d-flex">
                <InputText readonly :model-value="input.id" class="w-100" />
                <DeleteOutline
                    @click.prevent.stop="deleteInput(index)"
                    class="ms-2 delete"
                />
            </el-col>
        </el-row>
        <Add @add="addInput()" />
    </div>
</template>

<script setup lang="ts">
    import {ref, watch, inject} from "vue";
    import InputText from "../code/components/inputs/InputText.vue";
    import Add from "../code/components/Add.vue";
    import {DeleteOutline} from "../code/utils/icons";
    import {PANEL_INJECTION_KEY} from "../code/injectionKeys";

    interface InputType {
        type: string;
        id?: string;
        cls?: string;
    }

    const props = withDefaults(defineProps<{
        modelValue: InputType[];
        label: string;
        required?: boolean;
        disabled?: boolean;
    }>(), {
        modelValue: () => [],
        required: false,
        disabled: false
    });

    const emit = defineEmits<{
        (e: "update:modelValue", value: InputType[]): void
    }>();

    const panel = inject(PANEL_INJECTION_KEY, ref());

    const newInputs = ref<InputType[]>([]);
    const selectedInput = ref<InputType | undefined>();
    const selectedIndex = ref<number | undefined>();
    const loading = ref(false);

    watch(() => props.modelValue, (newValue) => {
        newInputs.value = newValue;
    }, {deep: true, immediate: true});

    const selectInput = async (input: InputType, index: number) => {
        loading.value = true;
        selectedInput.value = input;
        selectedIndex.value = index;

        panel.value = {
            props: {
                selectedIndex: index,
            }
        };
    };

    const deleteInput = (index: number) => {
        newInputs.value.splice(index, 1);
        emit("update:modelValue", newInputs.value);
    };

    const addInput = () => {
        newInputs.value.push({type: "STRING"});
        selectInput(newInputs.value[newInputs.value.length - 1], newInputs.value.length - 1);
    };
</script>

<style scoped lang="scss">
@import "../../components/code/styles/code.scss";
</style>
