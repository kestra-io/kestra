<template>
    <div class="tasks-wrapper">
        <Collapse
            title="tasks"
            :elements="items"
            section="tasks"
            block-type="tasks"
            @remove="(yaml) => store.commit('flow/setFlowYaml', yaml)"
            @reorder="(yaml) => store.commit('flow/setFlowYaml', yaml)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useStore} from "vuex";
    import Collapse from "../../code/components/collapse/Collapse.vue";

    defineOptions({inheritAttrs: false});

    const store = useStore();

    interface Task {id:string, type:string}

    const props = withDefaults(defineProps<{
        modelValue?: Task[]
    }>(), {
        modelValue: () => []
    });

    const items = computed(() =>
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
