<template>
    <div v-if="!isNamespace && (isAllowedEdit || canDelete)" class="me-2">
        <el-dropdown>
            <el-button type="default" :disabled="isReadOnly">
                <DotsVertical title="" />
                {{ $t("actions") }}
            </el-button>
            <template #dropdown>
                <el-dropdown-menu class="m-dropdown-menu">
                    <el-dropdown-item
                        v-if="isAllowedEdit"
                        :icon="Download"
                        size="large"
                        @click="forwardEvent('export')"
                    >
                        {{ $t("export_to_file") }}
                    </el-dropdown-item>
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
                </el-dropdown-menu> 
            </template>
        </el-dropdown>
    </div>
    <div>
        <el-button
            :icon="ContentSave"
            @click="forwardEvent('save', $event)"
            v-if="isAllowedEdit"
            :type="buttonType"
            :disabled="hasErrors || !haveChange && !isCreating"
            class="edit-flow-save-button"
        >
            {{ $t("save") }}
        </el-button>
    </div>
</template>
<script setup>
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    
    import Delete from "vue-material-design-icons/Delete.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Download from "vue-material-design-icons/Download.vue";
</script>
<script>
    import {defineComponent} from "vue";

    export default defineComponent({
        emits: [
            "delete-flow",
            "copy",
            "save",
            "export"
        ],
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
            errors: {
                type: Array,
                default: undefined
            },
            warnings: {
                type: Array,
                default: undefined
            },
            isNamespace: {
                type: Boolean,
                default: false
            }
        },
        computed: {
            hasErrors(){
                return this.errors && this.errors.length > 0;
            },
            buttonType() {
                if (this.errors) {
                    return "danger";
                }

                return this.warnings
                    ? "warning"
                    : "primary";
            }
        },
        methods: {
            forwardEvent(type, event) {
                this.$emit(type, event);
            }
        }
    })
</script>