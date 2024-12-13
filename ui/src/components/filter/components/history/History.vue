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
            <el-dropdown-menu class="py-2 history-dropdown">
                <p class="pt-3 title">
                    {{ t("filters.save.label") }}
                </p>
                <div class="overflow-x-auto saved scroller">
                    <el-dropdown-item
                        v-if="!saved.length"
                        @click="emits('search', {})"
                        class="pe-none"
                    >
                        <small class="text-secondary label">
                            {{ t("filters.save.empty") }}
                        </small>
                    </el-dropdown-item>
                    <el-dropdown-item
                        v-for="(item, index) in saved"
                        :key="index"
                        @click="emits('search', item.value)"
                    >
                        <div class="d-flex align-items-center w-100 item">
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

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import KestraIcon from "../../../Kicon.vue";
    import Label from "../Label.vue";

    import History from "vue-material-design-icons/History.vue";
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";

    const emits = defineEmits(["search"]);
    const props = defineProps({prefix: {type: String, required: true}});

    import {useFilters} from "../../useFilters.js";
    const {getSavedItems, removeSavedItem} = useFilters(props.prefix);

    let saved = ref([]);

    const loadAll = () => {
        saved.value = getSavedItems().reverse();
    };

    loadAll();

    const remove = (index) => {
        removeSavedItem(saved.value[index]);
        saved.value.splice(index, 1);
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

.item {
    .el-tag {
        background: var(--bs-border-color);
        color: var(--bs-gray-900);
    }

    .small {
        font-size: var(--el-font-size-extra-small);
    }
}
</style>
