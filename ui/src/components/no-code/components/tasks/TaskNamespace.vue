<template>
    <NamespaceSelect
        data-type="flow"
        v-model="modelValue"
        :readOnly="!isCreating"
        allowCreate
    />
</template>

<script lang="ts" setup>
    import {computed, onMounted} from "vue";
    import {useFlowStore} from "../../../../stores/flow";
    import NamespaceSelect from "../../../namespaces/components/NamespaceSelect.vue";

    const modelValue = defineModel<string>();

    const flowStore = useFlowStore();

    const isCreating = computed(() => flowStore.isCreating);

    onMounted(() => {
        const flowNamespace = flowStore.flow?.namespace;
        if (!modelValue.value && flowNamespace) {
            modelValue.value = flowNamespace;
        }
    });
</script>
