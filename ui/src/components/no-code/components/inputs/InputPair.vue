<template>
    <span v-if="required" class="me-1 text-danger">*</span>
    <span v-if="label" class="label">{{ label }}</span>
    <el-alert
        v-if="alertState.visible"
        :title="alertState.message"
        type="error"
        showIcon
        :closable="false"
        class="mb-2"
    />
    <div class="mt-1 mb-2 w-100 wrapper">
        <el-row
            v-for="(pair, index) in internalPairs"
            :key="index"
            :gutter="10"
            align="middle"
        >
            <el-col :span="8">
                <InputText
                    :modelValue="pair[0]"
                    :placeholder="t('key')"
                    @update:model-value="(changed) => handleKeyInput(index, changed)"
                    :haveError="duplicatedKeys.includes(pair[0])"
                />
            </el-col>
            <el-col :span="16" class="d-flex">
                <slot name="value-field" :value="pair[1]" :key="pair[0]" :index="index" :updateValue="updateValue">
                    <InputText
                        :modelValue="pair[1]"
                        :placeholder="t('value')"
                        @update:model-value="(changed) => updateValue(index, changed)"
                        class="w-100 me-2"
                    />
                </slot>
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

    const internalPairs = ref<[string, string | undefined][]>([])

    // this flag will avoid updating the modelValue when the
    // change was initiated in the component itself
    const localEdit = ref(false);

    const duplicatedKeys = computed(() => {
        return internalPairs.value.map(pair => pair[0])
            .filter((key, index, self) =>
                self.indexOf(key) !== index
            );
    });

    const alertState = computed(() => {
        if(duplicatedKeys.value.length > 0){
            return {
                visible: true,
                message: t("duplicate-pair", {label: props.label ?? t("key"), key: duplicatedKeys.value[0]}),
            }
        }
        return {
            visible: false,
            message: "",
        };
    });

    watch(() => props.modelValue, (newValue) => {
        // If the alert is visible, we don't want to update the pairs
        // because it would delete problem line silently.
        if (alertState.value.visible || localEdit.value) {
            return;
        }
        localEdit.value = false;
        internalPairs.value = Object.entries(newValue || {});
    }, {
        deep: true,
        immediate: true
    });



    function updateModel() {
        localEdit.value = true;
        const newVal = Object.fromEntries(internalPairs.value.filter(pair => pair[0] !== "" && pair[1] !== undefined))

        emit("update:modelValue", newVal);
    }

    function handleKeyInput(index: number, newValue: string) {

        internalPairs.value[index][0] = newValue.toString();
        updateModel()
    };

    function addPair() {

        internalPairs.value.push(["", undefined])
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

:deep(.delete-outline-icon) {
    height: 10px;
    margin-left: 10px;
}
</style>
