<template>
    <el-tooltip
        effect="light"
        :persistent="false"
        transition=""
        :hideAfter="0"
        :content="t('Set labels tooltip')"
        rawContent
        :placement="tooltipPosition"
    >
        <component
            :is="component"
            :icon="LabelMultiple"
            @click="isOpen = !isOpen"
            :disabled="!enabled"
        >
            {{ t("Set labels") }}
        </component>
    </el-tooltip>
    <el-dialog v-if="isOpen" v-model="isOpen" destroyOnClose :appendToBody="true">
        <template #header>
            <h5>{{ t("Set labels") }}</h5>
        </template>

        <template #footer>
            <el-button @click="isOpen = false">
                {{ t("cancel") }}
            </el-button>
            <el-button type="primary" @click="setLabels()">
                {{ t("ok") }}
            </el-button>
        </template>

        <p v-html="t('Set labels to execution', {id: execution.id})" />

        <el-form>
            <el-form-item :label="t('execution labels')">
                <LabelInput
                    v-model:labels="executionLabels"
                    :existingLabels="execution.labels"
                />
            </el-form-item>
        </el-form>
    </el-dialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue";
    import {State} from "@kestra-io/ui-libs";
    import {useMiscStore} from "override/stores/misc";
    import {useExecutionsStore} from "../../stores/executions";
    import {useAuthStore} from "override/stores/auth";
    import LabelInput from "../../components/labels/LabelInput.vue";
    import {filterValidLabels} from "./utils";
    import {useToast} from "../../utils/toast";
    import permission from "../../models/permission";
    import action from "../../models/action";

    interface Label {
        key: string | null;
        value: string | null;
    }

    interface Execution {
        id: string;
        namespace: string;
        state: {
            current: string;
        };
        labels?: Label[];
    }

    interface Props {
        component?: string;
        execution: Execution;
        tooltipPosition?: string;
    }

    const props = withDefaults(defineProps<Props>(), {
        component: "el-button",
        tooltipPosition: "bottom"
    });

    const {t} = useI18n();
    const toast = useToast();
    const miscStore = useMiscStore();
    const executionsStore = useExecutionsStore();
    const authStore = useAuthStore();

    const isOpen = ref(false);
    const executionLabels = ref<Label[]>([]);

    const enabled = computed(() => {
        if (!authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, props.execution.namespace)) {
            return false;
        }
        return !State.isRunning(props.execution.state.current);
    });

    const setLabels = async () => {
        const filtered = filterValidLabels(executionLabels.value);

        if (filtered.error) {
            toast.error(t("wrong labels"));
            return;
        }

        isOpen.value = false;
        try {
            const response = await executionsStore.setLabels({
                labels: filtered.labels,
                executionId: props.execution.id
            });
            executionsStore.execution = response.data;
            toast.success(t("Set labels done"));
        } catch (error) {
            // Error handling is done by the store/interceptor
        }
    };

    watch(isOpen, () => {
        executionLabels.value = [];

        const toIgnore = miscStore.configs?.hiddenLabelsPrefixes || [];

        if (props.execution.labels) {
            executionLabels.value = props.execution.labels.filter(
                label => !toIgnore.some(prefix => label.key?.startsWith(prefix))
            );
        }
    });
</script>
