<template>
    <el-alert
        v-if="duplicatedKeys?.length"
        :title="t('duplicate-pair', {label: t('key'), key: duplicatedKeys[0]})"
        type="error"
        showIcon
        :closable="false"
        class="mb-2"
    />
    <template v-if="componentType">
        <Wrapper v-for="(item, index) in currentValue" :key="index" class="item-wrapper">
            <template #tasks>
                <InputText
                    :modelValue="item[0]"
                    @update:model-value="onKey(index, $event)"
                    margin="m-0"
                    placeholder="Key"
                    :haveError="duplicatedKeys.includes(item[0])"
                />
                <hr>
                <component
                    ref="valueComponent"
                    :is="componentType"
                    :modelValue="item[1]"
                    @update:model-value="onValueChange(index, $event)"
                    :root="getKey(item[0])"
                    :schema="schema.additionalProperties"
                    :required="isRequired(item[0])"
                    :disabled
                    merge
                />
                <div class="delete-container">
                    <button @click="removeItem(index)" class="remove-entry">
                        {{ te(`no_code.remove.${root}`) ? t(`no_code.remove.${root}`) : t('no_code.remove.default') }} <DeleteOutline />
                    </button>
                </div>
            </template>
        </Wrapper>
    </template>
    <template v-else>
        <el-row v-for="(item, index) in currentValue" :key="index" :gutter="10" class="w-100" :data-testid="`task-dict-item-${item[0]}-${index}`">
            <el-col :span="6">
                <InputText
                    :modelValue="item[0]"
                    @update:model-value="onKey(index, $event)"
                    margin="m-0"
                    placeholder="Key"
                    :haveError="duplicatedKeys.includes(item[0])"
                />
            </el-col>
            <el-col :span="16">
                <TaskExpression
                    :modelValue="item[1]"
                    @update:model-value="onValueChange(index, $event)"
                    :root="getKey(item[0])"
                    :schema="schema.additionalProperties"
                    :required="isRequired(item[0])"
                    :disabled
                />
            </el-col>
            <el-col :span="2" class="col align-self-center delete">
                <DeleteOutline @click="removeItem(index)" />
            </el-col>
        </el-row>
    </template>
    <Add v-if="!props.disabled" :disabled="addButtonDisabled" @add="addItem()" />
</template>

<script setup lang="ts">
    import {computed, ref, useTemplateRef, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {DeleteOutline} from "../../utils/icons";

    import InputText from "../inputs/InputText.vue";
    import TaskExpression from "./TaskExpression.vue";
    import Add from "../Add.vue";
    import debounce from "lodash/debounce";
    import Wrapper from "./Wrapper.vue";
    import {useBlockComponent} from "./useBlockComponent";
    import {useToast} from "../../../../utils/toast";

    const {t, te} = useI18n();

    defineOptions({
        inheritAttrs: false,
    });

    const valueComponent = useTemplateRef<any[]>("valueComponent");

    const props = withDefaults(defineProps<{
        modelValue?: Record<string, any>;
        schema?: any;
        root?: string;
        disabled?: boolean;
    }>(), {
        disabled: false,
        modelValue: () => ({}),
        root: undefined,
        schema: () => ({type: "object"})
    });

    const {getBlockComponent} = useBlockComponent();

    const componentType = computed(() => {
        return props.schema?.additionalProperties ? getBlockComponent.value(
            props.schema.additionalProperties,
            props.root
        ) : undefined;
    });

    const currentValue = ref<[string, any][]>([])

    // this flag will avoid updating the modelValue when the
    // change was initiated in the component itself
    const localEdit = ref(false);

    watch(
        () => props.modelValue,
        (newValue) => {
            if(localEdit.value) {
                return;
            }
            localEdit.value = false;
            if(newValue === undefined || newValue === null) {
                currentValue.value = [];
                return;
            }
            currentValue.value = Object.entries(newValue ?? {});
        },
        {
            immediate: true,
            deep: true
        },
    );

    const duplicatedKeys = computed(() => {
        return currentValue.value.map(pair => pair[0])
            .filter((key, index, self) =>
                self.indexOf(key) !== index
            );
    });

    const emitUpdate = debounce(function () {
        if(duplicatedKeys.value?.length > 0) {
            return;
        }
        localEdit.value = true;
        emit("update:modelValue", Object.fromEntries(currentValue.value.filter(pair => pair[0] !== "" && pair[1] !== undefined)));
    }, 200);

    const emit = defineEmits(["update:modelValue"]);

    function getKey(key: string) {
        return props.root ? `${props.root}.${key}` : key;
    }

    function isRequired(key: string) {
        return props.schema?.required?.includes(key);
    }

    function onKey(key: number, val: string) {
        currentValue.value[key][0] = val;
        emitUpdate()
    }

    function onValueChange(key: number, val: any) {
        currentValue.value[key][1] = val;
        emitUpdate()
    }

    function removeItem(index: number) {
        currentValue.value.splice(index, 1);
        emitUpdate()
    }

    const toast = useToast();

    function addItem() {
        if(addButtonDisabled.value) {
            toast.warning(t("no_code.add.disabled_warning"));
            return;
        }
        currentValue.value.push(["", undefined]);
        emitUpdate()
    }

    const addButtonDisabled = computed(() => {
        return currentValue.value.at(-1)?.[0] === "" && currentValue.value.at(-1)?.[1] === undefined;
    });
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

.task-container{
    margin-bottom: 1rem;
}

.delete-container{
    display: flex;
    align-items: center;
    margin-left: 1rem;
    justify-content: end;
}

.remove-entry{
    color: var(--ks-content-secondary);
    background-color: var(--ks-button-background-secondary);
    border: none;
    display: flex;
    align-items: center;
    gap: .5rem; 
    opacity: 0.7;
    padding: 0;
    height: .75rem;
    &:hover {
        color: var(--ks-content-secondary);
        opacity: 1;
    }
}

.item-wrapper {
    margin: .25rem 0;
    background-color: var(--ks-background-card);
}
</style>
