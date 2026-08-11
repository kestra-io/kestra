<template>
    <KsSearch
        v-model="search"
        :placeholder="$t('search')"
        clearable
    />

    <div class="items">
        <Item
            v-for="dashboard in filtered"
            :key="dashboard.id"
            :dashboard
            :active="dashboard.id === selected?.id"
            @set-default="$emit('setDefault', $event)"
            @edit="$emit('edit', $event)"
            @remove="$emit('remove', $event)"
            @click="$emit('select', dashboard.id)"
        />
        <span v-if="!filtered.length">
            {{ $t("dashboards.empty") }}
        </span>
    </div>

    <KsDivider class="divider" />

    <KsButton
        type="primary"
        :icon="Plus"
        tag="router-link"
        :to="{name: 'dashboards/create', query}"
        class="w-100"
    >
        {{ $t("dashboards.creation.label") }}
    </KsButton>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import Item from "./Item.vue"
    import Plus from "vue-material-design-icons/Plus.vue"

    type Dashboard = {id: string; title: string; isDefault: boolean};

    const props = defineProps<{
        dashboards: Dashboard[];
        selected: {id: string; title: string} | undefined;
        query: Record<string, unknown>;
    }>()

    defineEmits<{
        select: [id: string];
        setDefault: [id: string];
        edit: [id: string];
        remove: [dashboard: {id: string; title: string}];
    }>()

    const search = ref("")
    const filtered = computed(() =>
        props.dashboards.filter((d) => !search.value || d.title.toLowerCase().includes(search.value.toLowerCase())),
    )
</script>

<style scoped lang="scss">
.items {
    max-height: 12rem; // ~5 visible items before scrolling
    overflow-y: auto;
    margin-top: var(--ks-spacing-2);
}

.divider {
    margin: var(--ks-spacing-2) 0;
}
</style>
