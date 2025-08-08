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
            {{ current }}
        </section>
        <template #footer>
            <div class="dialog-footer">
                <el-button @click="toggle()">
                    {{ t("cancel") }}
                </el-button>
                <el-button v-if="validationErrorMessage" disabled type="danger">
                    {{ validationErrorMessage }}
                </el-button>
                <el-button v-if="!validationErrorMessage" :disabled="!label" type="primary" @click="save()">
                    {{ t("save") }}
                </el-button>
            </div>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import {computed, getCurrentInstance, ref} from "vue";
    import {ElInput} from "element-plus";
    import KestraIcon from "../../Kicon.vue";
    import {Save} from "../utils/icons";
    import {useI18n} from "vue-i18n";
    import {useFilters} from "../composables/useFilters";

    const toast = getCurrentInstance()?.appContext.config.globalProperties.$toast();

    const {t} = useI18n({useScope: "global"});

    const props = withDefaults(defineProps<{
        disabled?: boolean,
        prefix: string,
        current: string,
    }>(),{
        disabled: true,
    });

    const {getSavedItems, setSavedItems} = useFilters(props.prefix);

    const visible = ref(false);
    const toggle = (isVisible = false) => {
        visible.value = isVisible;

        // Clearing input each time dialog closes
        if (!isVisible) label.value = "";
    };

    const input = ref<InstanceType<typeof ElInput> | null>(null);
    const label = ref("");

    const validationErrorMessage = computed(() =>{
        const items = getSavedItems();
        if(items && items.map(i => i.name).find(name => name === label.value)){
            return t("filters.save.name_already_used");
        } else {
            return null;
        }
    });

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
