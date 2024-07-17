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
        <el-col v-for="(namespace, index) in hierarchy(namespaces)" :key="index" :span="24" class="my-2 p-3 namespaces">
            <el-tree :data="[namespace]" default-expand-all :props="{class: 'tree'}" class="h-auto">
                <template #default="{data}">
                    <div class="node">
                        <div class="d-flex">
                            <VectorIntersection class="me-2 icon" />
                            <span>{{ data.label }}</span>
                        </div>
                        <el-button
                            size="small"
                            tag="router-link"
                            :to="{name: 'namespaces/update', params: {id: data.id}}"
                        >
                            <TextSearch />
                        </el-button>
                    </div>
                </template>
            </el-tree>
        </el-col>
    </el-row>
</template>

<script setup lang="ts">
    import {onMounted, computed, watch, ref} from "vue";
    import {useRouter} from "vue-router";
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
    }

    const router = useRouter();
    const route = computed(() => ({title: t("namespaces")}));
    const user = computed(() => store.state.auth.user);
    const isUserEmpty = computed(() => Object.keys(user.value).length === 0);

    const filter = ref("");
    watch(filter, () => loadData());

    const namespaces = computed(() => store.state.namespace.namespaces as Namespace[]);
    const loadData = () => {
        const query = filter.value ? {q: filter.value} : undefined;

        router.push({query});

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

        return build(map);
    };
</script>

<style lang="scss">
$width: 200px;
$active: #A396FF;

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
    background: var(--bs-body-bg);

    &.empty {
        font-size: var(--font-size-sm);
    }

    .tree {
        --el-tree-node-hover-bg-color: transparent;
    }

    .el-tree-node__content {
        height: 2.25rem;
        overflow: hidden;
        background: var(--bs-body-bg);

        &:hover {
            background: var(--bs-body-bg);
            color: $active;
        }

        .el-tree-node__expand-icon {
            display: none;
        }

        .icon {
            color: $active;
        }
    }

    .node {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: space-between;
    }
}
</style>