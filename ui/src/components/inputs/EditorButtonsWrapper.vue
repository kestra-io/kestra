<template>
    <div class="button-wrapper">
        <FlowPlaygroundToggle v-if="isSettingsPlaygroundEnabled && !onboardingStore.isGuidedActive" />

        <ValidationError
            class="validation"
            tooltipPlacement="bottom-start"
            :errors="flowStore.flowErrors"
            :warnings="flowWarnings"
            :infos="flowStore.flowInfos"
        />

        <EditorButtons
            :isCreating="flowStore.isCreating"
            :isReadOnly="flowStore.isReadOnly"
            :canDelete="true"
            :isAllowedEdit="flowStore.isAllowedEdit"
            :haveChange="haveChange"
            :flowHaveTasks="Boolean(flowStore.flowHaveTasks)"
            :errors="flowStore.flowErrors"
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
    import {computed, inject, InjectionKey} from "vue";
    import {useRouter, useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import EditorButtons from "./EditorButtons.vue";
    import FlowPlaygroundToggle from "./FlowPlaygroundToggle.vue";
    import ValidationError from "../flows/ValidationError.vue";

    import localUtils from "../../utils/utils";
    import {isSuccessfulFlowSaveOutcome, useFlowStore} from "../../stores/flow";
    import {useOnboardingV2Store} from "../../stores/onboardingV2";
    import {useToast} from "../../utils/toast";

    defineProps<{
        haveChange: boolean;
    }>();

    const {t} = useI18n();

    const exportYaml = () => {
        if(!flowStore.flow || !flowStore.flowYaml) return;

        const {id, namespace} = flowStore.flow;
        const blob = new Blob([flowStore.flowYaml], {type: "text/yaml"});

        localUtils.downloadUrl(window.URL.createObjectURL(blob), `${namespace}.${id}.yaml`);
    };

    const flowStore = useFlowStore();
    const onboardingStore = useOnboardingV2Store();
    const router = useRouter()
    const route = useRoute()
    const routeParams = computed(() => route.params)

    // If playground is not defined, enable it by default
    const isSettingsPlaygroundEnabled = computed(() => localStorage.getItem("editorPlayground") === "false" ? false : true);

    const toast = useToast();
    const flowWarnings = computed(() => {
        const outdatedWarning =
            flowStore.flowValidation?.outdated && !flowStore.isCreating
                ? flowStore.flowValidation?.constraints?.split(", ") ?? []
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
        try {
            // Save the isCreating before saving.
            // saveAll can change its value.
            const isCreating = flowStore.isCreating;
            const outcome = await flowStore.saveAll();
            if (isSuccessfulFlowSaveOutcome(outcome)) {
                onboardingStore.recordSave();
            }

            if (isCreating && outcome === "redirect_to_update") {
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
        } catch (error: any) {
            if (error?.status === 401) {
                toast.error("401 Unauthorized", undefined, {duration: 2000});
                return;
            }
        }
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
    @media screen and (max-width: 768px) {
        .button-wrapper {
            flex-wrap: wrap;
            justify-content: space-evenly;
        }
    }
</style>
