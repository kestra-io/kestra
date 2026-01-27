<template>
    <div class="wrapper vsm--item" :class="{editing}">
        <div v-if="editing" class="edit-row">
            <el-input
                class="vsm--input"
                ref="titleInput"
                v-model="updatedTitle"
                @keyup.enter="renameBookmark"
                @keyup.esc="editing = false"
            />
            <CheckCircle @click.stop="renameBookmark" class="save" />
        </div>
        <template v-else>
            <a
                class="vsm--link vsm--link_level-2"
                :href="href"
                :title="updatedTitle"
            >
                <div class="vsm--title">
                    <span>{{ updatedTitle }}</span>
                </div>
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
            </a>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {nextTick, ref} from "vue"
    import {useI18n} from "vue-i18n";
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";
    import PencilOutline from "vue-material-design-icons/PencilOutline.vue";
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue";
    import {ElMessageBox} from "element-plus";
    import {useBookmarksStore} from "../../stores/bookmarks";

    const {t} = useI18n({useScope: "global"});

    const props = defineProps<{
        href: string
        title: string
    }>()
    const bookmarksStore = useBookmarksStore();

    const editing = ref(false);
    const updatedTitle = ref(props.title);
    const titleInput = ref<{ focus: () => void; select: () => void } | null>(null);

    function deleteBookmark() {
        ElMessageBox.confirm(t("remove_bookmark"), t("confirmation"), {
            type: "warning",
            confirmButtonText: t("ok"),
            cancelButtonText: t("close"),
        }).then(() => {
            bookmarksStore.remove({path: props.href});
        });
    }

    function startEditBookmark() {
        editing.value = true;
        nextTick(() => {
            titleInput.value?.focus();
            titleInput.value?.select();
        });
    }
    function renameBookmark() {
        bookmarksStore.rename({
            path: props.href,
            label: updatedTitle.value
        })
        editing.value = false
    }
</script>

<style scoped>
.wrapper {
    position: relative;
    display: flex;
    align-items: center;
    padding: 0.25rem 0.5rem;
    overflow: hidden;
    border-radius: 0.25rem;
    box-sizing: border-box;
}

.buttons {
    position: absolute;
    right: 2rem;
    top: 50%;
    transform: translateY(-50%);
    display: flex;
    gap: 0.25rem;
    opacity: 0;
    visibility: hidden;
    transition: opacity 0.15s ease;
    z-index: 10;
}

.vsm--input {
    flex: 1;
    font-size: 0.875em;
}

.edit-row {
    display: flex;
    align-items: center;
    width: 100%;
    gap: 0.5rem;
}

.save {
    cursor: pointer;
    color: var(--ks-content-primary);
}

.vsm--link {
    position: relative;
    z-index: 1;
    display: inline-flex;
    max-width: 100%;
    width: 100%;
    text-decoration: none;
    color: var(--ks-content-primary);
    font-size: 0.875em;
}

.wrapper:not(.editing) .vsm--link:hover .buttons {
    margin-right: 1rem;
    opacity: 1;
    visibility: visible;
}

.vsm--title {
    overflow: hidden;
    white-space: nowrap;
    padding: 0.25rem 0.5rem;
    text-overflow: ellipsis;
    max-width: calc(100% - 2.5rem);
}
</style>
