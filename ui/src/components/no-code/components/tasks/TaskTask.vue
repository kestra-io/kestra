<template>
    <div class="w-100">
        <Element
            :section="root"
            :parentPathComplete="parentPathComplete"
            :blockSchemaPath
            :element="Object.keys(model).length > 0 ? {
                id: (localSchema?.properties?.id ? model?.id : undefined),
                type: model?.type,
            } : {id: fieldTitle}"
            typeFieldSchema="type"
            @remove-element="removeElement()"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue";
    import {
        PARENT_PATH_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        FULL_SCHEMA_INJECTION_KEY
    } from "../../injectionKeys";
    import Element from "./taskList/Element.vue";
    import {getValueAtJsonPath} from "../../../../utils/utils";


    const model = defineModel({
        type: Object,
        default: () => ({})
    });

    const props = defineProps({
        root: {
            type: String,
            required: true
        },
    });

    defineOptions({
        inheritAttrs: false
    })

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined);
    const creatingTask = inject(CREATING_TASK_INJECTION_KEY, false);
    const blockSchemaPathInjected = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref())
    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref({}))

    const blockSchemaPath = computed(() => {
        return [blockSchemaPathInjected.value, "properties", props.root.split(".").pop()].join("/")
    })

    const localSchema = computed(() => getValueAtJsonPath(fullSchema.value,  blockSchemaPath.value))

    const fieldTitle = computed(() => {
        const schema = localSchema.value;

        if(schema?.anyOf && Array.isArray(schema.anyOf)){
            // find all the title fields in the anyOf
            const titles: string[] = schema.anyOf.map((s: any) => s.allOf?.find((a: any) => a.title)?.title ?? s.title);

            // if all the titles are the same, return that title
            if(titles.every((t) => t === titles[0])){
                return titles[0];
            }
        }
        return "Set a task"
    })

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
            props.root,
        ].filter(p => p.length).join(".")}`;
    });

    function removeElement() {
        model.value = undefined;
    }
</script>


