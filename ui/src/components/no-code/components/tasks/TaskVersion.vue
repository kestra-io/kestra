<template>
    <InputText
        :modelValue="modelValue"
        :disabled
        :placeholder="disabled ? $t('no_code.version_oss_placeholder') : undefined"
        class="w-100"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import InputText from "../inputs/InputText.vue";

    import {useMiscStore} from "override/stores/misc";
    const miscStore = useMiscStore();
    
    const disabled = computed(() => miscStore.configs?.edition === "OSS") 

    const modelValue = defineModel<string>({default: ""})

    defineOptions({
        name: "TaskVersion",
        inheritAttrs: false,
    })
</script>