<script lang="ts" setup>
    import {nextTick, ref} from "vue"
    import {useI18n} from "vue-i18n";
    import {useStore} from "vuex";
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";
    import PencilOutline from "vue-material-design-icons/PencilOutline.vue";
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue";

    const {t} = useI18n();

    const $store = useStore()

    const props = defineProps<{
        href: string
        title: string
    }>()

    const editing = ref(false)
    const updatedTitle = ref(props.title)
    const titleInput = ref<{focus: () => void, select: () => void} | null>(null)

    function deleteBookmark() {
        $store.dispatch("starred/remove", {
            path: props.href
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
        $store.dispatch("starred/rename", {
            path: props.href,
            label: updatedTitle.value
        })
        editing.value = false
    }
</script>

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

<style scoped>
    .wrapper{
        position: relative;
        .buttons {
            color: var(--el-text-color-regular);
            position: absolute;
            z-index: 1;
            top: calc(.35 * var(--spacer));
            right: calc(.5 * var(--spacer));
            display: none;
            gap: calc(.5 * var(--spacer));
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
                top: calc(.5 * var(--spacer));
                right: calc(.5 * var(--spacer));
                z-index: 2;
                color: var(--el-text-color-regular);
                cursor: pointer;
            }
        }
    }
    a {
        display: block;
        padding: calc(.25 * var(--spacer)) calc(.5 * var(--spacer));
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        color: var(--el-text-color-regular);
        font-size: 0.875em;
        border-radius: 4px;
        &:hover{
            color: var(--el-text-color-secondary);
            background-color: var(--el-bg-color);
        }
    }


</style>
