<script setup lang="ts">
    import {ref} from "vue"
    import {ElTree, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        data?: any[]
        lazy?: boolean
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        load?: (node: any, resolve: (data: any[]) => void) => void
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        allowDrop?: (draggingNode: any, dropNode: any, type: string) => boolean
        draggable?: boolean
        nodeKey?: string
        props?: {label?: string; children?: string; disabled?: string; isLeaf?: string}
        defaultExpandAll?: boolean
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        defaultExpandedKeys?: any[]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        defaultCheckedKeys?: any[]
    }>()

    const emit = defineEmits<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        nodeDragStart: [node: any, event: DragEvent]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        nodeDrop: [draggingNode: any, dropNode: any, dropType: string, event: DragEvent]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        nodeClick: [data: any, node: any, el: any, event: MouseEvent]
    }>()

    defineSlots<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        default?: (scope: {node: any; data: any}) => unknown
        empty?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)

    const treeRef = ref<InstanceType<typeof ElTree>>()

    defineExpose({
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        getNode: (data: any) => treeRef.value?.getNode(data),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        remove: (data: any) => treeRef.value?.remove(data),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        append: (data: any, parent: any) => treeRef.value?.append(data, parent),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        getCheckedNodes: (...args: any[]) => (treeRef.value?.getCheckedNodes as any)(...args),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        setCheckedKeys: (...args: any[]) => (treeRef.value?.setCheckedKeys as any)(...args),
        getCurrentKey: () => treeRef.value?.getCurrentKey(),
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        setCurrentKey: (key: any) => treeRef.value?.setCurrentKey(key),
    })
</script>

<template>
    <el-tree
        ref="treeRef"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @node-drag-start="(node, event) => emit('nodeDragStart', node, event)"
        @node-drop="(draggingNode, dropNode, dropType, event) => emit('nodeDrop', draggingNode, dropNode, dropType, event)"
        @node-click="(data, node, el, event) => emit('nodeClick', data, node, el, event)"
    >
        <template v-if="$slots.default" #default="scope"><slot v-bind="scope" /></template>
        <template v-if="$slots.empty" #empty><slot name="empty" /></template>
    </el-tree>
</template>
