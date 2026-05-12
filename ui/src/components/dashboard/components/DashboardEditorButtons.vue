<template>
    <div class="button-top">
        <ValidationError 
            class="mx-3"
            tooltipPlacement="bottom-start"
            :errors="dashboardStore.errors"
            :warnings="dashboardStore.warnings"
        />

        <KsButton
            :icon="ContentSave"
            @click="emit('save')"
            :type="saveButtonType"
            :disabled="!canSave"
        >
            {{ $t("save") }}
        </KsButton>
    </div>
</template>

<script lang="ts" setup>
    import {computed} from "vue"
    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import ValidationError from "../../flows/ValidationError.vue"
    import {useDashboardStore} from "../../../stores/dashboard"

    const emit = defineEmits<{
        (e: "save"): void;
    }>()

    const dashboardStore = useDashboardStore()

    const saveButtonType = computed(() => {
        if (dashboardStore.errors) return "danger"
        return dashboardStore.warnings ? "warning" : "primary"
    })

    const canSave = computed(() => dashboardStore.haveChange || dashboardStore.isCreating)
</script>
<style lang="scss" scoped>
    .button-top {
        background: none;
        border: none;
    }
</style>
