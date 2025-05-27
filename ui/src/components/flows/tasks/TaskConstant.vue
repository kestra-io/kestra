<template>
    <pre>{{ model }}</pre>
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