<template>
    <Navbar :title="routeInfo.title">
        <template #actions>
            <Action
                v-if="!isOSS && canCreate"
                :label="$t('create')"
                :to="{name: 'namespaces/create', params: {tab: 'edit'}}"
            />
        </template>
    </Navbar>

    <KsRow class="p-5">
        <KSFilter
            :configuration="namespacesFilter"
            :prefix="'namespaces-list'"
            :tableOptions="{
                chart: {shown: false},
                columns: {shown: false},
                refresh: {shown: false}
            }"
            :searchInputFullWidth="true"
            :buttons="{
                savedFilters: {shown: false},
                tableOptions: {shown: false}
            }"
        />

        <KsCol v-if="namespaces.length === 0" class="p-3 namespaces">
            <span>{{ $t("no_namespaces") }}</span>
        </KsCol>

        <KsCol
            v-for="namespace in namespacesHierarchy"
            :key="namespace.id"
            class="namespaces"
            :class="{system: namespace.id === systemNamespace}"
        >
            <KsTree
                :data="[namespace]"
                defaultExpandAll
                :props="({class: 'tree'} as any)"
                class="h-auto p-2 rounded-full"
            >
                <template #default="{data}">
                    <router-link
                        :to="{
                            name: 'namespaces/update',
                            params: {
                                id: data.id,
                                tab: data.system ? 'blueprints' : 'overview',
                            },
                        }"
                        tag="div"
                        class="node"
                    >
                        <div class="d-flex">
                            <FolderOpenOutline class="me-2 icon" />
                            <span class="pe-3">
                                {{ namespaceLabel(data.label) }}
                            </span>
                            <slot name="description" :namespace="data" />
                            <span v-if="data.system" class="system">
                                {{ $t("system_namespace") }}
                            </span>
                        </div>
                        <KsButton size="small">
                            <TextSearch />
                        </KsButton>
                    </router-link>
                </template>
            </KsTree>
        </KsCol>
    </KsRow>
</template>

<script setup lang="ts">
    import {computed, Ref, ref, watch} from "vue"

    import {useRoute} from "vue-router"
    import useRouteContext from "../../../composables/useRouteContext"
    import useNamespaces, {Namespace} from "../../../composables/useNamespaces"
    import {useI18n} from "vue-i18n"
    import {useMiscStore} from "override/stores/misc"

    import Navbar from "../../../components/layout/TopNavBar.vue"
    import Action from "../../../components/namespaces/components/buttons/Action.vue"
    import {KsFilter as KSFilter} from "@kestra-io/design-system"
    import {useNamespacesFilter} from "../../../components/filter/configurations"
    import resource from "../../../models/resource"
    import action from "../../../models/action"

    import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue"
    import TextSearch from "vue-material-design-icons/TextSearch.vue"
    import {useAuthStore} from "override/stores/auth"

    const namespacesFilter = useNamespacesFilter()

    interface Node {
        id: string;
        label: string;
        description?: string;
        disabled?: boolean;
        children?: Node[];
        system?: boolean;
    }

    const route = useRoute()

    const {t} = useI18n({useScope: "global"})

    const routeInfo = computed(() => ({title: t("namespaces")}))
    useRouteContext(routeInfo)

    const authStore = useAuthStore()
    const canCreate = computed(() => {
        return authStore.user?.hasAnyAction(resource.NAMESPACE, action.CREATE)
    })

    const namespaces = ref([]) as Ref<Namespace[]>
    const loadData = async () => {
        namespaces.value = await useNamespaces(
            1000,
            route.query?.["filters[q][EQUALS]"] === undefined ? undefined : {q: route.query["filters[q][EQUALS]"]},
        ).all()
    }

    watch(
        () => route.query["filters[q][EQUALS]"],
        () => loadData(),
        {immediate: true},
    )

    const miscStore = useMiscStore()
    const systemNamespace = computed(
        () => miscStore.configs?.systemNamespace || "system",
    )

    const isOSS = computed(() => useMiscStore().configs?.edition === "OSS")

    const namespacesHierarchy = computed(() => {
        if (namespaces.value === undefined || namespaces.value.length === 0) {
            return []
        }

        const map = {} as Node[]

        namespaces.value.forEach((item) => {
            const parts = item.id.split(".")
            let currentLevel = map as any

            parts.forEach((_part, index) => {
                const label = parts.slice(0, index + 1).join(".")
                const isLeaf = index === parts.length - 1

                if (!currentLevel[label])
                    currentLevel[label] = {
                        id: label,
                        label,
                        description: isLeaf ? item.description : undefined,
                        children: [],
                    }
                currentLevel = currentLevel[label].children
            })
        })

        const build = (nodes: Node[]): Node[] => {
            return Object.values(nodes).map((node) => {
                const result: Node = {
                    id: node.id,
                    label: node.label,
                    description: node.description,
                    children: node.children ? build(node.children) : undefined,
                }
                return result
            })
        }

        const result = build(map)

        const system = result.findIndex(
            (namespace) => namespace.id === systemNamespace.value,
        )

        if (system !== -1) {
            const [systemItem] = result.splice(system, 1)
            result.unshift({...systemItem, system: true})
        }

        return result
    })

    const namespaceLabel = (path: string) => {
        const segments = path.split(".")
        return segments.length > 1 ? segments[segments.length - 1] : path
    }
</script>

<style scoped lang="scss">

.namespaces {
    margin: 0.25rem 0;
    border-radius: var(--kel-border-radius-round);
    border: 1px solid var(--ks-border-default);
    box-shadow: 0px 2px 4px 0px var(--ks-shadow-element);

    &.system {
        border-color: var(--ks-border-focus);

        & span.system {
            line-height: 1.5rem;
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-primary);
        }
    }

    .rounded-full {
        border-radius: var(--kel-border-radius-round);
        background-color: var(--ks-bg-surface)
    }

    :deep(.kel-tree-node__content) {
        height: 2.25rem;
        overflow: hidden;
        background: transparent;
        border-radius: var(--kel-border-radius-round);

        &:hover {
            background: var(--ks-bg-body);
        }

        .icon {
            color: var(--ks-text-link);
        }
    }

    .node {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 0.5rem;
        color: var(--ks-text-primary);

        &:hover {
            background: transparent;
            color: var(--ks-text-link);
        }
    }
}
</style>
