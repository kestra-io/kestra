<template>
    <Navbar :title="details.title">
        <template #additional-right>
            <Action
                v-if="canCreate"
                :label="t('create')"
                :to="{name: 'namespaces/create', params: {tab: 'edit'}}"
            />
        </template>
    </Navbar>

    <el-row class="p-5">
        <KestraFilter
            :placeholder="t('search')"
            legacy-query
        />

        <el-col v-if="namespaces.length === 0" class="p-3 namespaces">
            <span>{{ t("no_namespaces") }}</span>
        </el-col>

        <el-col
            v-for="namespace in namespacesHierarchy"
            :key="namespace.id"
            class="namespaces"
            :class="{system: namespace.id === systemNamespace}"
        >
            <el-tree
                :data="[namespace]"
                default-expand-all
                :props="{class: 'tree'}"
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
                            <DotsSquare class="me-2 icon" />
                            <span class="pe-3">
                                {{ namespaceLabel(data.label) }}
                            </span>
                            <slot name="description" :namespace="data" />
                            <span v-if="data.system" class="system">
                                {{ t("system_namespace") }}
                            </span>
                        </div>
                        <el-button size="small">
                            <TextSearch />
                        </el-button>
                    </router-link>
                </template>
            </el-tree>
        </el-col>
    </el-row>
</template>

<script setup lang="ts">
    import {computed, onMounted, Ref, ref, watch} from "vue";

    import {useRoute} from "vue-router";
    import useRouteContext from "../../../mixins/useRouteContext.ts";
    import {useStore} from "vuex";
    import useNamespaces, {Namespace} from "../../../composables/useNamespaces.ts";
    import {useI18n} from "vue-i18n";
    import {useMiscStore} from "override/stores/misc";

    import Navbar from "../../../components/layout/TopNavBar.vue";
    import Action from "../../../components/namespaces/components/buttons/Action.vue";
    import KestraFilter from "../../../components/filter/KestraFilter.vue";

    import permission from "../../../models/permission.ts";
    import action from "../../../models/action.ts";

    import DotsSquare from "vue-material-design-icons/DotsSquare.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";

    interface Node {
        id: string;
        label: string;
        description?: string;
        disabled: boolean;
        children?: Node[];
        system?: boolean;
    }

    const route = useRoute();

    const {t} = useI18n({useScope: "global"});

    const details = computed(() => ({title: t("namespaces")}));
    useRouteContext(details);

    const store = useStore();

    const user = computed(() => store.state.auth.user);
    const canCreate = computed(() => {
        if (Object.keys(user.value).length === 0) return false;
        return user.value.hasAnyAction(permission.NAMESPACE, action.CREATE);
    });

    const namespaces = ref([]) as Ref<Namespace[]>;
    const loadData = async () => {
        namespaces.value = await useNamespaces(
            store,
            1000,
            route.query?.q === undefined ? undefined : {q: route.query.q},
        ).all();
    };

    onMounted(() => loadData());
    watch(
        () => route.query,
        () => loadData(),
    );

    const miscStore = useMiscStore();
    const systemNamespace = computed(
        () => miscStore.configs?.systemNamespace || "system",
    );

    const namespacesHierarchy = computed(() => {
        if (namespaces.value === undefined || namespaces.value.length === 0) {
            return [];
        }

        const map = {} as Node[];

        namespaces.value.forEach((item) => {
            const parts = item.id.split(".");
            let currentLevel = map;

            parts.forEach((part, index) => {
                const label = parts.slice(0, index + 1).join(".");
                const isLeaf = index === parts.length - 1;

                if (!currentLevel[label])
                    currentLevel[label] = {
                        id: label,
                        label,
                        description: isLeaf ? item.description : undefined,
                        children: [],
                    };
                currentLevel = currentLevel[label].children;
            });
        });

        const build = (nodes: Node[]): Node[] => {
            return Object.values(nodes).map((node) => {
                const result: Node = {
                    id: node.id,
                    label: node.label,
                    description: node.description,
                    children: node.children ? build(node.children) : undefined,
                };
                return result;
            });
        };

        const result = build(map);

        const system = result.findIndex(
            (namespace) => namespace.id === systemNamespace.value,
        );

        if (system !== -1) {
            const [systemItem] = result.splice(system, 1);
            result.unshift({...systemItem, system: true});
        }

        return result;
    });

    const namespaceLabel = (path) => {
        const segments = path.split(".");
        return segments.length > 1 ? segments[segments.length - 1] : path;
    };
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/color-palette.scss";

.namespaces {
    margin: 0.25rem 0;
    border-radius: var(--bs-border-radius-lg);
    border: 1px solid var(--ks-border-primary);
    box-shadow: 0px 2px 4px 0px var(--ks-card-shadow);

    &.system {
        border-color: $base-blue-300;

        & span.system {
            line-height: 1.5rem;
            font-size: var(--font-size-xs);
            color: var(--ks-content-primary);
        }
    }

    .rounded-full {
        border-radius: var(--bs-border-radius-lg);
        background-color: var(--ks-background-card)
    }

    :deep(.el-tree-node__content) {
        height: 2.25rem;
        overflow: hidden;
        background: transparent;
        border-radius: var(--bs-border-radius-lg);

        &:hover {
            background: var(--ks-background-body);
        }

        .icon {
            color: var(--ks-content-link);
        }
    }

    .node {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0 0.5rem;
        color: var(--ks-content-primary);

        &:hover {
            background: transparent;
            color: var(--ks-content-link);
        }
    }
}
</style>
