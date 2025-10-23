<template>
    <InputText
        :modelValue="modelValue"
        :disabled
        :placeholder="disabled ? t('no_code.version_oss_placeholder') : undefined"
        class="w-100"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import InputText from "../inputs/InputText.vue";

    import {useMiscStore} from "override/stores/misc";
    const miscStore = useMiscStore();

    const {edition} = miscStore.configs || {};
    const disabled = computed(() => edition === "OSS")

    const {t} = useI18n()

    const modelValue = defineModel<string>({default: ""})

    defineOptions({
        name: "TaskVersion",
        inheritAttrs: false,
    })
</script>