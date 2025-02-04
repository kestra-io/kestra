<template>
    <Navbar :title="routeInfo.title">
        <template #additional-right v-if="!isUserEmpty && user.hasAnyAction(permission.NAMESPACE, action.CREATE)">
            <ul>
                <li>
                    <el-button :icon="Plus" type="primary">
                        {{ $t('create') }}
                    </el-button>
                </li>
            </ul>
        </template>
    </Navbar>

    <el-row class="p-5">
        <KestraFilter :placeholder="$t('search')" />
        <el-col v-if="!namespaces || !namespaces.length" :span="24" class="p-3 my-2 namespaces empty">
            <span>{{ t("no_namespaces") }}</span>
        </el-col>
        <el-col
            v-for="(namespace, index) in hierarchy(namespaces)"
            :key="index"
            :span="24"
            class="my-1 namespaces"
            :class="{system: namespace.id === 'system'}"
        >
            <el-tree :data="[namespace]" default-expand-all class="h-auto p-2 rounded-full tree">
                <template #default="{data}">
                    <router-link :to="{name: 'namespaces/update', params: {id: data.id, tab: data.system ? 'blueprints': ''}}" tag="div" class="node">
                        <div class="d-flex">
                            <DotsSquare class="me-2 icon" />
                            <span class="pe-3">{{ namespaceLabel(data.label) }}</span>
                            <span v-if="data.system" class="system">{{ $t("system_namespace") }}</span>
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
    import {onMounted, watch, computed} from "vue";
    import {useStore} from "vuex";
    import {ElTree} from "element-plus";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import Navbar from "../layout/TopNavBar.vue";
    import KestraFilter from "../filter/KestraFilter.vue";

    import permission from "../../models/permission";
    import action from "../../models/action";

    import Plus from "vue-material-design-icons/Plus.vue";
    import DotsSquare from "vue-material-design-icons/DotsSquare.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import {useRoute} from "vue-router";

    const store = useStore();

    interface Namespace {
        id: string;
        disabled: boolean;
    }

    interface Node {
        id: string;
        label: string;
        disabled: boolean;
        children?: Node[];
        system?: boolean;
    }

    const routeInfo = computed(() => ({title: t("namespaces")}));
    const user = computed(() => store.state.auth.user);
    const isUserEmpty = computed(() => Object.keys(user.value).length === 0);

    const route = useRoute()

    const namespaces = computed(() => store.state.namespace.namespaces as Namespace[]);
    const loadData = () => {
        // TODO: Implement a new endpoint which does not require size limit but returns everything
        const query = {size: 10000, page: 1, ...(route.query?.q ? {q: route.query.q} : {})}
        store.dispatch("namespace/search", query);
    };

    onMounted(() => loadData());
    watch(() => route.query, () => loadData());

    const hierarchy = (data: Namespace[]): Node[] => {
        if (!data) return [];

        const map = {} as Node[];
        const roots: Node[] = [];

        data.forEach(item => {
            const parts = item.id.split(".");
            let currentLevel = map;

            parts.forEach((part, index) => {
                const label = parts.slice(0, index + 1).join(".");

                if (!currentLevel[label]) currentLevel[label] = {id: label, label, disabled: item.disabled, children: []};
                currentLevel = currentLevel[label].children;
            });

            if (parts.length === 1) {
                roots.push(map[item.id]);
            }
        });

        const build = (nodes: Node[]): Node[] => {
            return Object.values(nodes).map(node => {
                const result: Node = {id: node.id, label: node.label, disabled: node.disabled, children: node.children ? build(node.children) : undefined};
                return result;
            });
        };

        const result = build(map);

        const system = result.findIndex(namespace => namespace.id === "system");

        if (system !== -1) {
            const [systemItem] = result.splice(system, 1);
            result.unshift({...systemItem, system: true});
        }

        return result;
    };

    const namespaceLabel = (path) => {
        const segments = path.split(".");
        return segments.length > 1 ? segments[segments.length - 1] : path;
    };
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/color-palette.scss";

.filter {
    min-width: 200px;
    color: var(--bs-heading-color);
    font-size: var(--font-size-sm);

    & .el-input__wrapper {
        padding: 0 1rem;
        border-radius: var(--bs-border-radius-lg);

        &.is-focus {
            box-shadow: 0 0 0 1px var(--ks-border-primary) inset;
        }
    }
}

.namespaces {
    border-radius: var(--bs-border-radius-lg);
    border: 1px solid var(--ks-border-primary);
    box-shadow: 0px 2px 4px 0px var(--ks-card-shadow);

    &.system {
        border-color: $base-blue-300;
    }

    &.empty {
        font-size: var(--font-size-sm);
    }

    .tree {
        --el-tree-node-hover-bg-color: transparent;
        --el-fill-color-blank: var(--ks-background-card);
    }

    .rounded-full {
        border-radius: var(--bs-border-radius-lg);
    }

    :deep(.el-tree-node__content) {
        height: 2.25rem;
        overflow: hidden;
        background: transparent;

        &:hover {
            background: var(--ks-background-body);
            color: var(--ks-content-link);
        }
        .el-tree-node__expand-icon {
            display: none;
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
        padding: 0 1rem;
        color: var(--ks-content-primary);

        &:hover {
            background: transparent;
            color: var(--ks-content-link);
        }
    }
}
</style>