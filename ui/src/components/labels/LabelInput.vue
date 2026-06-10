<template>
    <div
        class="d-flex w-100 mb-2"
        v-for="(label, index) in locals"
        :key="index"
    >
        <div class="flex-grow-1 d-flex align-items-center">
            <KsInput
                class="form-control me-2"
                :placeholder="$t('key')"
                :modelValue="(label.key as string | undefined)"
                :disabled="existingRows.has(label)"
                @update:model-value="update(index, $event, 'key')"
            />
            <KsInput
                class="form-control me-2"
                :placeholder="$t('value')"
                :modelValue="(label.value as string | undefined)"
                @update:model-value="update(index, $event, 'value')"
            />
        </div>
        <div class="flex-shrink-1">
            <KsButtonGroup class="d-flex">
                <KsButton :icon="Plus" @click="addItem" :tooltip="$t('add label')" />
                <KsButton :icon="Minus" @click="removeItem(index)" :tooltip="$t('remove label')" />
            </KsButtonGroup>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, onMounted, watch} from "vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import Minus from "vue-material-design-icons/Minus.vue"

    interface Label {
        key: string | null;
        value: string | null;
    }

    const props = defineProps<{
        labels: Label[];
        existingLabels?: Label[];
    }>()

    const emit = defineEmits<{
        (e: "update:labels", value: Label[]): void;
    }>()

    const locals = ref<Label[]>([])
    const existingRows = ref<Set<Label>>(new Set())

    const addItem = () => {
        locals.value.push({key: null, value: null})
        emit("update:labels", locals.value)
    }

    const removeItem = (index: number) => {
        locals.value.splice(index, 1)
        if (locals.value.length === 0) {
            addItem()
        }
        emit("update:labels", locals.value)
    }

    const update = (index: number, value: string | number | undefined, prop: keyof Label) => {
        locals.value[index][prop] = value !== "" && value !== undefined ? String(value) : null
        emit("update:labels", locals.value)
    }

    const syncFromProps = (labels: Label[]) => {
        if (labels.length === 0) {
            locals.value = [{key: null, value: null}]
        } else {
            locals.value = labels
        }
    }

    onMounted(() => {
        if (props.labels.length === 0) {
            addItem()
        } else {
            syncFromProps(props.labels)
            if (locals.value.length === 0) {
                addItem()
            }
        }

        const existingKeys = new Set((props.existingLabels ?? []).map((label) => label.key ?? ""))
        existingRows.value = new Set(
            locals.value
                .filter((label) => label.key != null && existingKeys
                    .has(label.key)),
        )
    })

    watch(
        () => props.labels,
        (labels) => {
            if (labels === locals.value) {
                return
            }
            syncFromProps(labels)
        },
    )
</script>
