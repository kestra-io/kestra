<template>
    <el-cascader-panel ref="panelRef" :options="options" :id="id">
        <template #default="{data}">
            <div v-if="isFile(data.value)">
                <VarValue :value="data.value" :execution="execution" />
            </div>
            <div v-else class="w-100 d-flex justify-content-between">
                <div
                    class="pe-5 d-flex task label-container"
                    :title="data.label"
                >
                    {{ data.label }}
                </div>
                <div v-if="data.value && data.children">
                    <code>
                        {{ data.children.length }}
                        {{
                            data.children.length === 1 ? t("item") : t("items")
                        }}
                    </code>
                </div>
            </div>
        </template>
    </el-cascader-panel>
</template>

<script setup lang="ts">
    import {ref, onMounted, toRefs} from "vue";
    import VarValue from "../executions/VarValue.vue";
    import {useI18n} from "vue-i18n";

    const {t} = useI18n({useScope: "global"});

    const isFile = (data: any) =>
        typeof data === "string" && (data.startsWith("kestra:///") || data.startsWith("file://") || data.startsWith("nsfile://"));

    interface Options {
        label: string;
        value: [string, number, boolean];
        children?: Options[];
    }

    const props = defineProps<{ options: Options; execution: any; id: string }>();
    const {options, execution, id} = toRefs(props);

    const panelRef = ref<any>(null);
    let hasAutoExpanded = false;

    const expandOutputsPanel = () => {
        if (hasAutoExpanded) return;
        if (!id?.value || !id.value.includes("outputs")) return;

        const panelElement = panelRef.value?.$el ?? panelRef.value;
        if (!panelElement) return;

        const menus = panelElement.querySelectorAll(".el-cascader-menu");
        if (!menus || menus.length === 0) return;

        const firstNode = menus[0].querySelector(".el-cascader-node");
        if (!firstNode) return;

        (firstNode as HTMLElement).click();
        hasAutoExpanded = true;
    };

    onMounted(() => {
        expandOutputsPanel();
    });
</script>

<style lang="scss" scoped>
.label-container {
    white-space: nowrap;
    overflow-x: auto;
    overflow-y: hidden;
    text-overflow: ellipsis;
}
</style>
