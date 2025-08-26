<template>
    <pre class="ks-constant">{{ model }}</pre>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue";

    defineOptions({inheritAttrs: false});

    const model = defineModel("modelValue", {
        type: [String, Object],
        default: "",
    });

    const props = defineProps({
        schema: {
            type: Object,
            required: true,
        },
    });

    const constValue = computed(() => {
        return props.schema.const;
    });

    watch(constValue, (val) => {
        model.value = val
    }, {immediate: true});
</script>

<style scoped>
.ks-constant {
    display: block;
    width: 100%;
    padding: 0 9px;
    border: 1px solid var(--ks-border-primary);
    border-radius: 4px;
}
</style>