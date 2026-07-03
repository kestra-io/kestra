import {defineStore} from "pinia"
import {computed, ref} from "vue"
import * as Utils from "../utils/utils"
import {useNamespacesStore} from "override/stores/namespaces"
import {useToast} from "../utils/toast"
import {useI18n} from "vue-i18n"

export interface TreeNodeBase {
    id: string;
    fileName: string;
    leaf: boolean;
}

export interface TreeNodeFile{
    id: string;
    fileName: string;
    type: "File";
    leaf: true;
    content?: string;
    extension?: string;
}

export interface TreeNodeDirectory{
    id: string;
    fileName: string;
    type: "Directory";
    leaf: false;
    children: TreeNode[];
}

export interface ElTreeNode {
    childNodes: ElTreeNode[];
    data: TreeNode;
    level: number;
}

export type TreeNode = TreeNodeFile | TreeNodeDirectory;

export function isDirectory(node: TreeNode): node is TreeNodeDirectory {
    return node.type === "Directory"
}

export function sorted(itemsArr: TreeNode[]) {
    return itemsArr.sort((a, b) => {
        if (a.type === "Directory" && b.type !== "Directory") return -1
        else if (a.type !== "Directory" && b.type === "Directory") return 1
        return a.fileName.localeCompare(b.fileName)
    })
}

export function getFileNameWithExtension(fileNameWithExtension: string): [string, string] {
    const lastDotIdx = fileNameWithExtension.lastIndexOf(".")
    return lastDotIdx !== -1
        ? [
            fileNameWithExtension.slice(0, lastDotIdx),
            fileNameWithExtension.slice(lastDotIdx + 1),
        ]
        : [fileNameWithExtension, ""]
}

function readFile(file: File): Promise<ArrayBuffer> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result as ArrayBuffer)
        reader.onerror = reject
        reader.readAsArrayBuffer(file)
    })
}



function isNotRootTreeNode(node: ElTreeNode | {level: 0}): node is ElTreeNode {
    return node.level > 0
}

