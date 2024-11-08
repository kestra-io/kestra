<template>
    <div>
        <MonacoEditor 
            :value="flowContent" 
            :language="language" 
            :theme="theme" 
            @change="updateFlowContent"
        />
    </div>
</template>

<script>
import { defineComponent } from "vue";
import MonacoEditor from "./MonacoEditor.vue";

export default defineComponent({
    components: { MonacoEditor },
    props: {
        flowContent: {
            type: String,
            required: true
        },
        language: {
            type: String,
            default: "yaml"
        },
        theme: {
            type: String,
            default: "vs-dark"
        }
    },
    methods: {
        updateFlowContent(newValue) {
            this.$emit("update:flowContent", newValue);
        }
    }
});
</script>

<style scoped>
/* Add any specific styles for FlowMonacoEditor here */
</style>