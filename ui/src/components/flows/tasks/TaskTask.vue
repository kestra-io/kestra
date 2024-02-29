<template>
    <el-input
        :model-value="JSON.stringify(values)"
        :disabled="true"
    >
        <template #append>
            <el-button :icon="TextSearch" @click="isOpen = true" />
        </template>
    </el-input>

    <el-drawer
        v-if="isOpen"
        v-model="isOpen"
        destroy-on-close
        size=""
        :append-to-body="true"
    >
        <template #header>
            <code>{{ root }}</code>
        </template>
        <el-form label-position="top">
            <task-editor
                ref="editor"
                :model-value="taskYaml"
                :section="SECTIONS.TASKS"
                @update:model-value="onInput"
            />
        </el-form>
        <template #footer>
            <el-button :icon="ContentSave" @click="isOpen = false" type="primary">
                {{ $t('save') }}
            </el-button>
        </template>
    </el-drawer>
</template>

<script setup>
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import {SECTIONS} from "../../../utils/constants.js";
</script>

<script>
    import Task from "./Task"
    import YamlUtils from "../../../utils/yamlUtils";
    import TaskEditor from "../TaskEditor.vue"

    export default {
        mixins: [Task],
        components: {TaskEditor},
        emits: ["update:modelValue"],
        data() {
            return {
                isOpen: false,
            };
        },
        computed: {
            taskYaml() {
                return YamlUtils.stringify(this.modelValue);
            }
        },
        methods: {
            onInput(value) {
                this.$emit("update:modelValue", YamlUtils.parse(value));
            },
        }
    };
</script>

