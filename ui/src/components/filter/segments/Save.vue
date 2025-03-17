<template>
    <el-tooltip v-if="disabled" :content="t('filters.save.tooltip')">
        <el-button
            disabled
            :icon="Save"
            @click="toggle(true)"
            class="rounded-0 rounded-end"
        />
    </el-tooltip>

    <KestraIcon
        v-else
        :tooltip="$t('filters.save.dialog.heading')"
        placement="bottom"
    >
        <el-button
            :icon="Save"
            @click="toggle(true)"
            class="rounded-0 rounded-end"
        />
    </KestraIcon>

    <el-dialog
        v-model="visible"
        :title="t('filters.save.dialog.heading')"
        :width="540"
        align-center
        append-to-body
        @opened="input?.focus"
    >
        <section class="pb-3">
            <span class="text-secondary">
                {{ t("filters.save.dialog.hint") }}
            </span>
            <el-input
                ref="input"
                v-model="label"
                :placeholder="t('filters.save.dialog.placeholder')"
                class="pt-2 bg-transparent"
                @keydown.enter.prevent="save()"
            />
        </section>
        <section class="items">
            <el-tag v-for="(item, index) in current" :key="index" class="m-1">
                <Label :option="item" />
            </el-tag>
        </section>
        <template #footer>
            <div class="dialog-footer">
                <el-button @click="toggle()">
                    {{ t("cancel") }}
                </el-button>
                <el-button :disabled="!label" type="primary" @click="save()">
                    {{ t("save") }}
                </el-button>
            </div>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import {PropType, getCurrentInstance, ref} from "vue";
    import {ElInput} from "element-plus";

    import {CurrentItem} from "../utils/types";

    import KestraIcon from "../../Kicon.vue";
    import Label from "../components/Label.vue";

    import {Save} from "../utils/icons";

    const toast = getCurrentInstance()?.appContext.config.globalProperties.$toast();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const props = defineProps({
        disabled: {type: Boolean, default: true},
        prefix: {type: String, required: true},
        current: {type: Object as PropType<CurrentItem[]>, required: true},
    });

    import {useFilters} from "../composables/useFilters";
    const {getSavedItems, setSavedItems} = useFilters(props.prefix);

    const visible = ref(false);
    const toggle = (isVisible = false) => {
        visible.value = isVisible;

        // Clearing input each time dialog closes
        if (!isVisible) label.value = "";
    };

    const input = ref<InstanceType<typeof ElInput> | null>(null);
    const label = ref("");
    const save = () => {
        const items = getSavedItems();

        setSavedItems([...items, {name: label.value, value: props.current}]);

        toggle();

        toast.saved(t("filters.save.dialog.confirmation", {name: label.value}));
    };
</script>

<style scoped lang="scss">
@import "../styles/filter";
</style>
