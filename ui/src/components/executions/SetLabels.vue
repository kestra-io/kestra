<template>
    <el-button
        :disabled="!enabled"
        :icon="Plus"
        @click="isOpen = !isOpen"
    >
        {{ t("set_extra_labels") }}
    </el-button>

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
            <el-button @click="onCancel">
                {{ t("cancel") }}
            </el-button>
            <el-button type="primary" :loading="isSaving" @click="setLabels()">
                {{ t("ok") }}
            </el-button>
        </template>

        <p v-html="t('Set labels to execution', {id: execution.id})" />

        <el-form>
            <el-form-item :label="t('execution labels')">
                <LabelInput
                    v-model:labels="executionLabels"
                    :existingLabels="executionLabels"
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

    import Plus from "vue-material-design-icons/Plus.vue";

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
    const isSaving = ref(false);

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

    const onCancel = () => {
        // discard temp and close dialog without mutating parent
        isOpen.value = false;
        executionLabels.value = [];
    };

    const setLabels = async () => {
        const filtered = filterValidLabels(executionLabels.value);

        if (filtered.error) {
            toast.error(t("wrong labels"), t("error"));
            return;
        }

        isSaving.value = true;
        try {
            const response = await executionsStore.setLabels({
                labels: filtered.labels,
                executionId: props.execution.id,
            });

            if (response && response.data) {
                executionsStore.execution = response.data;
            }

            toast.success(t("Set labels done"));

            // close and clear only after success
            isOpen.value = false;
            executionLabels.value = [];
        } catch (err) {
            console.error(err); // keep dialog open so user can fix / retry
        } finally {
            isSaving.value = false;
        }
    };

    // initialize the temp clone only when opening the dialog
    watch(isOpen, (open) => {
        if (open) {
            const toIgnore = miscStore.configs?.hiddenLabelsPrefixes || [];
            const source = props.execution.labels || [];

            // deep clone so child edits never mutate the original
            executionLabels.value = JSON.parse(JSON.stringify(source || []))
                .filter((label: Label) => !toIgnore.some((prefix: string) => label.key?.startsWith(prefix)));

        } else {
            // when dialog closed, clear temp state (safe-guard)
            executionLabels.value = [];
        }
    });
</script>
