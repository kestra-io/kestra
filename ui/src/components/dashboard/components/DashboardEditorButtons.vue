<template>
    <div class="button-top">
        <ValidationError 
            class="mx-3"
            tooltipPlacement="bottom-start"
            :errors="dashboardStore.errors"
            :warnings="dashboardStore.warnings"
        />

        <el-button
            :icon="ContentSave"
            @click="emit('save')"
            :type="saveButtonType"
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
