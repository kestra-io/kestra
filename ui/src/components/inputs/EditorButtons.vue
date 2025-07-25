<template>
    <div v-if="!isNamespace && (isAllowedEdit || canDelete)">
        <el-dropdown>
            <el-button type="default" :disabled="isReadOnly">
                <DotsVertical title="" />
                {{ t("actions") }}
            </el-button>
            <template #dropdown>
                <el-dropdown-menu class="m-dropdown-menu">
                    <el-dropdown-item
                        v-if="isAllowedEdit"
                        :icon="Download"
                        size="large"
                        @click="forwardEvent('export')"
                    >
                        {{ t("flow_export") }}
                    </el-dropdown-item>
                    <el-dropdown-item
                        v-if="!isCreating && canDelete"
                        :icon="Delete"
                        size="large"
                        @click="forwardEvent('delete-flow', $event)"
                    >
                        {{ t("delete") }}
                    </el-dropdown-item>

                    <el-dropdown-item
                        v-if="!isCreating"
                        :icon="ContentCopy"
                        size="large"
                        @click="forwardEvent('copy', $event)"
                    >
                        {{ t("copy") }}
                    </el-dropdown-item>
                </el-dropdown-menu>
            </template>
        </el-dropdown>
    </div>
    <div>
        <el-button
            v-if="isNamespace || isAllowedEdit"
            :icon="ContentSave"
            @click="forwardEvent('save', $event)"
            :type="playgroundStore.enabled ? undefined : 'primary'"
            :class="{'el-button--playground': playgroundStore.enabled}"
            :disabled="hasErrors || !canSave"
            class="edit-flow-save-button"
        >
            {{ t("save") }}
        </el-button>
    </div>
</template>
<script lang="ts" setup>
    import {computed} from "vue";
    import {useStore} from "vuex";

    import {useI18n} from "vue-i18n";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";

    import Delete from "vue-material-design-icons/Delete.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import {usePlaygroundStore} from "../../stores/playground";

    const store = useStore();
    const playgroundStore = usePlaygroundStore();

    const {t} = useI18n();

    const props = defineProps<{
        isCreating: boolean;
        isReadOnly: boolean;
        canDelete: boolean;
        isAllowedEdit: boolean;
        haveChange: boolean;
        flowHaveTasks: boolean;
        errors: string[] | undefined;
        warnings: string[] | undefined;
        isNamespace: boolean;
    }>()

    const forwardEvent = defineEmits([
        "delete-flow",
        "copy",
        "save",
        "export"
    ])

    const hasErrors = computed(() => props.errors && props.errors.length > 0);

    const canSave = computed(() => {
        if (props.isNamespace) {
            return store.state.editor.current?.dirty || false;
        }
        return props.haveChange || props.isCreating;
    });
</script>