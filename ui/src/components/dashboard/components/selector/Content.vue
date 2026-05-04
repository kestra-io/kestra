<template>
    <el-button
        type="primary"
        :icon="Plus"
        tag="router-link"
        :to="{name: 'dashboards/create', query}"
        class="w-100"
    >
        <small>{{ $t("dashboards.creation.label") }}</small>
    </el-button>

    <Item
        :dashboard="{
            id: filtered.filter(d => d.id === selected?.id)?.[0]?.id ?? 'default',
            title: (selected?.title ?? $t('dashboards.default')),
            isDefault: filtered.filter(d => d.id === selected?.id)?.[0]?.isDefault
        }"
        :edit="(id: string) => $emit('edit', id)"
        :setAsDefault="(id: string) => $emit('setDefault', id)"
        class="mt-3"
    />

    <hr class="my-2">

    <el-input
        v-model="search"
        :placeholder="$t('search')"
        :prefixIcon="Magnify"
        clearable
        class="my-1 mb-3 search"
    />

    <div class="overflow-x-auto items">
        <Item
            v-for="(dashboard, index) in filtered"
            :key="index"
            :dashboard
            :edit="(id: string) => $emit('edit', id)"
            :remove="(d: {id: string; title: string}) => $emit('remove', d)"
            :setAsDefault="(id: string) => $emit('setDefault', id)"
            @click="$emit('select', dashboard.id)"
        />
        <span v-if="!filtered.length" class="empty">
            {{ $t("dashboards.empty") }}
        </span>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue";
    import Item from "./Item.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";

    type Dashboard = {id: string; title: string; isDefault: boolean};

    const props = defineProps<{
        dashboards: Dashboard[];
        selected: {id: string; title: string} | undefined;
        query: Record<string, unknown>;
    }>();

    defineEmits<{
        select: [id: string];
        setDefault: [id: string];
        edit: [id: string];
        remove: [dashboard: {id: string; title: string}];
    }>();

    const search = ref("");
    const filtered = computed(() =>
        props.dashboards.filter((d) => !search.value || d.title.toLowerCase().includes(search.value.toLowerCase()))
    );
</script>

<style scoped lang="scss">
.search {
    font-size: revert;
}

.items {
    max-height: 193.4px !important; // 5 visible items

    :deep(li.el-dropdown-menu__item) {
        border-radius: unset;
    }
}

:deep(li.el-dropdown-menu__item) {
    &:hover,
    &:focus {
        background: var(--ks-select-hover);
    }
}
</style>
