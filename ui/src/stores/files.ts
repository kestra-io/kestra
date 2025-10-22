import {defineStore} from "pinia";
import {ref} from "vue";

export interface TreeNodeFile{
    id: string;
    fileName: string;
    type: "File";
    leaf: true;
    extension?: string;
    data?: any;
    content?: ArrayBuffer;
}

export interface TreeNodeDirectory{
    id: string;
    fileName: string;
    type: "Directory";
    data?: any;
    leaf: false;
    children: TreeNode[];
}

export type TreeNode = TreeNodeFile | TreeNodeDirectory;

export function isDirectory(node: TreeNode): node is TreeNodeDirectory {
    return node.type === "Directory";
}

export const useFilesStore = defineStore("files", () => {
    const fileTree = ref<TreeNode[]>([]);
    return {
        fileTree
    };
})
  