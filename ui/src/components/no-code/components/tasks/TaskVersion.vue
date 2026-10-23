<template>
    <InputText
        v-model="modelValue"
        :disabled
        :placeholder="disabled ? $t('no_code.version_oss_placeholder') : undefined"
        class="task-version-input"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import InputText from "../inputs/InputText.vue"

    import {useMiscStore} from "override/stores/misc"
    const miscStore = useMiscStore()
    
    const disabled = computed(() => miscStore.configs?.edition === "OSS") 

    const modelValue = defineModel<string>({default: ""})

    defineOptions({
        name: "TaskVersion",
        inheritAttrs: false,
    })
</script>
<style scoped lang="scss">
.task-version-input {
    width: 100%;
}
</style>