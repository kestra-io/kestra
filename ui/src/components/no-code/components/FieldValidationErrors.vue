<template>
    <div v-if="errors.length" role="alert">
        <span
            v-for="(error, index) in errors"
            :key="`${index}:${error}`"
            class="field-error"
            :data-test="dataTest"
        >
            <AlertCircleOutline class="field-error-icon" aria-hidden="true" />
            {{ error }}
        </span>
    </div>
</template>

<script setup lang="ts">
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"

    withDefaults(defineProps<{errors: string[], dataTest?: string}>(), {dataTest: "field-validation-error"})
</script>

<style scoped lang="scss">
.field-error {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    margin-top: var(--ks-spacing-1);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-error);
}

.field-error-icon {
    display: inline-flex;
    flex-shrink: 0;
    font-size: var(--ks-font-size-sm);
}
</style>
