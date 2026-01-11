<template>
    <div class="button-top">
        <ValidationError
            class="mx-3"
            tooltipPlacement="bottom-start"
            :errors="dashboardStore.errors"
            :warnings="dashboardStore.warnings"
        />

        <!-- Save button is disabled when there are no unsaved changes -->
        <el-button
            :icon="ContentSave"
            @click="emit('save')"
            :type="saveButtonType"
            :disabled="!props.allowSaveUnchanged && props.source === props.initialSource"
        >
            {{ $t("save") }}
        </el-button>
    </div>
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import ValidationError from "../../flows/ValidationError.vue";
    import {useDashboardStore} from "../../../stores/dashboard";

    // Accept editor state from parent (needed to detect unsaved changes)
    const props = defineProps<{
        source?: string;
        initialSource?: string;
        allowSaveUnchanged?: boolean;
    }>();

    const emit = defineEmits<{
        (e: "save"): void;
    }>();

    const dashboardStore = useDashboardStore();

    const saveButtonType = computed(() => {
        if (dashboardStore.errors) return "danger";
        return dashboardStore.warnings ? "warning" : "primary";
    });
</script>

<style lang="scss" scoped>
.button-top {
    background: none;
    border: none;
}
</style>
