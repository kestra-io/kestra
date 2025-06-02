<template>
    <el-input :model-value="JSON.stringify(values)">
        <template #append>
            <el-button :icon="TextSearch" @click="isOpen = true" />
        </template>
    </el-input>

    <drawer
        v-if="isOpen"
        v-model="isOpen"
    >
        <template #header>
            <code>{{ root }}</code>
        </template>
        <el-form label-position="top">
            <task-editor
                ref="editor"
                :model-value="taskYaml"
                :section="section"
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
    import {SECTIONS} from "@kestra-io/ui-libs";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import TaskEditor from "../TaskEditor.vue"
    import Drawer from "../../Drawer.vue"
</script>

<script>
    import Task from "./Task"

    export default {
        inheritAttrs: false,
        mixins: [Task],
        emits: ["update:modelValue"],
        props: {
            section: {
                type: String,
                default: SECTIONS.TASKS
            },
        },
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
        }
    };
</script>

