<template>
    <ElTree
        ref="treeRef"
        v-bind="{...filteredProps(), ...$attrs}"
        @node-drag-start="(node, event) => emit('nodeDragStart', node, event)"
        @node-drop="(draggingNode, dropNode, dropType, event) => emit('nodeDrop', draggingNode, dropNode, dropType, event)"
        @node-click="(data, node, el, event) => emit('nodeClick', data, node, el, event)"
    >
        <template v-if="$slots.default" #default="scope">
            <slot v-bind="scope" />
        </template>
        <template v-if="$slots.empty" #empty>
            <slot name="empty" />
        </template>
    </ElTree>
</template>

<script setup lang="ts">
    import {ref, type ComponentInternalInstance} from "vue"
    import {
        ElTree,
        type AllowDropFunction,
        type LoadFunction,
        type NodeDropType,
        type TreeData,
        type TreeInstance,
        type TreeKey,
        type TreeNodeData,
        type TreeOptionProps,
    } from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        data?: TreeData
        lazy?: boolean
        load?: LoadFunction
        allowDrop?: AllowDropFunction
        draggable?: boolean
        nodeKey?: string
        props?: TreeOptionProps
        defaultExpandAll?: boolean
        defaultExpandedKeys?: TreeKey[]
        defaultCheckedKeys?: TreeKey[]
    }>()

    type TreeNode = Parameters<AllowDropFunction>[0]

    const emit = defineEmits<{
        nodeDragStart: [node: TreeNode, event: DragEvent]
        nodeDrop: [draggingNode: TreeNode, dropNode: TreeNode, dropType: NodeDropType, event: DragEvent]
        nodeClick: [data: TreeNodeData, node: TreeNode, el: ComponentInternalInstance | null, event: MouseEvent]
    }>()

    defineSlots<{
        default?: (scope: {node: TreeNode; data: TreeNodeData}) => unknown
        empty?(): unknown
    }>()

    const treeRef = ref<InstanceType<typeof ElTree>>()

    const filteredProps = useFilteredProps(props)

    defineExpose({
        getNode: (data: TreeKey | TreeNodeData | TreeNode) => treeRef.value?.getNode(data),
        remove: (data: TreeKey | TreeNodeData | TreeNode) => {
            const node = typeof data === "string" || typeof data === "number" ? treeRef.value?.getNode(data) : data
            if (node) treeRef.value?.remove(node)
        },
        append: (data: TreeNodeData, parent: TreeNodeData | TreeKey | TreeNode) => treeRef.value?.append(data, parent),
        getCheckedNodes: (...args: Parameters<TreeInstance["getCheckedNodes"]>) => treeRef.value?.getCheckedNodes(...args),
        setCheckedKeys: (...args: Parameters<TreeInstance["setCheckedKeys"]>) => treeRef.value?.setCheckedKeys(...args),
        getCurrentKey: () => treeRef.value?.getCurrentKey(),
        setCurrentKey: (key: TreeKey | null) => treeRef.value?.setCurrentKey(key),
    })
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tree';
</style>
