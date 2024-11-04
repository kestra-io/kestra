<template>
    <el-button :disabled @click="toggle(true)" :icon="Save" />

    <el-dialog
        @opened="input?.focus"
        v-model="visible"
        :title="t('filters.save.dialog.heading')"
        :width="400"
        align-center
    >
        <section class="pb-3">
            <span class="text-secondary">
                {{ t("filters.save.dialog.hint") }}
            </span>
            <el-input
                ref="input"
                v-model="label"
                @keydown.enter.prevent="save()"
                :placeholder="t('filters.save.dialog.placeholder')"
                class="pt-1"
            />
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
    import {getCurrentInstance, ref} from "vue";
    import {ElInput} from "element-plus";

    const toast = getCurrentInstance()?.appContext.config.globalProperties.$toast();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import Save from "vue-material-design-icons/ContentSaveOutline.vue";

    const props = defineProps({
        disabled: {type: Boolean, default: true},
        prefix: {type: String, required: true},
        current: {type: Object, required: true},
    });

    import {useFilters} from "../filters";
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
        toast.saved(t("filters.save.dialog.confirmation", {name: label.value}));

        toggle();
    };
</script>
