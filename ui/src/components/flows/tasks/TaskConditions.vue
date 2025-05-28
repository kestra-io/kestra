<template>
    <div class="conditions-wrapper">
        <Collapse
            title="conditions"
            :elements="items"
            section="conditions"
            block-type="conditions"
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

    interface Condition {id:string, type:string}

    const props = withDefaults(defineProps<{
        modelValue?: Condition[]
    }>(), {
        modelValue: () => []
    });

    const items = computed(() =>
        !Array.isArray(props.modelValue) ? [props.modelValue] : props.modelValue,
    );
</script>

<style scoped lang="scss">
@import "../../code/styles/code.scss";

.conditions-wrapper {
    width: 100%;
}
</style>
