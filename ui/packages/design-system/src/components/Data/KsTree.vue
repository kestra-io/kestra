<template>
    <ElTree
        ref="treeRef"
        v-bind="({...filteredProps(), ...$attrs} as any)"
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
    import {ref} from "vue"
    import {ElTree} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        data?: any[]
        lazy?: boolean
        load?: (node: any, resolve: (data: any[]) => void) => void
        allowDrop?: (draggingNode: any, dropNode: any, type: string) => boolean
        draggable?: boolean
        nodeKey?: string
        props?: {label?: string; children?: string; disabled?: string; isLeaf?: string}
        defaultExpandAll?: boolean
        defaultExpandedKeys?: any[]
        defaultCheckedKeys?: any[]
    }>()

    const emit = defineEmits<{
        nodeDragStart: [node: any, event: DragEvent]
        nodeDrop: [draggingNode: any, dropNode: any, dropType: string, event: DragEvent]
        nodeClick: [data: any, node: any, el: any, event: MouseEvent]
    }>()

    defineSlots<{
        default?: (scope: {node: any; data: any}) => unknown
        empty?(): unknown
    }>()

    const treeRef = ref<InstanceType<typeof ElTree>>()

    const filteredProps = useFilteredProps(props)

    defineExpose({
        getNode: (data: any) => treeRef.value?.getNode(data),
        remove: (data: any) => treeRef.value?.remove(data),
        append: (data: any, parent: any) => treeRef.value?.append(data, parent),
        getCheckedNodes: (...args: any[]) => (treeRef.value?.getCheckedNodes as any)?.(...args),
        setCheckedKeys: (...args: any[]) => (treeRef.value?.setCheckedKeys as any)?.(...args),
        getCurrentKey: () => treeRef.value?.getCurrentKey(),
        setCurrentKey: (key: any) => treeRef.value?.setCurrentKey(key),
    })
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tree';
</style>
