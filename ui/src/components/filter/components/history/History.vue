<template>
    <el-dropdown
        trigger="click"
        placement="bottom-start"
        @visible-change="loadAll"
    >
        <el-button :icon="History" class="rounded-0 rounded-start" />

        <template #dropdown>
            <el-dropdown-menu class="py-2 history-dropdown">
                <p class="title">
                    {{ t("filters.recent.label") }}
                </p>
                <el-dropdown-item
                    v-if="!recents.length"
                    @click="emits('search', {})"
                >
                    <small class="text-secondary label">
                        {{ t("filters.recent.empty") }}
                    </small>
                </el-dropdown-item>
                <template v-else>
                    <el-dropdown-item
                        v-for="(recent, rIdx) in recents.slice(0, 5)"
                        :key="rIdx"
                        @click="emits('search', recent.value)"
                    >
                        <Item :item="recent">
                            <template #delete>
                                <DeleteOutline
                                    @click.stop="remove('recents', rIdx)"
                                />
                            </template>
                        </Item>
                    </el-dropdown-item>
                </template>

                <template v-if="saved.length">
                    <p class="pt-3 title">
                        {{ t("filters.save.label") }}
                    </p>
                    <div class="overflow-x-auto saved scroller">
                        <el-dropdown-item
                            v-for="(save, sIdx) in saved"
                            :key="sIdx"
                            @click="emits('search', save.value)"
                        >
                            <Item :item="save">
                                <template #delete>
                                    <DeleteOutline
                                        @click.stop="remove('saved', sIdx)"
                                    />
                                </template>
                            </Item>
                        </el-dropdown-item>
                    </div>
                </template>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script setup lang="ts">
    import {ref} from "vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import Item from "./Item.vue";

    import History from "vue-material-design-icons/History.vue";
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";

    const emits = defineEmits(["search"]);
    const props = defineProps({prefix: {type: String, required: true}});

    import {useFilters} from "../../filters.js";
    const {getRecentItems, removeRecentItem, getSavedItems, removeSavedItem} =
        useFilters(props.prefix);

    let recents = ref([]);
    let saved = ref([]);

    const loadAll = () => {
        recents.value = getRecentItems().reverse();
        saved.value = getSavedItems().reverse();
    };

    loadAll();

    const remove = (prefix, index) => {
        const list = prefix === "recents" ? recents : saved;
        const action = prefix === "recents" ? removeRecentItem : removeSavedItem;

        action(list.value[index]);
        list.value.splice(index, 1);
    };
</script>

<style lang="scss">
.history-dropdown {
    width: 400px;
}

.title {
    margin: 0;
    padding: calc(1rem / 4) 0 0 1rem;
    font-size: var(--el-font-size-extra-small);
    color: var(--bs-grey-700);
}

.saved {
    max-height: 170px !important; // 5 visible items
}

.scroller {
    &::-webkit-scrollbar {
        height: 5px;
        width: 5px;
    }

    &::-webkit-scrollbar-track {
        background: var(--card-bg);
    }

    &::-webkit-scrollbar-thumb {
        background: var(--bs-border-color);
        border-radius: 0px;
    }
}
</style>
