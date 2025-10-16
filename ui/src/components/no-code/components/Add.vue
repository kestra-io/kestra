<template>
    <button @click="emit('add', what)" class="py-2 adding" type="button">
        {{
            what
                ? t("no_code.adding", {what})
                : t("no_code.adding_default")
        }}
    </button>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const emit = defineEmits<{
        (e: "add", what: string | undefined): void;
    }>();

    defineProps<{
        what?: string;
    }>();
</script>

<style scoped lang="scss">
@import "../styles/code.scss";

button {
    background: transparent;
    border: none;
}

.adding {
    cursor: pointer;
    color: var(--ks-content-secondary);
    font-size: $code-font-sm;
    transition: all 0.2s ease;

    &:hover {
        background-color: var(--ks-button-background-secondary-hover);
        color: var(--ks-content-link-hover);
    }
}
</style>
