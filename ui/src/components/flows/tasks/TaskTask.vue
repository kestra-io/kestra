<template>
    <el-input
        :model-value="JSON.stringify(values)"
        :disabled="true"
    >
        <template #append>
            <el-button :icon="Eye" @click="this.isOpen = true" />
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
                section="tasks"
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
    import Eye from "vue-material-design-icons/Eye.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
</script>

<script>
    import Task from "./Task"
    import YamlUtils from "../../../utils/yamlUtils";
    import TaskEditor from "../TaskEditor.vue"

    export default {
        mixins: [Task],
        components: {TaskEditor},
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

