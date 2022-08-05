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
        v-model="isOpen"
        :title="root"
        destroy-on-close
        size=""
        :append-to-body="true"
    >
        <el-form label-position="top">
            <task-object
                v-if="schema"
                :model-value="modelValue"
                @update:model-value="onInput"
                :schema="schema"
                :definitions="definitions"
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
    export default {
        mixins: [Task],
        data() {
            return {
                isOpen: false,
            };
        },
    };
</script>
