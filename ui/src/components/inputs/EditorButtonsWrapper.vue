<template>
    <div class="button-wrapper">
        <FlowPlaygroundToggle v-if="isSettingsPlaygroundEnabled" />

        <ValidationError
            class="validation"
            tooltip-placement="bottom-start"
            :errors="flowErrors"
            :warnings="flowWarnings"
            :infos="flowInfos"
        />

        <EditorButtons
            :is-creating="isCreating"
            :is-read-only="isReadOnly"
            :can-delete="true"
            :is-allowed-edit="isAllowedEdit"
            :have-change="flowStore.haveChange || tabs.some(t => t.dirty === true)"
            :flow-have-tasks="Boolean(flowHaveTasks)"
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
            :is-namespace="false"
        />
    </div>
</template>

<script setup lang="ts">
    import {computed, getCurrentInstance} from "vue";
    import {useRouter, useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import EditorButtons from "./EditorButtons.vue";
    import FlowPlaygroundToggle from "./FlowPlaygroundToggle.vue";
    import ValidationError from "../flows/ValidationError.vue";

    import localUtils from "../../utils/utils";
    import {useFlowOutdatedErrors} from "./flowOutdatedErrors";
    import {useEditorStore} from "../../stores/editor";
    import {useFlowStore} from "../../stores/flow";

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
    const editorStore = useEditorStore();
    const router = useRouter()
    const route = useRoute()
    const routeParams = computed(() => route.params)

    const {translateError, translateErrorWithKey} = useFlowOutdatedErrors();

    const isSettingsPlaygroundEnabled = computed(() => localStorage.getItem("editorPlayground") === "true");
    const isCreating = computed(() => flowStore.isCreating === true)
    const isReadOnly = computed(() => flowStore.isReadOnly)
    const isAllowedEdit = computed(() => flowStore.isAllowedEdit)
    const flowHaveTasks = computed(() => flowStore.flowHaveTasks)
    const flowErrors = computed(() => flowStore.flowErrors?.map(translateError));
    const flowInfos = computed(() => flowStore.flowInfos)
    const tabs = computed<{dirty?:boolean}[]>(() => editorStore.tabs)
    const metadata = computed(() => flowStore.metadata);
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
    }

    const deleteFlow = () => {
        flowStore.deleteFlowAndDependencies()
            .then(() => {
                return router.push({
                    name: "flows/list",
                    params: {
                        tenant: routeParams.value.tenant,
                    },
                });
            })
            .then(() => {
                toast.deleted(metadata.value?.id);
            });
    };
</script>

<style lang="scss" scoped>
    .button-wrapper {
        display: flex;
        align-items: center;
        margin: .5rem;
        gap: .5rem;
    }
</style>