<template>
    <NamespaceSelect
        data-type="flow"
        :value="modelValue"
        allow-create
        @update:model-value="onInput"
    />
</template>
<script>
    import {mapStores} from "pinia";
    import Task from "./Task";
    import NamespaceSelect from "../../namespaces/components/NamespaceSelect.vue";

    import {useFlowStore} from "../../../stores/flow";
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

        }
    };
</script>
