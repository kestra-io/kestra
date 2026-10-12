<template>
    <ElTree
        ref="treeRef"
        v-bind="{...filteredProps(), ...$attrs}"
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
    import type {RenderContentContext, TreeNodeData} from "element-plus"

    /** A tree node, holding one of the items given through `data` or resolved by `load`. */
    export type KsTreeNode<T extends TreeNodeData = TreeNodeData> = RenderContentContext["node"] & {data: T}
</script>

<script setup lang="ts" generic="T extends TreeNodeData">
    import {ref, type ComponentInternalInstance} from "vue"
    import {
        ElTree,
        type AllowDropFunction,
        type AllowDropType,
        type LoadFunction,
        type NodeDropType,
        type TreeKey,
        type TreeOptionProps,
    } from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
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
    }>()

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

    const filteredProps = useFilteredProps(props, ["load", "allowDrop"])

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
