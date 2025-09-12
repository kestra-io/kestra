<template>
    <div class="tasks-wrapper">
        <el-collapse v-model="expanded" class="collapse">
            <el-collapse-item
                :name="sectionName"
                :title="`${sectionName}${elements ? ` (${elements.length})` : ''}`"
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
                    :section="sectionName"
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
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue";
    import {BLOCK_SCHEMA_PATH_INJECTION_KEY} from "../../injectionKeys";
    import {useFlowStore} from "../../../../stores/flow";
    import Creation from "./taskList/buttons/Creation.vue";
    import Element from "./taskList/Element.vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import {CollapseItem} from "../../utils/types";

    import {
        CREATING_TASK_INJECTION_KEY, FULL_SCHEMA_INJECTION_KEY, FULL_SOURCE_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY, REF_PATH_INJECTION_KEY,
    } from "../../injectionKeys";
    import {SECTIONS_MAP} from "../../../../utils/constants";
    import {getValueAtJsonPath} from "../../../../utils/utils";

    const blockSchemaPathInjected = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref(""))
    const blockSchemaPath = computed(() => [blockSchemaPathInjected.value, "properties", props.root, "items"].join("/"));

    defineOptions({
        inheritAttrs: false
    });

    const flowStore = useFlowStore();

    interface Task {
        id:string,
        type:string
    }

    const emits = defineEmits(["update:modelValue"]);
    const props = withDefaults(defineProps<{
        modelValue?: Task[],
        root?: string;
    }>(), {
        modelValue: () => [],
        root: undefined
    });

    const elements = computed(() =>
        !Array.isArray(props.modelValue) ? [props.modelValue] : props.modelValue,
    );

    function removeElement(index: number){
        if(elements.value.length <= 1){
            emits("update:modelValue", undefined);
            return
        }
        let localItems = [...elements.value]
        localItems.splice(index, 1)

        emits("update:modelValue", localItems);
    };

    const sectionName = computed(() => {
        return props.root ?? "tasks";
    });



    const flow = inject(FULL_SOURCE_INJECTION_KEY, ref(""));

    const filteredElements = computed(() => elements.value?.filter(Boolean) ?? []);
    const expanded = ref<CollapseItem["title"]>(props.root ?? "tasks");

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
            sectionName.value
        ].filter(p => p.length).join(".")}`;
    });

    const movedIndex = ref(-1);

    const moveElement = (
        items: Record<string, any>[] | undefined,
        elementID: string,
        index: number,
        direction: "up" | "down",
    ) => {
        const keyName = sectionName.value === "Plugin Defaults" ? "type" : "id";
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

        flowStore.flowYaml =
            YAML_UTILS.swapBlocks({
                source:flow.value,
                section: SECTIONS_MAP[sectionName.value.toLowerCase() as keyof typeof SECTIONS_MAP],
                key1:elementID,
                key2:items[newIndex][keyName],
                keyName,
            })
    };

    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref<Record<string, any>>({}));

    // resolve parentPathComplete field schema from pluginsStore
    const typeFieldSchema = computed(() => {
        const blockSchema = getValueAtJsonPath(fullSchema.value, blockSchemaPath.value)?.properties;
        return blockSchema?.type ? "type" : blockSchema?.on ? "on" : "type";
    });
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

.tasks-wrapper {
    width: 100%;
}

.disabled {
    opacity: 0.5;
    pointer-events: none;
    cursor: not-allowed;
}
</style>
