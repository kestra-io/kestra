<template>
    <div class="dag-depends-on" data-test="dag-depends-on">
        <SourceBranch class="dag-depends-on-icon" aria-hidden="true" />
        <span class="dag-depends-on-label">{{ t("block_editor.depends_on_label") }}</span>
        <KsSelect
            :modelValue="dependsOn ?? []"
            multiple
            filterable
            size="small"
            class="dag-depends-on-select"
            :placeholder="t('block_editor.depends_on_placeholder')"
            :aria-label="t('block_editor.depends_on_label')"
            data-test="dag-depends-on-select"
            @click.stop
            @update:modelValue="onUpdate"
        >
            <KsOption
                v-for="id in siblingIds"
                :key="id"
                :label="id"
                :value="id"
            />
        </KsSelect>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import SourceBranch from "vue-material-design-icons/SourceBranch.vue"
    import {KsSelect, KsOption} from "@kestra-io/design-system"

    const {t} = useI18n()

    defineProps<{
        dependsOn?: string[]
        siblingIds: string[]
    }>()

    const emit = defineEmits<{
        (e: "update", dependsOn: string[]): void
    }>()

    function onUpdate(value: string[]) {
        emit("update", value)
    }
</script>

<style scoped lang="scss">
    .dag-depends-on {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) var(--ks-spacing-3) var(--ks-spacing-2);
        cursor: default;
    }

    .dag-depends-on-icon {
        flex-shrink: 0;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-icon-muted);
    }

    .dag-depends-on-label {
        flex-shrink: 0;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        white-space: nowrap;
    }

    .dag-depends-on-select {
        flex: 1;
        min-width: 0;
    }
</style>
