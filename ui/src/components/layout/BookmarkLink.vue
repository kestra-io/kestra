<template>
    <div class="bookmark-link" :class="{editing}">
        <div v-if="editing" class="edit-row">
            <KsInput
                class="bookmark-input"
                ref="titleInput"
                v-model="updatedTitle"
                @keyup.enter="renameBookmark"
                @keyup.esc="editing = false"
            />
            <CheckCircle @click.stop="renameBookmark" class="save" />
        </div>
        <template v-else>
            <router-link
                class="bookmark-anchor"
                :to="href"
                :title="updatedTitle"
            >
                <span class="bookmark-title">{{ updatedTitle }}</span>
                <div class="buttons">
                    <PencilOutline
                        @click.stop.prevent="startEditBookmark"
                        :title="$t('edit')"
                    />
                    <DeleteOutline
                        @click.prevent="deleteBookmark"
                        :title="$t('delete')"
                    />
                </div>
            </router-link>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {nextTick, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue"
    import PencilOutline from "vue-material-design-icons/PencilOutline.vue"
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
    import {KsMessageBox} from "@kestra-io/design-system"
    import {useBookmarksStore} from "../../stores/bookmarks"

    const {t} = useI18n({useScope: "global"})

    const props = defineProps<{
        href: string
        title: string
    }>()
    const bookmarksStore = useBookmarksStore()

    const editing = ref(false)
    const updatedTitle = ref(props.title)
    const titleInput = ref<{ focus: () => void; select: () => void } | null>(null)

    function deleteBookmark() {
        KsMessageBox.confirm(t("remove_bookmark"), t("confirmation"), {
            type: "warning",
            confirmButtonText: t("ok"),
            cancelButtonText: t("close"),
        }).then(() => {
            bookmarksStore.remove({path: props.href})
        })
    }

    function startEditBookmark() {
        editing.value = true
        nextTick(() => {
            titleInput.value?.focus()
            titleInput.value?.select()
        })
    }
    function renameBookmark() {
        bookmarksStore.rename({
            path: props.href,
            label: updatedTitle.value,
        })
        editing.value = false
    }
</script>

<style scoped>
.bookmark-link {
    display: flex;
    align-items: center;
    padding: 0.25rem 0;
    border-radius: var(--ks-radius-base);
    box-sizing: border-box;
}

.buttons {
    display: flex;
    align-items: center;
    gap: 0.25rem;
    margin-left: auto;
    padding-right: var(--ks-spacing-2);
    opacity: 0;
    visibility: hidden;
    transition: opacity 0.15s ease;
    flex-shrink: 0;
}

.bookmark-input {
    flex: 1;
    font-size: var(--ks-font-size-sm);
}

.edit-row {
    display: flex;
    align-items: center;
    width: 100%;
    gap: 0.5rem;
}

.save {
    cursor: pointer;
    color: var(--ks-text-primary);
}

.bookmark-anchor {
    display: flex;
    align-items: center;
    width: 100%;
    min-width: 0;
    text-decoration: none;
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-xs);
    font-weight: 600;
}

.bookmark-link:not(.editing) .bookmark-anchor:hover .buttons {
    opacity: 1;
    visibility: visible;
}

.bookmark-title {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    padding: 0.25rem 0.5rem;
    text-overflow: ellipsis;
}
</style>
