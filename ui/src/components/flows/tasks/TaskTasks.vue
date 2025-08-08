<template>
    <div class="tasks-wrapper">
        <Collapse
            title="tasks"
            :elements="items"
            :section
            :block-schema-path="[schemaPath, 'properties', root, 'items'].join('/')"
            @remove="(yaml) => flowStore.flowYaml = yaml"
            @reorder="(yaml) => flowStore.flowYaml = yaml"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue";
    import Collapse from "../../code/components/collapse/Collapse.vue";
    import {SCHEMA_PATH_INJECTION_KEY} from "../../code/injectionKeys";
    import {useFlowStore} from "../../../stores/flow";

    const schemaPath = inject(SCHEMA_PATH_INJECTION_KEY, ref())

    defineOptions({
        inheritAttrs: false
    });

    const flowStore = useFlowStore();

    interface Task {
        id:string,
        type:string
    }

    const props = withDefaults(defineProps<{
        modelValue?: Task[],
        root?: string;
    }>(), {
        modelValue: () => [],
        root: undefined
    });

    const items = computed(() =>
        !Array.isArray(props.modelValue) ? [props.modelValue] : props.modelValue,
    );

    const section = computed(() => {
        return props.root ?? "tasks";
    });
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
