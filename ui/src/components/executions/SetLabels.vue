<template>
    <el-tooltip
        effect="light"
        :persistent="false"
        transition=""
        :hideAfter="0"
        :content="t('Set labels tooltip')"
        rawContent
        placement="bottom"
    >
        <el-button
            :icon="LabelMultiple"
            @click="isOpen = !isOpen"
            :disabled="!enabled"
        >
            {{ t("Set labels") }}
        </el-button>
    </el-tooltip>

    <el-dialog
        v-if="isOpen"
        v-model="isOpen"
        destroyOnClose
        :appendToBody="true"
    >
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

    import LabelInput from "../../components/labels/LabelInput.vue";

    import {State} from "@kestra-io/ui-libs";
    import {filterValidLabels} from "./utils";

    import {useMiscStore} from "override/stores/misc";
    import {useExecutionsStore} from "../../stores/executions";
    import {useAuthStore} from "override/stores/auth";

    const miscStore = useMiscStore();
    const executionsStore = useExecutionsStore();
    const authStore = useAuthStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useToast} from "../../utils/toast";
    const toast = useToast();

    import permission from "../../models/permission";
    import action from "../../models/action";

    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue";

    interface Label {
        key: string;
        value: string;
    }

    interface Props {
        execution: {
            id: string;
            namespace: string;
            state: {
                current: string;
            };
            labels?: Label[];
        };
    }

    const props = defineProps<Props>();

    const isOpen = ref(false);
    const executionLabels = ref<Label[]>([]);

    const enabled = computed(() => {
        if (
            !authStore.user?.isAllowed(
                permission.EXECUTION,
                action.UPDATE,
                props.execution.namespace,
            )
        ) {
            return false;
        }
        return !State.isRunning(props.execution.state.current);
    });

    const setLabels = async () => {
        const filtered = filterValidLabels(executionLabels.value);

        if (filtered.error) {
            toast.error(t("wrong labels"), t("error"));
            return;
        }

        isOpen.value = false;
        try {
            const response = await executionsStore.setLabels({
                labels: filtered.labels,
                executionId: props.execution.id,
            });
            executionsStore.execution = response.data;
            toast.success(t("Set labels done"));
        } catch (err) {
            console.error(err); // Error handling is done by the store/interceptor
        }
    };

    watch(isOpen, () => {
        executionLabels.value = [];

        const toIgnore = miscStore.configs?.hiddenLabelsPrefixes || [];

        if (props.execution.labels) {
            executionLabels.value = props.execution.labels.filter(
                (label) =>
                    !toIgnore.some((prefix: string) =>
                        label.key?.startsWith(prefix),
                    ),
            );
        }
    });
</script>
