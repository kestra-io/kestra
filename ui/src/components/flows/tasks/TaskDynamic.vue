<template>
    <editor
        :model-value="editorValue"
        :navbar="false"
        :full-height="false"
        :input="true"
        lang="yaml"
        @update:model-value="onInput"
    />
</template>
<script>
    import Task from "./Task";
    import Editor from "../../../components/inputs/Editor.vue";

    export default {
        mixins: [Task],
        components: {Editor},
    };
</script>
