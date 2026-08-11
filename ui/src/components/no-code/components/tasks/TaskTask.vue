<template>
    <div class="w-100">
        <LeafBlockCard
            v-if="isSet"
            :block="cardBlock"
            :path="parentPathComplete"
            :label="cardLabel"
            :showDuplicate="false"
            :showOpenSplit="false"
            @select="onSelect"
            @delete="removeElement"
        />
        <BlockEmptyDrop
            v-else
            variant="inline"
            :label="fieldTitle"
            @add="onSelect"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue"
    import {
        PARENT_PATH_INJECTION_KEY,
        REF_PATH_INJECTION_KEY,
        CREATING_TASK_INJECTION_KEY,
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        EDIT_TASK_FUNCTION_INJECTION_KEY,
        FULL_SCHEMA_INJECTION_KEY,
    } from "../../injectionKeys"
    import LeafBlockCard from "../../blocks/LeafBlockCard.vue"
    import BlockEmptyDrop from "../../blocks/BlockEmptyDrop.vue"
    import {getValueAtJsonPath} from "../../../../utils/utils"
    import {useI18n} from "vue-i18n"

    const {t} = useI18n()

    const model = defineModel({
        type: Object,
        default: () => ({}),
    })

    const props = defineProps({
        root: {
            type: String,
            required: true,
        },
    })

    defineOptions({
        inheritAttrs: false,
    })

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "")
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined)
    const creatingTask = inject(CREATING_TASK_INJECTION_KEY, false)
    const blockSchemaPathInjected = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, ref())
    const editTask = inject(EDIT_TASK_FUNCTION_INJECTION_KEY, () => {})
    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref({}))

    const blockSchemaPath = computed(() => {
        return [blockSchemaPathInjected.value, "properties", props.root.split(".").pop()].join("/")
    })

    const localSchema = computed(() => getValueAtJsonPath(fullSchema.value,  blockSchemaPath.value))

    const fieldTitle = computed(() => {
        const schema = localSchema.value

        if(schema?.anyOf && Array.isArray(schema.anyOf)){
            const titles: string[] = schema.anyOf.map((s: any) => s.allOf?.find((a: any) => a.title)?.title ?? s.title)

            if(titles.every((title) => title === titles[0])){
                return titles[0]
            }
        }
        return t("block_editor.task_noun")
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
        ].filter(p => p?.length).join(".")}`
    })

    const isSet = computed(() => Object.keys(model.value ?? {}).length > 0)

    const cardBlock = computed(() => ({
        id: localSchema.value?.properties?.id ? model.value?.id : undefined,
        type: model.value?.type,
    }))

    const cardLabel = computed(() => {
        if (cardBlock.value.id != null) return String(cardBlock.value.id)
        return typeof model.value?.type === "string" ? model.value.type.split(".").pop() : undefined
    })

    const onSelect = () => {
        editTask(parentPathComplete.value, blockSchemaPath.value, undefined)
    }

    function removeElement() {
        model.value = undefined
    }
</script>
