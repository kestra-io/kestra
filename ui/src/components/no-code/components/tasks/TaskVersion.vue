<template>
    <InputText
        :model-value="modelValue"
        :disabled
        :placeholder="disabled ? t('no_code.version_oss_placeholder') : undefined"
        class="w-100"
    />
</template>

<script setup lang="ts">
    import {computed, getCurrentInstance} from "vue";
    import {useI18n} from "vue-i18n";
    import InputText from "../inputs/InputText.vue";

    const {t} = useI18n()

    const modelValue = defineModel<string>({default: ""})

    defineOptions({
        name: "TaskVersion",
        inheritAttrs: false,
    })

    const disabled = computed(() => {
        return Boolean(getCurrentInstance()?.appContext.config.globalProperties.$isOss)
    })
</script>