<template>
    <NamespaceSelect
        data-type="flow"
        :value="modelValue"
        :read-only="!isCreating"
        allow-create
        @update:model-value="onInput"
    />
</template>
<script>
    import {mapStores} from "pinia";
    import Task from "./MixinTask";
    import NamespaceSelect from "../../../namespaces/components/NamespaceSelect.vue";

    import {useFlowStore} from "../../../../stores/flow";
    export default {
        components: {NamespaceSelect},
        mixins: [Task],
        created() {
            const flowNamespace = this.flowStore.flow?.namespace;
            if (!this.modelValue && flowNamespace) {
                this.onInput(flowNamespace)
            }
        },
        computed: {
            ...mapStores(useFlowStore),
            isCreating() {
                return this.flowStore.isCreating;
            }
        }
    };
</script>
