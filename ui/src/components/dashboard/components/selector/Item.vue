<template>
    <KsDropdownItem
        class="dashboard-row"
        :class="{active}"
        :icon="active ? CheckBold : undefined"
    >
        <span class="label">{{ dashboard.title }}</span>

        <KsIconButton
            v-if="dashboard.id !== 'default'"
            class="action bookmark"
            :class="{'is-default': dashboard.isDefault}"
            :tooltip="$t('default')"
            @click.stop="setAsDefault(dashboard.id)"
        >
            <component :is="dashboard.isDefault ? Bookmark : BookmarkOutline" />
        </KsIconButton>

        <span class="spacer" />

        <KsIconButton
            v-if="dashboard.id !== 'default'"
            class="action"
            :tooltip="$t('edit')"
            @click.stop="edit(dashboard.id)"
        >
            <Pencil />
        </KsIconButton>
        <KsIconButton
            v-if="dashboard.id !== 'default' && remove"
            class="action mx-0"
            :tooltip="$t('delete')"
            @click.stop="remove(dashboard)"
        >
            <DeleteOutline />
        </KsIconButton>
    </KsDropdownItem>
</template>

<script setup lang="ts">
    import Bookmark from "vue-material-design-icons/Bookmark.vue"
    import BookmarkOutline from "vue-material-design-icons/BookmarkOutline.vue"
    import CheckBold from "vue-material-design-icons/CheckBold.vue"
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue"
    import Pencil from "vue-material-design-icons/Pencil.vue"

    defineProps<{
        dashboard: {id: string, title: string, isDefault: boolean},
        active?: boolean,
        setAsDefault: (id: string) => void,
        edit: (id: string) => void,
        remove?: (dashboard: {id: string, title: string}) => void}>()
</script>

<style scoped lang="scss">
.dashboard-row {
    .label {
        flex: 0 1 auto;
        min-width: 0;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        font-size: var(--ks-font-size-sm);
    }

    .spacer {
        flex: 1 1 auto;
    }
}
</style>