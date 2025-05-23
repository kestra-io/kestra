<template>
    <div class="conditions-wrapper">
        <Collapse
            title="conditions"
            :elements="items"
            section="conditions"
            block-type="conditions"
            @remove="(yaml) => emits('update:modelValue', yaml)"
            @reorder="(yaml) => emits('update:modelValue', yaml)"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import Collapse from "../../code/components/collapse/Collapse.vue";

    defineOptions({inheritAttrs: false});

    interface Condition {id:string, type:string}

    const emits = defineEmits<{
        (e: "update:modelValue", conditions: Condition[]): void
    }>();

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
