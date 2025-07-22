<template>
    <InputPair v-model="protectedModel" />
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import InputPair from "../../code/components/inputs/InputPair.vue";
    import {PairField} from "../../code/utils/types";

    const model = defineModel<PairField["value"] | string>()

    const protectedModel = computed({
        get: () => {
            return typeof model.value === "string" ? {} : model.value
        },
        set: (value) => {
            model.value = value
        }
    })
</script>