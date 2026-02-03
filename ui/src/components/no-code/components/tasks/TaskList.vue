<template>
    <div class="tasks-wrapper">
        <el-collapse v-model="expanded" class="collapse">
            <el-collapse-item
                :name="section"
                :title="`${section}${elements ? ` (${elements.length})` : ''}`"
                :disabled="merge"
                :class="{merge}"
            >
                <template #icon>
                    <Creation
                        :parentPathComplete
                        :refPath="elements?.length ? elements.length - 1 : undefined"
                        :blockSchemaPath
                    />
                </template>

                <Element
                    v-for="(element, elementIndex) in filteredElements"
                    :key="elementIndex"
                    :section
                    :parentPathComplete
                    :element
                    :elementIndex
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
    import Creation from "./taskList/buttons/Creation.vue";
    import Element from "./taskList/Element.vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import {CollapseItem} from "../../utils/types";

    import {
        CREATING_TASK_INJECTION_KEY, FULL_SCHEMA_INJECTION_KEY, FULL_SOURCE_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY, REF_PATH_INJECTION_KEY, UPDATE_YAML_FUNCTION_INJECTION_KEY,
    } from "../../injectionKeys";
    import {SECTIONS_MAP} from "../../../../utils/constants";
    import {getValueAtJsonPath} from "../../../../utils/utils";
    import {useI18n} from "vue-i18n";


    const blockSchemaPathInjected = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref(""))

    const schemaAtBlockPathInjected = computed(() => getValueAtJsonPath(fullSchema.value, blockSchemaPathInjected.value))

    const blockSchemaPath = computed(() => {
        const rootParts = props.root ? props.root.split(".") : []
        if(rootParts.length > 1){
            // if second part is a property not defined in properties, 
            // it can only be defined by additionalProperties
            const s = schemaAtBlockPathInjected.value?.properties?.[rootParts[0]]
            if(s && s.properties?.[rootParts[1]] === undefined && s.additionalProperties){
                rootParts[1] = "additionalProperties"
            } else {
                rootParts.splice(1, 0, "properties")
            }
        }
        return [blockSchemaPathInjected.value, "properties", ...rootParts, "items"].join("/");
    });

    defineOptions({
        inheritAttrs: false
    });

    interface Task {
        id:string,
        type:string
    }

    const emits = defineEmits(["update:modelValue"]);
    const props = withDefaults(defineProps<{
        modelValue?: Task[],
        root?: string;
        merge?: boolean;
    }>(), {
        modelValue: () => [],
        root: undefined,
        merge: false,
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

    const {t} = useI18n();

    const section = computed(() => {
        if(props.merge){
            return t("tasks");
        }
        return props.root ?? t("tasks");
    });

    const flow = inject(FULL_SOURCE_INJECTION_KEY, ref(""));

    const filteredElements = computed(() => elements.value?.filter(Boolean) ?? []);
    const expanded = props.merge ? computed(() => section.value) : ref<CollapseItem["title"]>(props.root ?? "tasks");

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
            section.value
        ].filter(p => p.length).join(".")}`;
    });

    const movedIndex = ref(-1);

    const updateYaml = inject(UPDATE_YAML_FUNCTION_INJECTION_KEY, () => {});

    const moveElement = (
        items: Record<string, any>[] | undefined,
        elementID: string,
        index: number,
        direction: "up" | "down",
    ) => {
        const keyName = section.value === "Plugin Defaults" ? "type" : "id";
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

        updateYaml(
            YAML_UTILS.swapBlocks({
                source:flow.value,
                section: SECTIONS_MAP[section.value.toLowerCase() as keyof typeof SECTIONS_MAP],
                key1:elementID,
                key2:items[newIndex][keyName],
                keyName,
            })
        );
    };

    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref<Record<string, any>>({}));

    const blockSchema = computed(() => getValueAtJsonPath(fullSchema.value, blockSchemaPath.value) ?? {});

    // resolve parentPathComplete field schema from pluginsStore
    const typeFieldSchema = computed(() => blockSchema.value?.type ? "type" : blockSchema.value?.on ? "on" : "type");
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

.list-header{
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
    gap: 1rem;
}
.tasks-wrapper {
    width: 100%;
}

.disabled {
    opacity: 0.5;
    pointer-events: none;
    cursor: not-allowed;
}

.merge :deep(.el-collapse-item__header){
    cursor: default;
}
</style>
