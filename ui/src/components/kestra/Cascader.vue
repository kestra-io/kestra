<template>
    <el-cascader-panel ref="panelRef" :options>
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
                        {{ data.children.length === 1 ? $t("item") : $t("items") }}
                    </code>
                </div>
            </div>
        </template>
    </el-cascader-panel>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue";

    import VarValue from "../executions/VarValue.vue";

    const isFile = (data: any) => typeof data === "string" && (data.startsWith("kestra:///") || data.startsWith("file://") || data.startsWith("nsfile://"));

    interface Options {
        label: string;
        value: [string, number, boolean];
        children?: Options[];
    }

    defineProps<{ options: Options; execution: any }>();
        
    const panelRef = ref<any>(null);

    onMounted(() => {
        const nodes =  panelRef.value.$el.querySelectorAll(".el-cascader-node");
        if(nodes.length > 0) (nodes[0] as HTMLElement).click();
    });
</script>

<style scoped lang="scss">
.label-container {
    white-space: nowrap;
    overflow-x: auto;
    overflow-y: hidden;
    text-overflow: ellipsis;
}
</style>