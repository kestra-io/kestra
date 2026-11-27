<template>
    <ElTree
        ref="treeRef"
        v-bind="{...filteredProps(), ...$attrs}"
        :class="{'ks-tree--files': variant === 'files'}"
        :load="load && loadNode"
        :allowDrop="allowDrop && canDrop"
        @node-drag-start="(node, event) => emit('nodeDragStart', own(node), event)"
        @node-drop="(draggingNode, dropNode, dropType, event) => emit('nodeDrop', own(draggingNode), own(dropNode), dropType, event)"
        @node-click="(_, node, nodeInstance, event) => emit('nodeClick', own(node).data, own(node), nodeInstance, event)"
    >
        <template v-if="$slots.default" #default="{node}">
            <slot :node="own(node)" :data="own(node).data" />
        </template>
        <template v-if="$slots.empty" #empty>
            <slot name="empty" />
        </template>
    </ElTree>
</template>

<script lang="ts">
    import type {RenderContentContext, TreeNodeData, TreeOptionProps, TreeKey} from "element-plus"

    /** A tree node, holding one of the items given through `data` or resolved by `load`. */
    export type KsTreeNode<T extends TreeNodeData = TreeNodeData> = RenderContentContext["node"] & {data: T}

    export interface KsTreeInstance<T extends TreeNodeData = TreeNodeData> {
        getNode: (data?: TreeKey | T | null) => KsTreeNode<T> | undefined
        remove: (data: T | KsTreeNode<T>) => void
        append: (data: T, parent?: T | TreeKey | KsTreeNode<T> | null) => void
        getCheckedNodes: (leafOnly?: boolean, includeHalfChecked?: boolean) => T[] | undefined
        setCheckedKeys: (keys: TreeKey[], leafOnly?: boolean) => void
        getCurrentKey: () => TreeKey | undefined
        setCurrentKey: (key?: TreeKey | null) => void
    }
</script>

<script setup lang="ts" generic="T extends TreeNodeData">
    import {ref, type ComponentInternalInstance, type HTMLAttributes} from "vue"
    import {
        ElTree,
        type TreeComponentProps,
        type AllowDropFunction,
        type AllowDropType,
        type LoadFunction,
        type NodeDropType,
    } from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"
    import type {NativeAttributes} from "../../utils/nativeAttributes"

    defineOptions({inheritAttrs: false})

    type KsTreeProps = {
        data?: T[]
        lazy?: boolean
        load?: (node: KsTreeNode<T>, resolve: (data: T[]) => void, reject: () => void) => void
        allowDrop?: (draggingNode: KsTreeNode<T>, dropNode: KsTreeNode<T>, type: AllowDropType) => boolean
        draggable?: boolean
        nodeKey?: string
        props?: TreeOptionProps
        defaultExpandAll?: boolean
        defaultExpandedKeys?: TreeKey[]
        defaultCheckedKeys?: TreeKey[]
        /** `files`: a file explorer, nodes indented by a guide line, outlined on hover, filled when current or carrying the `is-selected` class. */
        variant?: "files"
    }

    type ElTreeProps = Partial<TreeComponentProps>
    const props = defineProps<KsTreeProps & /* @vue-ignore */ Omit<NativeAttributes<ElTreeProps & HTMLAttributes>, keyof KsTreeProps>>()

    const emit = defineEmits<{
        nodeDragStart: [node: KsTreeNode<T>, event: DragEvent]
        nodeDrop: [draggingNode: KsTreeNode<T>, dropNode: KsTreeNode<T>, dropType: Exclude<NodeDropType, "none">, event: DragEvent]
        nodeClick: [data: T, node: KsTreeNode<T>, nodeInstance: ComponentInternalInstance | null, event: MouseEvent]
    }>()

    defineSlots<{
        default?: (scope: {node: KsTreeNode<T>; data: T}) => unknown
        empty?(): unknown
    }>()

    const treeRef = ref<InstanceType<typeof ElTree>>()

    const filteredProps = useFilteredProps(props, ["load", "allowDrop", "variant"])

    // ElTree builds every node from `data` or from what `load` resolves, so each node it hands back holds a T.
    // Its own types cannot say so, hence this one assertion.
    const own = (node: KsTreeNode) => node as KsTreeNode<T>
    const loadNode: LoadFunction = (node, resolve, reject) => props.load?.(own(node), resolve, reject)
    const canDrop: AllowDropFunction = (draggingNode, dropNode, type) => props.allowDrop?.(own(draggingNode), own(dropNode), type) ?? true

    defineExpose({
        getNode: (data: TreeKey | T) => treeRef.value && own(treeRef.value.getNode(data)),
        remove: (data: T | KsTreeNode<T>) => treeRef.value?.remove(data),
        append: (data: T, parent: T | TreeKey | KsTreeNode<T>) => treeRef.value?.append(data, parent),
        getCheckedNodes: (leafOnly?: boolean, includeHalfChecked?: boolean) => treeRef.value?.getCheckedNodes(leafOnly, includeHalfChecked),
        setCheckedKeys: (keys: TreeKey[], leafOnly?: boolean) => treeRef.value?.setCheckedKeys(keys, leafOnly),
        getCurrentKey: () => treeRef.value?.getCurrentKey(),
        setCurrentKey: (key?: TreeKey | null) => treeRef.value?.setCurrentKey(key),
    })
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tree';
</style>

<style>
    .kel-tree {
        --kel-tree-node-content-height: var(--ks-tree-node-height, 26px);
        --kel-tree-node-hover-bg-color: var(--ks-tree-node-hover-bg, var(--kel-fill-color-light));

        & .kel-tree-node__content {
            border-radius: var(--ks-tree-node-radius, 0);
            overflow: var(--ks-tree-node-overflow, visible);
        }

        & .kel-tree-node:focus > .kel-tree-node__content {
            background-color: var(--ks-tree-node-focus-bg, var(--kel-tree-node-hover-bg-color));
        }

        &.ks-tree--files {
            --kel-tree-node-hover-bg-color: transparent;

            & .kel-tree__empty-block {
                height: auto;
            }

            & .kel-tree-node__content {
                display: flex;
                align-items: center;
                margin-bottom: 2px !important;
                padding-left: 0 !important;
                border: 1px solid transparent;

                &:last-child {
                    margin-bottom: 0;
                }

                &:hover {
                    background: none;
                    border: 1px solid var(--ks-border-focus);
                }
            }

            & .is-expanded .kel-tree-node__children {
                margin-left: 11px !important;
                padding-left: 0 !important;
                border-left: 1px solid var(--ks-border-default);
            }

            & .kel-tree-node.is-current > .kel-tree-node__content {
                min-width: fit-content;
                border: 1px solid var(--ks-border-focus);
                background: var(--ks-btn-primary-bg-default);
            }

            & .kel-tree-node.is-selected > .kel-tree-node__content {
                min-width: fit-content;
                background-color: var(--ks-btn-primary-bg-default);
            }
        }
    }
</style>
