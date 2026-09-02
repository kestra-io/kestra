<template>
    <section id="filtering">
        <KsSearch
            v-model="search"
            :placeholder="$t(`dependency.search.placeholders.${assetView ? 'asset' : 'default'}`)"
            clearable
        />

        <KsSelect
            v-model="namespace"
            :placeholder="$t('dependency.search.namespace.select')"
            clearable
            filterable
        >
            <KsOption
                v-for="item in namespaces"
                :key="item.value"
                :label="item.label"
                :value="item.value"
            />
        </KsSelect>

        <div class="filter-row">
            <KsSwitch
                v-if="assetView"
                v-model="flow"
                size="small"
                :activeText="$t('dependency.search.flow.display')"
            />
            <KsText size="small" class="result-count">{{ shown }} / {{ total }}</KsText>
        </div>
    </section>
</template>

<script setup lang="ts">
    defineProps<{
        assetView: boolean;
        namespaces: {label: string; value: string}[];
        shown: number;
        total: number;
    }>()

    const search = defineModel<string>("search", {required: true})
    const namespace = defineModel<string | undefined>("namespace", {required: true})
    const flow = defineModel<boolean>("flow", {required: true})
</script>

<style scoped lang="scss">
    section#filtering {
        position: sticky;
        top: 0;
        z-index: 10;
        padding: var(--ks-spacing-4);
        background-color: var(--ks-bg-input);
        border-bottom: 1px solid var(--ks-border-default);

        :deep(.kel-input__wrapper), :deep(.kel-select__wrapper) {
            margin-bottom: var(--ks-spacing-2);
            font-size: var(--ks-font-size-sm);
        }
    }

    .filter-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);

        .result-count {
            margin-left: auto;
            white-space: nowrap;
            color: var(--ks-text-muted);
        }
    }
</style>
