<template>
    <el-row
        v-for="(element, index) in items"
        :key="'array-' + index"
        :gutter="10"
        align="top"
        class="w-100"
    >
        <el-col :span="2" class="d-flex flex-column justify-content-center reorder" v-if="items.length > 1">
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
            <Wrapper :merge="!needWrapper">
                <template #tasks>
                    <component
                        :key="'array-' + index"
                        :is="componentType"
                        :modelValue="element"
                        :task="modelValue"
                        :root="`${root}[${index}]`"
                        :properties="{}"
                        :schema="props.schema.items"
                        @update:model-value="handleInput($event, index)"
                    />
                </template>
            </Wrapper>
        </el-col>
        <el-col :span="2" class="delete">
            <DeleteOutline @click="removeItem(index)" />
        </el-col>
    </el-row>
    <Add @add="addItem()" />
</template>

<script setup lang="ts">
    import {computed, inject, provide, ref} from "vue";

    import {DeleteOutline, ChevronUp, ChevronDown} from "../../utils/icons";

    import Add from "../Add.vue";
    import Wrapper from "./Wrapper.vue";
    import {BLOCK_SCHEMA_PATH_INJECTION_KEY} from "../../injectionKeys";
    import {useBlockComponent} from "./useBlockComponent";

    defineOptions({inheritAttrs: false});

    const blockSchemaPath = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref())

    provide(BLOCK_SCHEMA_PATH_INJECTION_KEY, computed(() => {
        return [blockSchemaPath.value, "properties", props.root, "items"].join("/");
    }));

    const emits = defineEmits(["update:modelValue"]);
    const props = withDefaults(defineProps<{
        schema: any;
        modelValue?: (string | number | boolean | undefined)[] | string | number | boolean;
        required?: boolean;
        root?: string;
    }>(), {
        modelValue: undefined,
        schema: () => ({}),
        required: false,
        root: undefined,
    });

    const {getBlockComponent} = useBlockComponent();

    const componentType = computed(() => {
        return getBlockComponent.value?.(props.schema.items, props.root);
    });

    const needWrapper = computed(() => {
        return ![
            "string",
            "number",
            "boolean",
            "expression",
        ].includes(componentType.value.ksTaskName)
    });

    const items = computed(() =>
        props.modelValue === undefined && !props.required
            // we want to avoid displaying an item when
            // modelValue is undefined
            // if field is required though it invites users to fill it in
            ? []
            : !Array.isArray(props.modelValue) ? [props.modelValue] : props.modelValue,
    );

    const handleInput = (value: string, index: number) => {
        const newVal = [...items.value]
        newVal.splice(index, 1, value);
        emits("update:modelValue", newVal);
    };

    const newEmptyValue = computed(() => {
        if (props.schema.items?.type === "string") {
            return "";
        }
        return props.schema.items?.default ?? undefined;
    })

    const addItem = () => {
        emits("update:modelValue", [...items.value, newEmptyValue.value]);
    };

    const removeItem = (index: number) => {
        if (items.value.length <= 1) {
            emits("update:modelValue", undefined);
            return;
        }
        emits("update:modelValue", [...items.value].splice(index, 1));
    };

    const moveItem = (index: number, direction: "up" | "down") => {
        const tempValue = items.value
        if (direction === "up" && index > 0) {
            [tempValue[index - 1], tempValue[index]] = [
                tempValue[index],
                tempValue[index - 1],
            ];
        } else if (direction === "down" && index < tempValue.length - 1) {
            [tempValue[index + 1], tempValue[index]] = [
                tempValue[index],
                tempValue[index + 1],
            ];
        }
        emits("update:modelValue", tempValue);
    };
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

.disabled {
    opacity: 0.5;
    pointer-events: none;
    cursor: not-allowed;
}
</style>
