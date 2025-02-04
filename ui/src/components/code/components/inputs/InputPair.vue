<template>
    <span v-if="required" class="me-1 text-danger">*</span>
    <span class="label">{{ label }}</span>
    <div class="mt-1 mb-2 w-100 wrapper">
        <el-row
            v-for="(value, key, index) in props.modelValue"
            :key="index"
            :gutter="10"
        >
            <el-col :span="8">
                <InputText
                    :model-value="key"
                    :placeholder="t('key')"
                    @update:model-value="(changed) => updateKey(key, changed)"
                />
            </el-col>
            <el-col :span="16" class="d-flex">
                <InputText
                    :model-value="value"
                    :placeholder="t('value')"
                    @update:model-value="(changed) => updateValue(key, changed)"
                    class="w-100 me-2"
                />
                <DeleteOutline @click="removePair(key)" class="delete" />
            </el-col>
        </el-row>

        <Add :what="props.property" @add="addPair()" />
    </div>
</template>

<script setup lang="ts">
    import {PropType} from "vue";

    import {PairField} from "../../utils/types";

    import {DeleteOutline} from "../../utils/icons";

    import InputText from "./InputText.vue";
    import Add from "../Add.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const emits = defineEmits(["update:modelValue"]);
    const props = defineProps({
        modelValue: {
            type: Object as PropType<PairField["value"][]>,
            default: undefined,
        },
        label: {type: String, required: true},
        property: {type: String, default: undefined},
        required: {type: Boolean, default: false},
    });

    const addPair = () => {
        emits("update:modelValue", {...props.modelValue, "": ""});
    };
    const removePair = (key: any) => {
        const values = {...props.modelValue};
        delete values[key];

        emits("update:modelValue", values);
    };
    const updateKey = (old, changed) => {
        const values = {...props.modelValue};

        // Create an array of key-value pairs and preserve order
        const entries = Object.entries(values);

        // Find the index of the old key
        const index = entries.findIndex(([key]) => key === old);

        if (index !== -1) {
            // Get the value of the old key
            const [, value] = entries[index];

            // Remove the old key from the entries
            entries.splice(index, 1);

            // Add the new key with the same value
            entries.splice(index, 0, [changed, value]);

            // Rebuild the object while keeping the order
            const updatedValues = Object.fromEntries(entries);

            // Emit the updated object
            emits("update:modelValue", updatedValues);
        }
    };
    const updateValue = (key, value) => {
        const values = {...props.modelValue};
        values[key] = value;
        emits("update:modelValue", values);
    };
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";
</style>
