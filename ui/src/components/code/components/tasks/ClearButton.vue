<template>
    <button
        class="clear-button"
        type="button"
        @click="emit('click')"
    >
        <CloseIcon class="clear-icon" />{{ t("no_code.clearSelection") }}
    </button>
</template>

<script lang="ts" setup>
    import CloseIcon from "vue-material-design-icons/Close.vue";
    import {useI18n} from "vue-i18n";
    const {t} = useI18n();
    const emit = defineEmits(["click"]);
</script>

<style scoped>
    .clear-button{
        border: none;
        background: transparent;
        color: var(--ks-content-link);
        display: flex;
        align-items: center;
        gap: 0.5rem;
    }

    .clear-icon * {
        font-size: 20px;
    }
    .clear-icon {
        height: 20px;
    }
</style>