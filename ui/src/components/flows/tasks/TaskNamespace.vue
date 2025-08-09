<template>
    <InputText
        v-if="disabled"
        :model-value="modelValue"
        disabled
        class="w-100 disabled-field"
    />
    <NamespaceSelect
        v-else
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
    import InputText from "../../code/components/inputs/InputText.vue";
    export default {
        components: {InputText, NamespaceSelect},
        mixins: [Task],
        props: {
            disabled: {
                type: Boolean,
                default: false
            }
        },
        created() {
            const flowNamespace = this.flowStore.flow?.namespace;
            if (!this.modelValue && flowNamespace) {
                this.onInput(flowNamespace);
            }
        },
        computed: {
            ...mapStores(useFlowStore),

        }
    };
</script>
