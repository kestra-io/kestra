<template>
    <el-cascader-panel ref="cascader" :options="props.options">
        <template #default="{data}">
            <div class="node">
                <div :title="data.label">
                    {{ data.label }}
                </div>
                <div v-if="data.value && data.children">
                    <code>{{ itemsCount(data) }}</code>
                </div>
            </div>
        </template>
    </el-cascader-panel>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {Node} from "./Cascader.vue";

    const props = defineProps<{ options: Record<string, any> }>();

    const itemsCount = (item: Node) => {
        const length = item.children?.length ?? 0;

        if (!length) return undefined;

        return `${length} ${length === 1 ? t("item") : t("items")}`;
    };

    const cascader = ref<any>(null);
    onMounted(() => {
        // Open first node by default on page mount
        if (cascader?.value) {
            const nodes = cascader.value.$el.querySelectorAll(".el-cascader-node");
            if (nodes.length > 0) (nodes[0] as HTMLElement).click();
        }
    });
</script>
