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
