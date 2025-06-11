<template>
    <InputText
        :model-value="modelValue"
        :disabled
        class="w-100"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import InputText from "../../code/components/inputs/InputText.vue";

    const modelValue = defineModel<string>({default: ""})

    defineOptions({
        name: "TaskVersion",
        inheritAttrs: false,
    })

    const disabled = computed(() => {
        return true
    })
</script>