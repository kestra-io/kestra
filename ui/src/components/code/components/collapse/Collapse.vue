<template>
    <el-collapse v-model="expanded" class="collapse">
        <el-collapse-item
            v-for="(item, index) in props.items"
            :key="index"
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
    import {inject, nextTick, PropType, ref} from "vue";

    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {CollapseItem} from "../../utils/types";

    import Creation from "./buttons/Creation.vue";
    import Element from "./Element.vue";
    import {FLOW_INJECTION_KEY} from "../../injectionKeys";

    const emits = defineEmits(["remove", "reorder"]);

    const flow = inject(FLOW_INJECTION_KEY, ref(""));

    const props = defineProps({
        items: {
            type: Array as PropType<CollapseItem[]>,
            required: true,
        }
    });
    const expanded = ref<CollapseItem["title"][]>([]);


    props.items.forEach((item) => {
        if (item.elements?.length) expanded.value.push(item.title);
    });


    const removeElement = (title: string, index: number) => {
        props.items.forEach((item) => {
            if (item.title === title) {
                nextTick(() => {
                    const ID = item.elements?.[index].id;

                    item.elements?.splice(index, 1);
                    if(ID){
                        emits(
                            "remove",
                            YAML_UTILS.deleteTask(flow.value, ID, title.toUpperCase()),
                        );
                    }
                    expanded.value = expanded.value.filter((v) => v !== title);
                });
            }
        });
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
