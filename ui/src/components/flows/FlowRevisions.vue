<template>
    <Revisions
        lang="yaml"
        :revisions="flowRevisions"
        :revisionSource="loadRevisionContent"
        @restore="restoreRevision"
        class="flow-revisions"
    >
        <template #crud="{revision}">
            <Crud permission="FLOW" :detail="{resourceType: 'FLOW', namespace: route.params.namespace, flowId: route.params.id, revision}" />
        </template>
    </Revisions>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import Crud from "override/components/auth/Crud.vue";
    import Revisions from "../layout/Revisions.vue";

    import {useToast} from "../../utils/toast";
    import {useFlowStore} from "../../stores/flow";
    const route = useRoute();
    const router = useRouter();
    const toast = useToast();

    const flowStore = useFlowStore();
    const flow = computed(() => flowStore.flow);

    const flowRevisions = computed(() => {
        if (!flow.value) {
            return [];
        }

        return [...Array(flow.value.revision).keys()].map(idx => ({revision: idx + 1}));
    })

    async function restoreRevision(revisionSource: string) {
        return flowStore.saveFlow({flow: revisionSource})
            .then((response:any) => {
                toast.saved(response.id);
                flowStore.flowYaml = response.source;
            })
            .then(() => {
                router.push({query: {}});
            });
    }

    async function loadRevisionContent(revision: number) {
        if (revision === undefined) {
            return undefined;
        }

        return (await flowStore.loadFlow({
            namespace: flow.value?.namespace ?? "",
            id: flow.value?.id ?? "",
            revision: revision.toString(),
            allowDeleted: true,
            store: false
        })).source;
    }
</script>

<style scoped lang="scss">
    .flow-revisions {
        min-height: calc(100vh - 190px);
    }
</style>
