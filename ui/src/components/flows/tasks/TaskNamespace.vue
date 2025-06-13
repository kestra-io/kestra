<template>
    <NamespaceSelect
        data-type="flow"
        :value="modelValue"
        allow-create
        @update:model-value="onInput"
    />
</template>
<script>
    import Task from "./Task";
    import NamespaceSelect from "../../namespaces/components/NamespaceSelect.vue";
    import {mapState} from "vuex";
    export default {
        components: {NamespaceSelect},
        mixins: [Task],
        created() {
            const flowNamespace = this.flow?.namespace;
            if (!this.modelValue && flowNamespace) {
                this.onInput(flowNamespace)
            }
        },
        computed: {
            ...mapState("flow", ["flow"])
        }
    };
</script>
