<template>
    <el-dropdown
        trigger="click"
        placement="bottom-start"
        @visible-change="loadAll"
    >
        <KestraIcon :tooltip="$t('filters.save.label')" placement="bottom">
            <el-button :icon="History" class="rounded-0 rounded-start" />
        </KestraIcon>

        <template #dropdown>
            <el-dropdown-menu class="py-2 dropdown">
                <Title :text="t('filters.save.label')" />
                <div class="overflow-x-auto scroller items">
                    <el-dropdown-item
                        v-if="!saved.length"
                        @click="emits('search', {})"
                        class="pe-none"
                    >
                        <small class="empty">
                            {{ t("filters.save.empty") }}
                        </small>
                    </el-dropdown-item>
                    <el-dropdown-item
                        v-for="(item, index) in saved"
                        :key="index"
                        @click="emits('search', item.value)"
                    >
                        <div class="d-flex align-items-center w-100">
                            <div v-if="item.name" class="col-3 text-truncate">
                                <span class="small">{{ item.name }}</span>
                            </div>

                            <div
                                class="col flex-grow-1 overflow-auto text-nowrap"
                            >
                                <div class="me-3 overflow-x-auto scroller">
                                    <el-tag
                                        v-for="value in item.value"
                                        :key="value"
                                        class="me-2"
                                    >
                                        <span class="small">
                                            <Label :option="value" />
                                        </span>
                                    </el-tag>
                                </div>
                            </div>

                            <div class="col-auto">
                                <DeleteOutline @click.stop="remove(index)" />
                            </div>
                        </div>
                    </el-dropdown-item>
                </div>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script setup lang="ts">
    import {ref} from "vue";

    import {CurrentItem} from "../utils/types";

    import KestraIcon from "../../Kicon.vue";
    import Label from "../components/Label.vue";
    import Title from "../components/Title.vue";

    import {History, DeleteOutline} from "../utils/icons";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const emits = defineEmits(["search"]);
    const props = defineProps({prefix: {type: String, required: true}});

    import {useFilters} from "../composables/useFilters";
    const {getSavedItems, removeSavedItem} = useFilters(props.prefix);

    let saved = ref<{ value: CurrentItem[]; name: string }[]>([]);

    const loadAll = () => {
        saved.value = getSavedItems().reverse();
    };

    loadAll();

    const remove = (index: number) => {
        removeSavedItem(saved.value[index]);
        saved.value.splice(index, 1);
    };
</script>

<style scoped lang="scss">
@import "../styles/filter";

.dropdown {
    width: 400px;
}

.items {
    max-height: 170px !important; // 5 visible items
}
</style>
