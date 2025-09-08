<template>
    <el-collapse v-model="expanded" class="collapse">
        <el-collapse-item
            :name="title"
            :title="`${title}${elements ? ` (${elements.length})` : ''}`"
        >
            <template #icon>
                <Creation
                    :parentPathComplete="parentPathComplete"
                    :refPath="elements?.length ? elements.length - 1 : undefined"
                    :blockSchemaPath
                />
            </template>

            <Element
                v-for="(element, elementIndex) in filteredElements"
                :key="elementIndex"
                :section="section"
                :parentPathComplete="parentPathComplete"
                :element
                :elementIndex="elementIndex"
                :moved="elementIndex == movedIndex"
                :blockSchemaPath
                :typeFieldSchema
                @remove-element="removeElement(elementIndex)"
                @move-element="
                    (direction: 'up' | 'down') =>
                        moveElement(
                            elements,
                            element.id,
                            elementIndex,
                            direction,
                        )
                "
            />
        </el-collapse-item>
    </el-collapse>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue";

    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import {CollapseItem} from "../../utils/types";

    import Creation from "./buttons/Creation.vue";
    import Element from "./Element.vue";
    import {
        CREATING_TASK_INJECTION_KEY, FULL_SCHEMA_INJECTION_KEY, FULL_SOURCE_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY, REF_PATH_INJECTION_KEY,
    } from "../../injectionKeys";
    import {SECTIONS_MAP} from "../../../../utils/constants";
    import {getValueAtJsonPath} from "../../../../utils/utils";

    const emits = defineEmits(["remove", "reorder"]);

    const flow = inject(FULL_SOURCE_INJECTION_KEY, ref(""));

    const props = defineProps<CollapseItem>();
    const filteredElements = computed(() => props.elements?.filter(Boolean) ?? []);
    const expanded = ref<CollapseItem["title"]>(props.title);

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined);
    const creatingTask = inject(CREATING_TASK_INJECTION_KEY, false);

    const parentPathComplete = computed(() => {
        return `${[
            [
                parentPath,
                creatingTask && refPath !== undefined
                    ? `[${refPath + 1}]`
                    : refPath !== undefined
                        ? `[${refPath}]`
                        : undefined,
            ].filter(Boolean).join(""),
            props.section
        ].filter(p => p.length).join(".")}`;
    });

    const removeElement = (index: number) => {
        emits(
            "remove",
            YAML_UTILS.deleteBlockWithPath({
                source: flow.value,
                path: `${parentPathComplete.value}[${index}]`
            }),
            index
        );
    };

    const movedIndex = ref(-1);

    const moveElement = (
        items: Record<string, any>[] | undefined,
        elementID: string,
        index: number,
        direction: "up" | "down",
    ) => {
        const keyName = props.title === "Plugin Defaults" ? "type" : "id";
        if (!items || !flow) return;
        if (
            (direction === "up" && index === 0) ||
            (direction === "down" && index === items.length - 1)
        )
            return;

        const newIndex = direction === "up" ? index - 1 : index + 1;

        movedIndex.value = newIndex;
        setTimeout(() => {
            movedIndex.value = -1;
        }, 200);

        emits(
            "reorder",
            YAML_UTILS.swapBlocks({
                source:flow.value,
                section: SECTIONS_MAP[props.title.toLowerCase() as keyof typeof SECTIONS_MAP],
                key1:elementID,
                key2:items[newIndex][keyName],
                keyName,
            }),
        );
    };

    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref<Record<string, any>>({}));

    // resolve parentPathComplete field schema from pluginsStore
    const typeFieldSchema = computed(() => {
        const blockSchema = getValueAtJsonPath(fullSchema.value, props.blockSchemaPath)?.properties;
        return blockSchema?.type ? "type" : blockSchema?.on ? "on" : "type";
    });
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";
</style>
