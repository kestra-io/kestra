<template>
    <Navbar :title="route.title">
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
        <el-input v-model="filter" placeholder="Search" clearable class="w-25 pb-2 filter">
            <template #prefix>
                <Magnify />
            </template>
        </el-input>
        <el-col v-if="!namespaces || !namespaces.length" :span="24" class="my-2 p-3 namespaces empty">
            <span>{{ t("no_namespaces") }}</span>
        </el-col>
        <el-col
            v-for="(namespace, index) in hierarchy(namespaces)"
            :key="index"
            :span="24"
            class="my-1 namespaces"
            :class="{system: namespace.id === 'system'}"
        >
            <el-tree :data="[namespace]" default-expand-all :props="{class: 'tree'}" class="h-auto  py-2 px-4 rounded-full">
                <template #default="{data}">
                    <router-link :to="{name: 'namespaces/update', params: {id: data.id, tab: data.system ? 'blueprints': ''}}" tag="div" class="node">
                        <div class="d-flex">
                            <VectorIntersection class="me-2 icon" />
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
    import {onMounted, computed, watch, ref} from "vue";
    import {useStore} from "vuex";
    import {ElTree} from "element-plus";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import Navbar from "../layout/TopNavBar.vue";

    import permission from "../../models/permission";
    import action from "../../models/action";

    import Plus from "vue-material-design-icons/Plus.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import VectorIntersection from "vue-material-design-icons/VectorIntersection.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";

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

    const route = computed(() => ({title: t("namespaces")}));
    const user = computed(() => store.state.auth.user);
    const isUserEmpty = computed(() => Object.keys(user.value).length === 0);

    const filter = ref("");
    watch(filter, () => loadData());

    const namespaces = computed(() => store.state.namespace.namespaces as Namespace[]);
    const loadData = () => {
        // TODO: Implement a new endpoint which does not require size limit but returns everything
        const query = {size: 10000, page: 1, ...(filter.value ? {q: filter.value} : {})};
        store.dispatch("namespace/search", query);
    };

    onMounted(() => loadData());

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

<style lang="scss">
$width: 200px;
$active: #A396FF;
$system: #5BB8FF;

.filter {
    min-width: $width;
    color: var(--bs-heading-color);
    font-size: var(--font-size-sm);

    & .el-input__wrapper {
        padding: 0 1rem;
        border-radius: var(--bs-border-radius-lg);

        &.is-focus {
            box-shadow: 0 0 0 1px var(--bs-border-color) inset;
        }
    }
}

.namespaces {
    border-radius: var(--bs-border-radius-lg);
    border: 1px solid var(--bs-border-color);

    &.system {
        border-color: $system;

        .el-tree-node__content .icon {
            color: $system;
        }
    }

    &.empty {
        font-size: var(--font-size-sm);
    }

    .tree {
        --el-tree-node-hover-bg-color: transparent;
    }

    .rounded-full {
        border-radius: var(--bs-border-radius-lg);
    }

    .el-tree-node__content {
        height: 2.25rem;
        overflow: hidden;
        background: transparent;

        .icon {
            color: $active;
        }
    }

    .node {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: space-between;
        color: var(--el-text-color-regular);

        &.system {
            color: $system;
        }

        &:hover {
            background: transparent;
        }

        & .system {
            color: var(--el-text-color-placeholder);
            font-size: var(--font-size-sm);
        }
    }
}
</style>