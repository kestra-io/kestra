<template>
    <div class="button-top">
        <ValidationError class="mx-3" tooltipPlacement="bottom-start" :errors="errors" />

        <el-button
            :icon="ContentSave"
            @click="emit('save')"
            :type="saveButtonType"
        >
            {{ t("save") }}
        </el-button>
    </div>
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import ValidationError from "../../flows/ValidationError.vue";

    const {t} = useI18n();

    const emit = defineEmits<{
        (e: "save"): void;
    }>();

    const props = defineProps<{
        warnings?: string[];
        errors?: string[];
        disabled?: boolean;
    }>();

    const saveButtonType = computed(() => {
        if (props.errors) return "danger";
        return props.warnings ? "warning" : "primary";
    });
</script>
<style lang="scss" scoped>
    .button-top {
        background: none;
        border: none;
    }
</style>
