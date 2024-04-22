<template>
    <editor
        :model-value="localEditorValue"
        :navbar="false"
        :full-height="false"
        :input="true"
        lang="yaml"
        @update:model-value="editorInput"
    />
</template>
<script>
    import Task from "./Task";
    import Editor from "../../../components/inputs/Editor.vue";
    import YamlUtils from "../../../utils/yamlUtils";

    export default {
        mixins: [Task],
        components: {Editor},
        data() {
            return {
                localEditorValue: undefined
            }
        },
        created() {
            this.localEditorValue = this.editorValue;
        },
        methods: {
            editorInput(value) {
                this.localEditorValue = value;
                this.onInput(this.parseValue(value));
            },
            parseValue(value) {
                if(value.match(/^\s*{{/)) {
                    return value;
                }

                return YamlUtils.parse(value);
            }
        }
    };
</script>
