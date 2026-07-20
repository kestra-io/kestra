<template>
    <NamespaceSelect
        v-model="modelValue"
        multiple
        taggable
    />
</template>

<script lang="ts" setup>
    import NamespaceSelect from "../../../namespaces/components/NamespaceSelect.vue"

    const modelValue = defineModel<string[]>({default: () => []})
</script>
