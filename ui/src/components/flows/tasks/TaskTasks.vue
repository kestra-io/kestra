<template>
    <div class="tasks-wrapper">
        <Collapse
            title="tasks"
            :elements="items"
            @remove="(yaml) => emits('update:modelValue', yaml)"
            @reorder="(yaml) => emits('update:modelValue', yaml)"
        />
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import Collapse from "../../code/components/collapse/Collapse.vue";

    defineOptions({inheritAttrs: false});

    interface Task {id:string, type:string}

    const emits = defineEmits<{
        (e: "update:modelValue", tasks: Task[]): void
    }>();

    const props = withDefaults(defineProps<{
        modelValue?: Task[]
    }>(), {
        modelValue: () => []
    });

    const items = ref(
        !Array.isArray(props.modelValue) ? [props.modelValue] : props.modelValue,
    );

    // const handleInput = (value: Task, index: number) => {
    //     items.value[index] = value;
    //     emits("update:modelValue", items.value);
    // };

    // const removeItem = (index: number) => {
    //     items.value.splice(index, 1);
    //     emits("update:modelValue", items.value);
    // };

    // const moveItem = (index: number, direction: "up" | "down") => {
    //     if (direction === "up" && index > 0) {
    //         [items.value[index - 1], items.value[index]] = [
    //             items.value[index],
    //             items.value[index - 1],
    //         ];
    //     } else if (direction === "down" && index < items.value.length - 1) {
    //         [items.value[index + 1], items.value[index]] = [
    //             items.value[index],
    //             items.value[index + 1],
    //         ];
    //     }
    //     emits("update:modelValue", items.value);
    // };
</script>

<style scoped lang="scss">
@import "../../code/styles/code.scss";

.tasks-wrapper {
    width: 100%;
}

.disabled {
    opacity: 0.5;
    pointer-events: none;
    cursor: not-allowed;
}
</style>
