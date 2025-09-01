<template>
    <InputPair v-model="protectedModel">
        <template #value-field="{value, key}">
            <TaskString
                v-bind="$attrs"
                :model-value="value"
                @update:model-value="(changed: any) => updateValue(key, changed)"
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

    const emit = defineEmits<{
        (e: "update:modelValue", value: PairField["value"] | string): void;
    }>();

    const model = defineModel<PairField["value"] | string>();

    const protectedModel = computed({
        get: () => {
            return typeof model.value === "string" ? {} : model.value
        },
        set: (value) => {
            model.value = value
        }
    })

    function updateValue(key: string, changed: string){
        if(!model.value || typeof model.value === "string"){
            return
        }

        model.value[key] = changed
        emit("update:modelValue", model.value);
    }
</script>