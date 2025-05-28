<template>
    <editor
        :model-value="localEditorValue"
        :navbar="false"
        :full-height="false"
        :input="true"
        lang="yaml"
        :placeholder="`Your ${root || 'value'} here...`"
        @update:model-value="editorInput"
    />
</template>
<script>
    import Task from "./Task";
    import Editor from "../../../components/inputs/Editor.vue";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

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

                return YAML_UTILS.parse(value);
            }
        }
    };
</script>

<style lang="scss" scoped>
:deep(.placeholder) {
    top: -7px !important;
}
</style>
