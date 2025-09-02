<template>
    <InputPair v-model="protectedModel">
        <template #value-field="{value, updateValue, index}">
            <TaskString
                v-bind="$attrs"
                :model-value="value"
                @update:model-value="(changed: any) => updateValue(index, changed)"
            />
        </template>
    </InputPair>
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import {PairField} from "../../utils/types";
    import InputPair from "../inputs/InputPair.vue";
    // @ts-expect-error no typings for taskString yet
    import TaskString from "./TaskString.vue";

    const model = defineModel<PairField["value"] | string>();

    const protectedModel = computed({
        get: () => {
            return typeof model.value === "string" ? {} : model.value
        },
        set: (value) => {
            model.value = value
        }
    })
</script>