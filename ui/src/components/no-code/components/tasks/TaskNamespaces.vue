<template>
    <NamespaceSelect
        data-type="flow"
        v-model="modelValue"
        multiple
        allowCreate
    />
</template>

<script lang="ts" setup>
    import NamespaceSelect from "../../../namespaces/components/NamespaceSelect.vue"

    const modelValue = defineModel<string[]>({default: () => []})
</script>
