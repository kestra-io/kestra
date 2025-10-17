<template>
    <div class="button-wrapper">
        <FlowPlaygroundToggle v-if="isSettingsPlaygroundEnabled" />

        <ValidationError
            class="validation"
            tooltipPlacement="bottom-start"
            :errors="flowErrors"
            :warnings="flowWarnings"
            :infos="flowInfos"
        />

        <EditorButtons
            :isCreating="isCreating"
            :isReadOnly="isReadOnly"
            :canDelete="true"
            :isAllowedEdit="isAllowedEdit"
            :haveChange="haveChange"
            :flowHaveTasks="Boolean(flowHaveTasks)"
            :errors="flowErrors"
            :warnings="flowWarnings"
            @save="save"
            @copy="
                () =>
                    router.push({
                        name: 'flows/create',
                        query: {copy: 'true'},
                        params:
                            {tenant: routeParams.tenant},
                    })
            "
            @export="exportYaml"
            @delete-flow="deleteFlow"
            :isNamespace="false"
        />
    </div>
</template>

<script lang="ts">
    export const FILES_SAVE_ALL_INJECTION_KEY = Symbol("FILES_SAVE_ALL_INJECTION_KEY") as InjectionKey<() => void>;
</script>

<script setup lang="ts">
    import {computed, getCurrentInstance, inject, InjectionKey} from "vue";
    import {useRouter, useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import EditorButtons from "./EditorButtons.vue";
    import FlowPlaygroundToggle from "./FlowPlaygroundToggle.vue";
    import ValidationError from "../flows/ValidationError.vue";

    import localUtils from "../../utils/utils";
    import {useFlowOutdatedErrors} from "./flowOutdatedErrors";
    import {useFlowStore} from "../../stores/flow";

    defineProps<{
        haveChange: boolean;
    }>();

    const {t} = useI18n();

    const exportYaml = () => {
        const src = flowStore.flowYaml
        if(!src) {
            return;
        }
        const blob = new Blob([src], {type: "text/yaml"});
        localUtils.downloadUrl(window.URL.createObjectURL(blob), "flow.yaml");
    };

    const flowStore = useFlowStore();
    const router = useRouter()
    const route = useRoute()
    const routeParams = computed(() => route.params)

    const {translateError, translateErrorWithKey} = useFlowOutdatedErrors();

    // If playground is not defined, enable it by default
    const isSettingsPlaygroundEnabled = computed(() => localStorage.getItem("editorPlayground") === "false" ? false : true);

    const isCreating = computed(() => flowStore.isCreating === true)
    const isReadOnly = computed(() => flowStore.isReadOnly)
    const isAllowedEdit = computed(() => flowStore.isAllowedEdit)
    const flowHaveTasks = computed(() => flowStore.flowHaveTasks)
    const flowErrors = computed(() => flowStore.flowErrors?.map(translateError));
    const flowInfos = computed(() => flowStore.flowInfos)
    const toast = getCurrentInstance()?.appContext.config.globalProperties.$toast();
    const flowWarnings = computed(() => {

        const outdatedWarning =
            flowStore.flowValidation?.outdated && !flowStore.isCreating
                ? [translateErrorWithKey(flowStore.flowValidation?.constraints ?? "")]
                : [];

        const deprecationWarnings =
            flowStore.flowValidation?.deprecationPaths?.map(
                (f: string) => `${f} ${t("is deprecated")}.`
            ) ?? [];

        const otherWarnings = flowStore.flowValidation?.warnings ?? [];

        const warnings = [
            ...outdatedWarning,
            ...deprecationWarnings,
            ...otherWarnings,
        ];

        return warnings.length === 0 ? undefined : warnings;
    });

    const onSaveAll = inject(FILES_SAVE_ALL_INJECTION_KEY);

    async function save(){
        const creating = isCreating.value
        await flowStore.saveAll()

        if(creating){
            await router.push({
                name: "flows/update",
                params: {
                    id: flowStore.flow?.id,
                    namespace: flowStore.flow?.namespace,
                    tab: "edit",
                    tenant: routeParams.value.tenant,
                },
            });
        }

        onSaveAll?.();
    }

    const deleteFlow = () => {
        const flowId = flowStore.flowYamlMetadata?.id;

        flowStore.deleteFlowAndDependencies()
            .then(() => {
                toast.deleted(flowId);
                return router.push({
                    name: "flows/list",
                    params: {
                        tenant: routeParams.value.tenant,
                    },
                });
            })
            .catch(() => {
                toast.error(`Failed to delete flow ${flowId}`);
            });
    };
</script>

<style scoped lang="scss">
    .button-wrapper {
        display: flex;
        align-items: center;
        margin: .5rem;
        gap: .5rem;
    }
</style>