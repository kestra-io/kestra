<template>
    <span v-if="required" class="me-1 text-danger">*</span>
    <span v-if="label" class="label">{{ label }}</span>
    <el-alert
        v-if="alertState.visible"
        :title="alertState.message"
        type="error"
        show-icon
        :closable="false"
        class="mb-2"
    />
    <div class="mt-1 mb-2 w-100 wrapper">
        <el-row
            v-for="(pair, index) in internalPairs"
            :key="index"
            :gutter="10"
        >
            <el-col :span="8">
                <InputText
                    :model-value="pair[0]"
                    :placeholder="t('key')"
                    @update:model-value="(changed) => handleKeyInput(index, changed)"
                    :have-error="duplicatedPairs.includes(pair[0])"
                />
            </el-col>
            <el-col :span="16" class="d-flex">
                <InputText
                    :model-value="pair[1]"
                    :placeholder="t('value')"
                    @update:model-value="(changed) => updateValue(index, changed)"
                    class="w-100 me-2"
                />
                <DeleteOutline @click="removePair(index)" class="delete" />
            </el-col>
        </el-row>

        <Add :what="props.property" @add="addPair()" />
    </div>
</template>

<script setup lang="ts">
    import {watch, computed, ref} from "vue";
    import {PairField} from "../../utils/types";

    import {DeleteOutline} from "../../utils/icons";

    import InputText from "./InputText.vue";
    import Add from "../Add.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    defineOptions({
        name: "InputPair",
        inheritAttrs: false,
    });

    const emit = defineEmits(["update:modelValue"]);
    const props = defineProps<{
        modelValue?: PairField["value"],
        label?: string,
        property?: string,
        required?: boolean
    }>();

    const internalPairs = ref<[string, string][]>([])

    const alertState = computed(() => {
        return {
            visible: Object.keys(props.modelValue || {}).length === 0,
            message: t("code.inputPair.empty"),
        };
    });

    watch(() => props.modelValue, (newValue) => {
        // If the alert is visible, we don't want to update the pairs
        // because it would delete problem line silently.
        if (alertState.value.visible) {
            return;
        }
        internalPairs.value = Object.entries(newValue || {});
    }, {
        deep: true,
        immediate: true
    });

    const duplicatedPairs = computed(() => {
        return internalPairs.value.map(pair => pair[0])
            .filter((pair, index, self) =>
                self.findIndex(p => p[0] === pair[0]) !== index
            );
    });

    const modelValueToUpdate = computed(() => {
        return Object.fromEntries(internalPairs.value);
    });

    function updateModel() {
        emit("update:modelValue", modelValueToUpdate.value);
    }

    function handleKeyInput(pairId: number, newValue: string) {
        internalPairs.value[pairId][0] = newValue;
        updateModel()
    };

    function addPair() {
        internalPairs.value.push(["", ""])
        updateModel()
    };

    function removePair (pairId: number) {
        internalPairs.value.splice(pairId, 1);
        updateModel()
    };

    function updateValue (pairId: number, newValue: string){
        internalPairs.value[pairId][1] = newValue;
        updateModel()
    };
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";
</style>
