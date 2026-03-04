<template>
    <NamespaceSelect
        data-type="flow"
        v-model="modelValue"
        :readOnly="!isCreating"
        allowCreate
    />
</template>

<script lang="ts" setup>
    import {onMounted, inject, computed, provide} from "vue";
    import NamespaceSelect from "../../../namespaces/components/NamespaceSelect.vue";
    import {CREATING_FLOW_INJECTION_KEY, DEFAULT_NAMESPACE_INJECTION_KEY} from "../../injectionKeys";

    const modelValue = defineModel<string>();
    const isCreating = inject(CREATING_FLOW_INJECTION_KEY, false);
    const defaultNamespace = inject(DEFAULT_NAMESPACE_INJECTION_KEY, computed(() => ""));
    provide(DEFAULT_NAMESPACE_INJECTION_KEY, computed(() => modelValue.value || defaultNamespace.value));

    onMounted(() => {
        const flowNamespace = defaultNamespace.value;
        if (!modelValue.value && flowNamespace) {
            modelValue.value = flowNamespace;
        }
    });
</script>
