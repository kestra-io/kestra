<template>
    <div class="wrapper">
        <div v-if="editing" class="inputs">
            <el-input ref="titleInput" v-model="updatedTitle" @keyup.enter="renameBookmark" @keyup.esc="editing = false" />
            <CheckCircle @click.stop="renameBookmark" class="save" />
        </div>
        <div class="buttons">
            <PencilOutline @click="startEditBookmark" :title="t('edit')" />
            <DeleteOutline @click="deleteBookmark" :title="t('delete')" />
        </div>
        <a :href="href" :title="updatedTitle">
            {{ updatedTitle }}
        </a>
    </div>
</template>

<script lang="ts" setup>
    import {nextTick, ref} from "vue"
    import {useI18n} from "vue-i18n";
    import {useStore} from "vuex";
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";
    import PencilOutline from "vue-material-design-icons/PencilOutline.vue";
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue";
    import {ElMessageBox} from "element-plus";

    const {t} = useI18n({useScope: "global"});

    const $store = useStore()

    const props = defineProps<{
        href: string
        title: string
    }>()

    const editing = ref(false)
    const updatedTitle = ref(props.title)
    const titleInput = ref<{focus: () => void, select: () => void} | null>(null)

    function deleteBookmark() {
        ElMessageBox.confirm(t("remove_bookmark"), t("confirmation"), {
            type: "warning",
            confirmButtonText: t("ok"),
            cancelButtonText: t("close"),
        }).then(() => {
            $store.dispatch("bookmarks/remove", {path: props.href});
        });
    }

    function startEditBookmark() {
        editing.value = true
        nextTick(() => {
            titleInput.value?.focus()
            titleInput.value?.select()
        })
    }

    function renameBookmark() {
        $store.dispatch("bookmarks/rename", {
            path: props.href,
            label: updatedTitle.value
        })
        editing.value = false
    }
</script>

<style scoped>
    .wrapper{
        position: relative;
        .buttons {
            color: var(--ks-content-primary);
            position: absolute;
            align-items: center;
            z-index: 1;
            top: 0;
            right: 0;
            bottom: 0;
            display: none;
            gap: .5rem;
            background-color: var(--ks-background-button-secondary-hover);
            padding: .5rem;
            > span{
                cursor: pointer;
            }
        }

        &:hover .buttons {
            display: flex;
        }

        .inputs{
            width: 100%;
            position: absolute;
            top: 0;
            left: 0;
            z-index: 2;
            --el-input-height:18px;
            .el-input {
                font-size: 0.875em;
                &:deep(.el-input__wrapper) {
                    padding: 1px 8px;
                }
            }

            .save {
                position: absolute;
                top: .5rem;
                right: .5rem;
                z-index: 2;
                color: var(--ks-content-primary);
                cursor: pointer;
            }
        }
    }
    a {
        display: block;
        padding: .25rem .5rem;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        color: var(--ks-content-primary);
        font-size: 0.875em;
        border-radius: 4px;
        &:hover{
            color: var(--ks-content-link);
            background-color: var(--ks-button-background-secondary-hover);
        }
    }


</style>
