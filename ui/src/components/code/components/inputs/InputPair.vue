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
            v-for="pair in internalPairs"
            :key="pair.id"
            :gutter="10"
        >
            <el-col :span="8">
                <InputText
                    :model-value="pair.currentKey"
                    :placeholder="t('key')"
                    @update:model-value="(changed) => handleKeyInput(pair.id, changed)"
                />
            </el-col>
            <el-col :span="16" class="d-flex">
                <InputText
                    :model-value="pair.value"
                    :placeholder="t('value')"
                    @update:model-value="(changed) => updateValue(pair.id, changed)"
                    class="w-100 me-2"
                />
                <DeleteOutline @click="removePair(pair.id)" class="delete" />
            </el-col>
        </el-row>

        <Add :what="props.property" @add="addPair()" />
    </div>
</template>

<script setup lang="ts">
    import {ref, watch, reactive, PropType} from "vue";
    import {debounce} from "lodash";

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

    const emits = defineEmits(["update:modelValue"]);
    const props = defineProps({
        modelValue: {
            type: Object as PropType<PairField["value"]>,
            default: undefined,
        },
        label: {type: String, default: undefined},
        property: {type: String, default: undefined},
        required: {type: Boolean, default: false},
    });

    interface InternalPair {
        id: string;
        originalKey: string;
        currentKey: string;
        value: string;
    }

    const internalPairs = ref<InternalPair[]>([]);

    const alertState = reactive({
        visible: false,
        message: ""
    });

    const processAndEmitPairs = () => {
        const emittedValue: PairField["value"] = {};
        let hasDuplicate = false;
        let duplicateKeyMessage = "";

        const emittedKeys = new Set<string>();

        for (const pair of internalPairs.value) {
            if (pair.currentKey && !emittedKeys.has(pair.currentKey)) {
                emittedValue[pair.currentKey] = pair.value;
                emittedKeys.add(pair.currentKey);
            } else if (pair.currentKey) {
                hasDuplicate = true;
                duplicateKeyMessage = t("duplicate-pair", {label:props.label ?? t("key"), key: pair.currentKey});
            }
        }

        alertState.visible = hasDuplicate;
        alertState.message = duplicateKeyMessage;

        emits("update:modelValue", emittedValue);
    };

    watch(() => props.modelValue, (newModelValue) => {
        const newModelValueKeys = Object.keys(newModelValue || {});
        const existingModelValueMap = new Map(Object.entries(newModelValue || {}));

        let currentPairsList = internalPairs.value;

        // Add new/updated keys to our local pairs
        for (const key of newModelValueKeys) {
            const value = existingModelValueMap.get(key) || "";
            const existingPair = currentPairsList.find(p => p.currentKey === key);

            if (existingPair) {
                if (value === existingPair.value) continue;
                currentPairsList = currentPairsList.filter(p => p.id !== existingPair.id);
            }
            currentPairsList.push({
                id: existingPair?.id || crypto.randomUUID(),
                originalKey: existingPair?.currentKey || key,
                currentKey: existingPair?.currentKey || key,
                value: value,
            });
        }

        // Removed keys from our local pairs
        for (const key of currentPairsList.map(p => p.currentKey)) {
            if (key != "" && !Object.prototype.hasOwnProperty.call(newModelValue, key)) {
                currentPairsList = currentPairsList.filter(p => p.currentKey !== key);
            }
        }

        internalPairs.value = currentPairsList;
    }, {immediate: true, deep: true});

    const debouncedSetKey = debounce((pairId: string, newKeyCandidate: string) => {
        const pair = internalPairs.value.find(p => p.id === pairId);
        if (pair) {
            pair.currentKey = newKeyCandidate;
            processAndEmitPairs();
        }
    }, 500);

    const handleKeyInput = (pairId: string, newValue: string) => {
        const pair = internalPairs.value.find(p => p.id === pairId);
        if (pair) {
            pair.currentKey = newValue;
            debouncedSetKey(pairId, newValue);
        }
    };

    const addPair = () => {
        internalPairs.value.push({
            id: crypto.randomUUID(),
            originalKey: "",
            currentKey: "",
            value: "",
        });
        processAndEmitPairs();
    };

    const removePair = (pairId: string) => {
        internalPairs.value = internalPairs.value.filter(p => p.id !== pairId);
        processAndEmitPairs();
    };

    const updateValue = (pairId: string, newValue: string) => {
        const pairToUpdate = internalPairs.value.find(p => p.id === pairId);
        if (pairToUpdate) {
            pairToUpdate.value = newValue;
            processAndEmitPairs();
        }
    };
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";
</style>
