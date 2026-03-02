<template>
    <Revisions
        v-if="revisions.length > 0"
        lang="yaml"
        :revisions="flowRevisions"
        :revisionSource="loadRevisionContent"
        @restore="restoreRevision"
        @deleted="onRevisionDeleted"
        class="flow-revisions"
    >
        <template #crud="{revision}">
            <Crud permission="FLOW" :detail="{resourceType: 'FLOW', namespace: route.params.namespace, flowId: route.params.id, revision}" />
        </template>
    </Revisions>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, watch} from "vue";
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
    const storeRevisions = computed(() => flowStore.revisions);

    // Load revisions from API only if not already in store
    onMounted(async () => {
        if (flow.value && (!storeRevisions.value || storeRevisions.value.length === 0)) {
            await flowStore.loadRevisions({
                namespace: flow.value.namespace,
                id: flow.value.id
            });
        }
    });

    // Watch for flow changes to reload revisions
    watch(flow, async (newFlow) => {
        if (newFlow && (!storeRevisions.value || storeRevisions.value.length === 0)) {
            await flowStore.loadRevisions({
                namespace: newFlow.namespace,
                id: newFlow.id
            });
        }
    });

    const revisions = ref<Array<{revision: number}>>([]);

    async function fetchRevisions() {
        const namespace = (route.params.namespace as string) ?? "";
        const id = (route.params.id as string) ?? "";
        if (!namespace || !id) {
            revisions.value = [];
            return;
        }

        try {
            const loaded = await flowStore.loadRevisions({
                namespace,
                id
            });
            revisions.value = loaded ?? [];
        } catch (err) {
            console.error("Failed to load revisions", err);
            revisions.value = [];
        }
    }

    onMounted(fetchRevisions);

    const flowRevisions = computed(() => {
        // Use store revisions if available (includes updated)
        if (storeRevisions.value && storeRevisions.value.length > 0) {
            return storeRevisions.value;
        }

        // Fallback to generating from flow.revision count (no timestamps)
        if (!flow.value) {
            return revisions.value;
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

    async function onRevisionDeleted(revision: number) {
        const updatedQuery = {...route.query};
        for (const key of ["revisionLeft", "revisionRight"]) {
            if ((updatedQuery as any)[key]?.toString() === `${revision}`) delete (updatedQuery)[key];
        }
        await router.push({query: updatedQuery});
        await fetchRevisions();
    }

    watch(() => [route.params.namespace, route.params.id], fetchRevisions);
</script>

<style scoped lang="scss">
    .flow-revisions {
        min-height: calc(100vh - 190px);
    }
</style>