export const useFileExplorerStore = defineStore("fileExplorer", () => {
    const fileTree = ref<TreeNode[]>([])
    const searchResults = ref<string[]>([])
    const namespaceId = ref<string>()
    // whether the root level of the tree has been loaded at least once,
    // used to distinguish "not loaded yet" from "loaded and truly empty"
    const rootLoaded = ref(false)

    const namespacesStore = useNamespacesStore()
    const toast = useToast()
    const {t} = useI18n()

    function folderNode(fileName: string, children: TreeNode[]): TreeNodeDirectory {
        return {
            id: Utils.uid(),
            fileName,
            children: children ?? [],
            type: "Directory",
            leaf: false,
        }
    }

    function pushToParentFolder(parentPath: string, newNode: TreeNode) {
        const traverseAndInsert = (basePath = "", array: TreeNode[]) => {
            for (const item of array) {
                const folderPath = `${basePath}${item.fileName}`
                if (folderPath === parentPath && isDirectory(item) && Array.isArray(item.children)) {
                    if (!item.children.find((child) => child.fileName === newNode.fileName)) {
                        item.children.push(newNode)
                        item.children = sorted(item.children)
                    }
                    return true
                } else if (isDirectory(item) && Array.isArray(item.children)) {
                    if (traverseAndInsert(`${folderPath}/`, item.children)) return true
                }
            }
            return false
        }
        traverseAndInsert("", fileTree.value)
    }

    function getSiblingsAtPath(parentPath: string): TreeNode[] {
        if (!parentPath) return fileTree.value
        const findChildren = (basePath = "", array: TreeNode[]): TreeNode[] | undefined => {
            for (const item of array) {
                const folderPath = `${basePath}${item.fileName}`
                if (folderPath === parentPath && isDirectory(item)) return item.children
                if (isDirectory(item)) {
                    const result = findChildren(`${folderPath}/`, item.children)
                    if (result) return result
                }
            }
            return undefined
        }
        return findChildren("", fileTree.value) ?? []
    }

    async function addFolder(folder: {
        parentPath?: string,
        fileName: string,
        children?: TreeNode[]
    }, creation?: boolean) {
        if(!namespaceId.value) return
        const {fileName, parentPath = ""} = folder
        const NEW = folderNode(fileName, folder?.children ?? [])
        const path = parentPath ? `${parentPath}/${fileName}` : fileName
        if (creation) {
            const siblings = getSiblingsAtPath(parentPath)
            const conflict = siblings.find(item => item.fileName === fileName)
            if (conflict) {
                if (isDirectory(conflict)) {
                    toast.error(t("namespace files.create.folder_already_exists"))
                } else {
                    toast.error(t("namespace files.create.folder_conflicts_with_file"))
                }
                return
            }
            try {
                await namespacesStore.createDirectory({namespace: namespaceId.value, path})
                if (!parentPath) {
                    fileTree.value.push(NEW)
                    fileTree.value = sorted(fileTree.value)
                } else {
                    pushToParentFolder(parentPath, NEW)
                }
                toast.success(`Folder "${fileName}" created successfully.`)
            } catch (error) {
                console.error(`Failed to create folder: ${fileName}`, error)
                toast.error(t("namespace files.create.folder_error"))
                return
            }

            return
        }
        if (!parentPath) {
            const firstFolder = NEW.fileName.split("/")[0]
            if (!fileTree.value.find(item => item.fileName === firstFolder)) {
                NEW.fileName = firstFolder
                fileTree.value.push(NEW)
                fileTree.value = sorted(fileTree.value)
            }
        } else {
            pushToParentFolder(parentPath, NEW)
        }
    }

    async function searchFilesList(value: string) {
        if (!value || !namespaceId.value) return
        const results = await namespacesStore.searchFiles({
            namespace: namespaceId.value,
            query: value,
        })
        searchResults.value = results.map((result: string) => result.replace(/^\/*/, ""))
        return searchResults.value
    }

    function renderNodes(itemsArr: TreeNode[]) {
        fileTree.value = []
        
        for (const {type, fileName} of itemsArr) {
            if (type === "Directory") {
                addFolder({fileName})
            } else if (type === "File") {
                const [fileFileName, extension] = getFileNameWithExtension(fileName)
                addFile({
                    fileName: fileFileName,
                    extension, 
                    leaf: true,
                })
            }
        }
    }

    async function addFile(file: Omit<TreeNodeFile, "id" | "type">, parentPath?: string, creation: boolean = false): Promise<{ path?: string; file?: TreeNodeFile; }> {
        if(!namespaceId.value) return {}
        const {fileName, extension, content = "", leaf} = file
        const NAME = `${fileName}${extension ? `.${extension}` : ""}`

        const NEW: TreeNodeFile = {
            id: Utils.uid(),
            fileName: NAME,
            extension,
            content,
            type: "File",
            leaf,
        }
        const path = `${parentPath ? `${parentPath}/` : ""}${NAME}`
        if (creation) {
            const siblings = getSiblingsAtPath(parentPath ?? "")
            const conflict = siblings.find(item => item.fileName === NAME)
            if (conflict) {
                if (!isDirectory(conflict)) {
                    toast.error(t("namespace files.create.file_already_exists"))
                } else {
                    toast.error(t("namespace files.create.file_conflicts_with_folder"))
                }
                return {}
            }
            try {
                await namespacesStore.saveOrCreateFile({
                    namespace: namespaceId.value,
                    path,
                    content,
                })
                toast.success(`File "${NAME}" created successfully.`)
            } catch (error) {
                console.error(`Failed to create file: ${NAME}`, error)
                toast.error(t("namespace files.create.file_error"))
                return {}
            }
        }
        if (!parentPath) {
            fileTree.value.push(NEW)
            fileTree.value = sorted(fileTree.value)
        } else {
            pushToParentFolder(parentPath, NEW)
        }
        return {path, file: NEW}
    }

    function getPath(uid: string ) {
        // first, use the node unique id to find it in all the subtrees of the fileTree
        const findPath = (array: TreeNode[], currentPath = ""): string | undefined => {
            if (!Array.isArray(array)) return undefined
            for (const item of array) {
                const newPath = currentPath ? `${currentPath}/${item.fileName}` : item.fileName
                if (item.id === uid) {
                    return newPath
                }
                if (isDirectory(item)) {
                    const result = findPath(item.children, newPath)
                    if (result) {
                        return result
                    }
                }
            }
            return undefined
        }
        return findPath(fileTree.value)
    }

    async function loadNodes(
        node: ElTreeNode | {level: 0} = {level: 0},
        resolve?: (children: TreeNode[]) => void,
    ) {
        if (namespaceId.value === undefined) return
        if (node.level === 0) {
            rootLoaded.value = false
            const payload = {namespace: namespaceId.value}
            const rootTreeNodes = await namespacesStore.readDirectory<TreeNode>(payload)
            renderNodes(rootTreeNodes)
            fileTree.value = sorted(fileTree.value)
            rootLoaded.value = true
            resolve?.(fileTree.value)
        } else if (isNotRootTreeNode(node)) {
            const payload = {
                namespace: namespaceId.value, 
                path: getPath(node.data.id),
            }
            let children = await namespacesStore.readDirectory<TreeNode>(payload)
            children = sorted(
                children.map((item) => ({
                    ...item,
                    id: Utils.uid(),
                    leaf: item.type === "File",
                } as TreeNode)),
            )
            const updateChildren = (itemsArr: TreeNode[], path: string, newChildren: TreeNode[]) => {
                for(const item of itemsArr){
                    if(!isDirectory(item)) return
                    if (getPath(item.id) === path) {
                        item.children = newChildren
                    } else if (Array.isArray(item.children)) {
                        updateChildren(item.children, path, newChildren)
                    }
                }
            }
            const rootNodePath = getPath(node.data.id)
            if(rootNodePath){
                updateChildren(fileTree.value!, rootNodePath, children)
            } 
            resolve?.(children)
        }
    }

    function extractPaths(basePath = "", array: TreeNode[] = []) {
        const paths: string[] = []
        array?.forEach((item) => {
            if (isDirectory(item)) {
                const folderPath = `${basePath}${item.fileName}`
                paths.push(folderPath)
                paths.push(...extractPaths(`${folderPath}/`, item.children ?? []))
            }
        })
        return paths
    }

    const folders = computed(() => extractPaths(undefined, fileTree.value))

    // true only once the root has been loaded and no file/folder exists,
    // so the dedicated empty state can replace the file browser entirely
    const isEmpty = computed(() => rootLoaded.value && fileTree.value.length === 0)

    function findNodeByPath(path: string, itemsArr: TreeNode[] = fileTree.value, parentPath = ""): TreeNode | null {
        for (const item of itemsArr) {
            const fullPath = `${parentPath}${item.fileName}`
            if (fullPath === path) {
                return item
            }
            if (isDirectory(item) && item.children && item.children.length > 0) {
                const foundNode = findNodeByPath(path, item.children, `${fullPath}/`)
                if (foundNode) {
                    return foundNode
                }
            }
        }
        return null
    }

    async function importFiles(importedFiles: FileList) {
        if(!namespaceId.value) return
        for (const file of Array.from(importedFiles)) {
            if ((file as any).webkitRelativePath) {
                const filePath: string = (file as any).webkitRelativePath
                const pathParts = filePath.split("/")
                let currentFolder: TreeNode[] | undefined = fileTree.value
                const folderPath: string[] = []
                for (let i = 0; i < pathParts.length - 1; i++) {
                    const folderName = pathParts[i]
                    folderPath.push(folderName)
                    if(!currentFolder) continue
                    const folderIndex = currentFolder.findIndex(
                        (item: any) => typeof item === "object" && item.fileName === folderName,
                    )
                    if (folderIndex === -1) {
                        const newFolder: TreeNodeDirectory = {
                            id: Utils.uid(),
                            fileName: folderName,
                            children: [],
                            type: "Directory",
                            leaf: false,
                        }
                        currentFolder.push(newFolder)
                        sorted(currentFolder)
                        currentFolder = newFolder.children
                    } else {
                        currentFolder = (currentFolder[folderIndex] as TreeNodeDirectory).children
                    }
                }
                const fileName = pathParts[pathParts.length - 1]
                const [name, extension] = getFileNameWithExtension(fileName)
                const content = await readFile(file)
                namespacesStore.importFileDirectory({
                    namespace: namespaceId.value,
                    content,
                    path: `${folderPath}/${fileName}`,
                })
                currentFolder?.push({
                    id: Utils.uid(),
                    fileName: `${name}${extension ? `.${extension}` : ""}`,
                    extension,
                    type: "File",
                    leaf: true,
                })
            } else {
                const content = await readFile(file)
                const [name, extension] = getFileNameWithExtension(file.name)
                namespacesStore.importFileDirectory({
                    namespace: namespaceId.value,
                    content,
                    path: file.name,
                })
                
                fileTree.value.push({
                    id: Utils.uid(),
                    fileName: `${name}${extension ? `.${extension}` : ""}`,
                    extension,
                    type: "File",
                    leaf: true,
                })
            }
        }
    }

    return {
        addFolder,
        addFile,
        searchFilesList,
        loadNodes,
        findNodeByPath,
        importFiles,
        getPath,
        fileTree,
        folders,
        isEmpty,
        rootLoaded,
        namespaceId,
        searchResults,
    }
})
  