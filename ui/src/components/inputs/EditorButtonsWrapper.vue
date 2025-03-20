<template>
    <div style="display: flex; align-items: center; margin: .5rem; gap: .5rem;">
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
            :have-change="tabs.some(t => t.dirty === true)"
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
    import EditorButtons from "./EditorButtons.vue";
    import ValidationError from "../flows/ValidationError.vue";

    import localUtils from "../../utils/utils";

    const exportYaml = () => {
        const blob = new Blob([store.getters["flow/flowYaml"]], {type: "text/yaml"});
        localUtils.downloadUrl(window.URL.createObjectURL(blob), "flow.yaml");
    };

    const store = useStore()
    const router = useRouter()
    const route = useRoute()
    const routeParams = computed(() => route.params)

    const isCreating = computed(() => store.state.flow.isCreating === true)
    const isReadOnly = computed(() => store.getters["flow/isReadOnly"])
    const isAllowedEdit = computed(() => store.getters["flow/isAllowedEdit"])
    const flowHaveTasks = computed(() => store.getters["flow/flowHaveTasks"])
    const flowErrors = computed(() => store.getters["flow/flowErrors"])
    const flowWarnings = computed(() => store.getters["flow/flowWarnings"])
    const flowInfos = computed(() => store.getters["flow/flowInfos"])
    const flowParsed = computed(() => store.getters["flow/flow"])
    const tabs = computed<{dirty:boolean}[]>(() => store.state.editor.tabs)
    const metadata = computed(() => store.state.flow.metadata);
    const toast = getCurrentInstance().appContext.config.globalProperties.$toast();

    async function save(){
        await store.dispatch("flow/saveAll")

        if(isCreating.value){
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