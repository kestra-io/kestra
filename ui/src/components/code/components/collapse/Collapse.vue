<template>
    <el-collapse v-model="expanded" class="collapse">
        <el-collapse-item
            :name="title"
            :title="`${title}${elements ? ` (${elements.length})` : ''}`"
        >
            <template #icon>
                <Creation :section="title" />
            </template>

            <Element
                v-for="(element, elementIndex) in elements"
                :key="elementIndex"
                :section="title"
                :element
                :element-index
                @remove-element="removeElement(title, elementIndex)"
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
    import {inject, ref} from "vue";

    import {deleteBlock, swapBlocks} from "@kestra-io/ui-libs/flow-yaml-utils";

    import {CollapseItem} from "../../utils/types";

    import Creation from "./buttons/Creation.vue";
    import Element from "./Element.vue";
    import {FLOW_INJECTION_KEY} from "../../injectionKeys";
    import {SECTIONS_MAP} from "../../../../utils/constants";

    const emits = defineEmits(["remove", "reorder"]);

    const flow = inject(FLOW_INJECTION_KEY, ref(""));

    const props = defineProps<CollapseItem>();
    const expanded = ref<CollapseItem["title"]>(props.title);

    const removeElement = (title: string, index: number) => {
        const keyName = title === "Plugin Defaults" ? "type" : "id";

        if(props.elements?.[index]?.[keyName] === undefined) return;

        emits(
            "remove",
            deleteBlock({
                source: flow.value,
                section: SECTIONS_MAP[title.toLowerCase() as keyof typeof SECTIONS_MAP],
                key: props.elements[index][keyName],
                keyName,
            }),
        );
    };

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
        emits(
            "reorder",
            swapBlocks({
                source:flow.value,
                section: SECTIONS_MAP[props.title.toLowerCase() as keyof typeof SECTIONS_MAP],
                key1:elementID,
                key2:items[newIndex][keyName],
                keyName,
            }),
        );
    };
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";
</style>
