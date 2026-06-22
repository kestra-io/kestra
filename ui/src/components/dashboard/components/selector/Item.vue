<template>
    <KsDropdownItem class="dashboard-item">
        <div class="dashboard-row">
            <span class="lead">
                <KsIcon v-if="active">
                    <CheckBold />
                </KsIcon>
            </span>

            <span class="label">{{ dashboard.title }}</span>

            <span v-if="dashboard.id !== 'default'" class="actions">
                <span class="action">
                    <KsIconButton
                        :tooltip="$t('default')"
                        placement="top"
                        @click.stop="$emit('setDefault', dashboard.id)"
                    >
                        <component :is="dashboard.isDefault ? Bookmark : BookmarkOutline" />
                    </KsIconButton>
                </span>
                <span class="action">
                    <KsIconButton
                        :tooltip="$t('edit')"
                        placement="top"
                        @click.stop="$emit('edit', dashboard.id)"
                    >
                        <Pencil />
                    </KsIconButton>
                </span>
                <span class="action">
                    <KsIconButton
                        :tooltip="$t('delete')"
                        placement="top"
                        @click.stop="$emit('remove', dashboard)"
                    >
                        <DeleteOutline />
                    </KsIconButton>
                </span>
            </span>
        </div>
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
        active?: boolean}>()

    defineEmits<{
        setDefault: [id: string],
        edit: [id: string],
        remove: [dashboard: {id: string, title: string}]}>()
</script>

<style scoped lang="scss">
.dashboard-row {
    display: flex;
    align-items: center;
    width: 100%;
    gap: var(--ks-spacing-1);
    min-height: 1.75rem;

    .lead {
        flex: 0 0 auto;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-icon-size-sm);
    }

    .label {
        flex: 1 1 auto;
        min-width: 0;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        font-size: var(--ks-font-size-sm);
    }

    .actions {
        flex: 0 0 auto;
        display: none;
        align-items: center;
        gap: var(--ks-spacing-1);

        .material-design-icon {
            color: var(--ks-icon-muted);
        }
    }
}

.dashboard-item:hover .actions {
    display: inline-flex;
}
</style>
