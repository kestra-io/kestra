<template>
    <el-row v-for="(item, index) in currentValue" :key="index" :gutter="10" class="w-100" :data-testid="`task-dict-item-${item[0]}-${index}`">
        <el-col :span="6">
            <InputText
                :model-value="item[0]"
                @update:model-value="onKey(index, $event)"
                margin="m-0"
                placeholder="Key"
            />
        </el-col>
        <el-col :span="16">
            <component
                :is="schema.additionalProperties ? getTaskComponent(schema.additionalProperties) : TaskExpression"
                :model-value="item[1]"
                @update:model-value="onValueChange(index, $event)"
                :root="getKey(item[0])"
                :schema="schema.additionalProperties"
                :required="isRequired(item[0])"
                :definitions="definitions"
                :disabled
            />
        </el-col>
        <el-col :span="2" class="col align-self-center delete">
            <DeleteOutline @click="removeItem(index)" />
        </el-col>
    </el-row>
    <Add v-if="!disabledAdding" @add="addItem()" />
</template>

<script lang="ts" setup>
    import {computed, ref, watch} from "vue";
    import {DeleteOutline} from "../../code/utils/icons";

    import InputText from "../../code/components/inputs/InputText.vue";
    import TaskExpression from "./TaskExpression.vue";
    import Add from "../../code/components/Add.vue";
    import getTaskComponent from "./getTaskComponent";
    import debounce from "lodash/debounce";

    defineOptions({
        name: "TaskDict",
        inheritAttrs: false,
    });

    const props = defineProps({
        modelValue: {
            type: Object,
            default: () => ({}),
        },
        schema: {
            type: Object,
            required: true,
        },
        definitions: {
            type: Object,
            default: () => ({}),
        },
        root: {
            type: String,
            default: undefined,
        },
        disabled: {
            type: Boolean,
            default: false,
        },
    });

    const currentValue = ref<[string, any][]>([])

    watch(
        () => props.modelValue,
        (newValue) => {
            currentValue.value = Object.entries(newValue ?? {});
        },
        {
            immediate: true,
            deep: true
        },
    );

    const emitUpdate = debounce(function () {
        const uniqueKeys = new Set<string>();
        for (const [key, _] of currentValue.value) {
            // if two keys are the same, we want to avoid loosing data
            if(uniqueKeys.has(key)){
                // so we don't update the model value
                return
            };
            uniqueKeys.add(key);
        }
        emit("update:modelValue", Object.fromEntries(currentValue.value));
    }, 200);

    const emit = defineEmits(["update:modelValue"]);

    function getKey(key: string) {
        return props.root ? `${props.root}.${key}` : key;
    }

    function isRequired(key: string) {
        return props.schema?.required?.includes(key);
    }

    function onKey(key: number, val: string) {
        currentValue.value[key][0] = val;
        emitUpdate()
    }

    function onValueChange(key: number, val: any) {
        currentValue.value[key][1] = val;
        emitUpdate()
    }

    function removeItem(index: number) {
        currentValue.value.splice(index, 1);
        emitUpdate()
    }

    function addItem() {
        currentValue.value.push(["", undefined]);
        emitUpdate()
    }

    const disabledAdding = computed(() => {
        return props.disabled || currentValue.value.at(-1)?.[0] === "" && currentValue.value.at(-1)?.[1] === undefined;
    });
</script>

<style scoped lang="scss">
@import "../../code/styles/code.scss";
</style>
