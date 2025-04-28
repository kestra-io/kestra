<template>
    <el-collapse v-model="expanded" class="collapse">
        <el-collapse-item
            :name="item.title"
            :title="`${item.title}${item.elements ? ` (${item.elements.length})` : ''}`"
        >
            <template #icon>
                <Creation :section="item.title" />
            </template>

            <Element
                v-for="(element, elementIndex) in item.elements"
                :key="elementIndex"
                :section="item.title"
                :element
                @remove-element="removeElement(item.title, elementIndex)"
                @move-element="
                    (direction: 'up' | 'down') =>
                        moveElement(
                            item.elements,
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

    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {CollapseItem} from "../../utils/types";

    import Creation from "./buttons/Creation.vue";
    import Element from "./Element.vue";
    import {FLOW_INJECTION_KEY} from "../../injectionKeys";

    const emits = defineEmits(["remove", "reorder"]);

    const flow = inject(FLOW_INJECTION_KEY, ref(""));

    const props = defineProps<{
        item: CollapseItem
    }>();
    const expanded = ref<CollapseItem["title"]>(props.item.title);

    const removeElement = (title: string, index: number) => {
        if(props.item.elements?.[index]?.id === undefined) return;
        emits(
            "remove",
            YAML_UTILS.deleteTask(flow.value, props.item.elements[index].id, title),
        );
    };

    const moveElement = (
        items: Record<string, any>[] | undefined,
        elementID: string,
        index: number,
        direction: "up" | "down",
    ) => {
        if (!items || !flow) return;
        if (
            (direction === "up" && index === 0) ||
            (direction === "down" && index === items.length - 1)
        )
            return;

        const newIndex = direction === "up" ? index - 1 : index + 1;
        emits(
            "reorder",
            YAML_UTILS.swapTasks(flow.value, elementID, items[newIndex].id),
        );
    };
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";
</style>
