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
            :have-change="store.state.flow.haveChange || tabs.some(t => t.dirty === true)"
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
    import {useStore} from "vuex"
    import {useRouter, useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import EditorButtons from "./EditorButtons.vue";
    import FlowPlaygroundToggle from "./FlowPlaygroundToggle.vue";
    import ValidationError from "../flows/ValidationError.vue";

    import localUtils from "../../utils/utils";
    import {useFlowOutdatedErrors} from "./flowOutdatedErrors";

    const {t} = useI18n();

    const exportYaml = () => {
        const blob = new Blob([store.state.flow.flowYaml], {type: "text/yaml"});
        localUtils.downloadUrl(window.URL.createObjectURL(blob), "flow.yaml");
    };

    const store = useStore()
    const router = useRouter()
    const route = useRoute()
    const routeParams = computed(() => route.params)

    const {translateError, translateErrorWithKey} = useFlowOutdatedErrors();

    const isSettingsPlaygroundEnabled = computed(() => localStorage.getItem("editorPlayground") === "true");
    const isCreating = computed(() => store.state.flow.isCreating === true)
    const isReadOnly = computed(() => store.getters["flow/isReadOnly"])
    const isAllowedEdit = computed(() => store.getters["flow/isAllowedEdit"])
    const flowHaveTasks = computed(() => store.getters["flow/flowHaveTasks"])
    const flowErrors = computed(() => store.getters["flow/flowErrors"]?.map(translateError));
    const flowInfos = computed(() => store.getters["flow/flowInfos"])
    const flowParsed = computed(() => store.state.flow.flow)
    const tabs = computed<{dirty:boolean}[]>(() => store.state.editor.tabs)
    const metadata = computed(() => store.state.flow.metadata);
    const toast = getCurrentInstance()?.appContext.config.globalProperties.$toast();
    const flowWarnings = computed(() => {

        const outdatedWarning =
            store.state.flow.flowValidation?.outdated && !store.state.flow.isCreating
                ? [translateErrorWithKey(store.getters["flow/baseOutdatedTranslationKey"])]
                : [];

        const deprecationWarnings =
            store.state.flow.flowValidation?.deprecationPaths?.map(
                (f: string) => `${f} ${t("is deprecated")}.`
            ) ?? [];

        const otherWarnings = store.state.flow.flowValidation?.warnings ?? [];

        const warnings = [
            ...outdatedWarning,
            ...deprecationWarnings,
            ...otherWarnings,
        ];

        return warnings.length === 0 ? undefined : warnings;
    });

    async function save(){
        const creating = isCreating.value
        await store.dispatch("flow/saveAll")

        if(creating){
            await router.push({
                name: "flows/update",
                params: {
                    id: flowParsed.value.id,
                    namespace: flowParsed.value.namespace,
                    tab: "edit",
                    tenant: routeParams.value.tenant,
                },
            });
        }
    }

    const deleteFlow = () => {
        store.dispatch("flow/deleteFlowAndDependencies")
            .then(() => {
                return router.push({
                    name: "flows/list",
                    params: {
                        tenant: routeParams.value.tenant,
                    },
                });
            })
            .then(() => {
                toast.deleted(metadata.value.id);
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