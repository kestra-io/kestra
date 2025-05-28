<template>
    <el-input :model-value="JSON.stringify(values)">
        <template #append>
            <el-button :icon="TextSearch" @click="isOpen = true" />
        </template>
    </el-input>

    <drawer
        v-if="isOpen"
        v-model="isOpen"
        :title="root"
    >
        <template #header>
            <code>{{ root }}</code>
        </template>
        <el-form label-position="top">
            <task-editor
                ref="editor"
                :section="SECTIONS.TRIGGERS"
                :model-value="taskYaml"
                @update:model-value="onInput"
            />
        </el-form>

        <template #footer>
            <el-button :icon="ContentSave" @click="isOpen = false" type="primary">
                {{ $t('save') }}
            </el-button>
        </template>
    </drawer>
</template>

<script setup>
    import {YamlUtils as YAML_UTILS, SECTIONS} from "@kestra-io/ui-libs";

    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
</script>

<script>
    import Task from "./Task"
    import TaskEditor from "../TaskEditor.vue"
    import Drawer from "../../Drawer.vue"

    export default {
        mixins: [Task],
        components: {TaskEditor, Drawer},
        emits: ["update:modelValue"],
        data() {
            return {
                isOpen: false,
            };
        },
        computed: {
            taskYaml() {
                return YAML_UTILS.stringify(this.modelValue);
            }
        },
        methods: {
            onInput(value) {
                this.$emit("update:modelValue", YAML_UTILS.parse(value));
            },
        },
    };
</script>

