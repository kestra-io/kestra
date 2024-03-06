<template>
    <el-input
        :model-value="JSON.stringify(values)"
        :disabled="true"
    >
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
            <task-object
                v-if="currentSchema"
                :model-value="modelValue"
                @update:model-value="onInput"
                :schema="currentSchema"
                :definitions="definitions"
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
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
</script>

<script>
    import Task from "./Task"
    import Drawer from "../../Drawer.vue"

    export default {
        mixins: [Task],
        components: {Drawer},
        data() {
            return {
                isOpen: false,
            };
        },
        computed: {
            currentSchema() {
                let ref = this.schema.$ref.substring(8);
                if (this.definitions[ref]) {
                    return this.definitions[ref];
                }
                return undefined;
            }
        },
    };
</script>
