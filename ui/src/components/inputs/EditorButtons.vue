<template>
    <div class="to-action-button">
        <div v-if="isAllowedEdit || canDelete" class="mx-2">
            <el-dropdown>
                <el-button type="default" :disabled="isReadOnly">
                    <DotsVertical title="" />
                    {{ $t("actions") }}
                </el-button>
                <template #dropdown>
                    <el-dropdown-menu class="m-dropdown-menu">
                        <el-dropdown-item
                            v-if="!isCreating && canDelete"
                            :icon="Delete"
                            size="large"
                            @click="forwardEvent('delete-flow', $event)"
                        >
                            {{ $t("delete") }}
                        </el-dropdown-item>

                        <el-dropdown-item
                            v-if="!isCreating"
                            :icon="ContentCopy"
                            size="large"
                            @click="forwardEvent('copy', $event)"
                        >
                            {{ $t("copy") }}
                        </el-dropdown-item>
                        <el-dropdown-item
                            v-if="isAllowedEdit"
                            :icon="Exclamation"
                            size="large"
                            @click="forwardEvent('open-new-error', null)"
                            :disabled="!flowHaveTasks"
                        >
                            {{ $t("add global error handler") }}
                        </el-dropdown-item>
                        <el-dropdown-item
                            v-if="isAllowedEdit"
                            :icon="LightningBolt"
                            size="large"
                            @click="forwardEvent('open-new-trigger', null)"
                            :disabled="!flowHaveTasks"
                        >
                            {{ $t("add trigger") }}
                        </el-dropdown-item>
                        <el-dropdown-item
                            v-if="isAllowedEdit"
                            :icon="FileEdit"
                            size="large"
                            @click="forwardEvent('open-edit-metadata', null)"
                        >
                            {{ $t("edit metadata") }}
                        </el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        </div>
        <div>
            <el-button
                :icon="ContentSave"
                @click="forwardEvent('save', $event)"
                v-if="isAllowedEdit"
                :type="flowError ? 'danger' : 'primary'"
                :disabled="!haveChange && !isCreating"
                class="edit-flow-save-button"
            >
                {{ $t("save") }}
            </el-button>
        </div>
    </div>
</template>
<script setup>
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Exclamation from "vue-material-design-icons/Exclamation.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import FileEdit from "vue-material-design-icons/FileEdit.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
</script>
<script>
    import {defineComponent} from "vue";

    export default defineComponent({
        props: {
            isCreating: {
                type: Boolean,
                default: false
            },
            isReadOnly: {
                type: Boolean,
                default: false
            },
            canDelete: {
                type: Boolean,
                default: false
            },
            isAllowedEdit: {
                type: Boolean,
                default: false
            },
            haveChange: {
                type: Boolean,
                default: false
            },
            flowHaveTasks: {
                type: Boolean,
                default: false
            },
            flowError: {
                type: String,
                default: null
            }
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            }
        }
    })
</script>