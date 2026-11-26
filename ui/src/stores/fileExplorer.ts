import {defineStore} from "pinia"
import {computed, ref, type Ref} from "vue"
import * as Utils from "../utils/utils"
import {useNamespacesStore} from "override/stores/namespaces"
import {makeToast} from "../utils/toast"
import {globalI18n} from "../translations/i18n"
import type {KestraHttpError} from "../utils/kestraHttp"
import type {KsTreeNode} from "@kestra-io/design-system"
import type {FileAttributes} from "@kestra-io/kestra-sdk"

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

export type ElTreeNode = KsTreeNode<TreeNode>

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

function pathSegments(path: string): string[] {
    return path.split("/").filter(Boolean)
}

function isNotRootTreeNode(node: ElTreeNode | {level: 0}): node is ElTreeNode {
    return node.level > 0
}

export const useFileExplorerStore = defineStore("fileExplorer", () => {
    const fileTree: Ref<TreeNode[]> = ref([])
    const searchResults = ref<string[]>([])
    const namespaceId = ref<string>()
    // whether the root level of the tree has been loaded at least once,
    // used to distinguish "not loaded yet" from "loaded and truly empty"
    const rootLoaded = ref(false)
    const loadedDirectories = new Set<string>()

    const namespacesStore = useNamespacesStore()
    const t = (key: string, values?: Record<string, unknown>) => {
        if (!globalI18n.value) {
            return key
        }
        return (values ? globalI18n.value?.t(key, values) : globalI18n.value?.t(key)) ?? key
    }
    const toast = makeToast(t)

    function folderNode(fileName: string, children: TreeNode[]): TreeNodeDirectory {
        return {
            id: Utils.uid(),
            fileName,
            children: children ?? [],
            type: "Directory",
            leaf: false,
        }
    }

    function markDirectoryLoaded(path: string) {
        const prefix = `${path}/`
        for (const loaded of loadedDirectories) {
            if (loaded.startsWith(prefix)) {
                loadedDirectories.delete(loaded)
            }
        }
        loadedDirectories.add(path)
    }

    function mapEntriesToTreeNodes(entries: FileAttributes[]): TreeNode[] {
        return sorted(
            entries
                .filter((item): item is FileAttributes & {fileName: string} => Boolean(item.fileName))
                .map((item): TreeNode => item.type === "Directory"
                    ? folderNode(item.fileName, [])
                    : {
                        ...item,
                        id: Utils.uid(),
                        fileName: item.fileName,
                        extension: getFileNameWithExtension(item.fileName)[1],
                        type: "File",
                        leaf: true,
                    }),
        )
    }

    /**
     * Creates the folder nodes missing along `segments`
     * and returns the deepest one's children, or
     * undefined when a file already holds one of those names.
     */
    function ensureFolderPath(segments: string[], markNewAsLoaded = false): TreeNode[] | undefined {
        let children = fileTree.value
        for (let i = 0; i < segments.length; i++) {
            const segment = segments[i]
            const existing = children.find((item) => item.fileName === segment)
            if (existing && !isDirectory(existing)) {
                return undefined
            }
            const folder = existing ?? folderNode(segment, [])
            if (!existing) {
                children.push(folder)
                sorted(children)
                if (markNewAsLoaded) {
                    loadedDirectories.add(segments.slice(0, i + 1).join("/"))
                }
            }
            folder.children ??= []
            children = folder.children
        }
        return children
    }

    async function resolveSiblingsForCreation(segments: string[]): Promise<TreeNode[] | undefined> {
        let currentChildren = fileTree.value

        for (let i = 0; i < segments.length; i++) {
            const segment = segments[i]
            const existing = currentChildren.find((item) => item.fileName === segment)

            if (!existing) {
                return []
            }
            if (!isDirectory(existing)) {
                return undefined
            }
            const prefix = segments.slice(0, i + 1).join("/")
            if (!loadedDirectories.has(prefix) && namespaceId.value !== undefined) {
                try {
                    const entries = await namespacesStore.readDirectory({
                        namespace: namespaceId.value,
                        path: prefix,
                    })
                    existing.children = mapEntriesToTreeNodes(entries ?? [])
                    markDirectoryLoaded(prefix)
                } catch (e) {
                    if ((e as KestraHttpError)?.status === 404) {
                        existing.children = []
                        markDirectoryLoaded(prefix)
                    } else {
                        throw e
                    }
                }
            }
            existing.children ??= []
            currentChildren = existing.children
        }

        return currentChildren
    }

    async function addFolder(folder: {
        parentPath?: string,
        fileName: string,
        children?: TreeNode[]
    }, creation?: boolean) {
        if(!namespaceId.value) return
        const {fileName, parentPath = ""} = folder
        const segments = [...pathSegments(parentPath), ...pathSegments(fileName)]
        const name = segments.pop()
        if (!name) {
            return
        }
        const path = [...segments, name].join("/")
        if (creation) {
            let siblings: TreeNode[] | undefined
            try {
                siblings = await resolveSiblingsForCreation(segments)
            } catch (error) {
                console.error(`Failed to create folder: ${name}`, error)
                toast.error(t("namespace files.create.folder_error"))
                return
            }
            if (siblings === undefined) {
                toast.error(t("namespace files.create.folder_conflicts_with_file"))
                return
            }
            const conflict = siblings.find(item => item.fileName === name)
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
                toast.success(t("namespace files.create.folder_success", {name}))
            } catch (error) {
                console.error(`Failed to create folder: ${name}`, error)
                toast.error(t("namespace files.create.folder_error"))
                return
            }
        }
        const parent = ensureFolderPath(segments, Boolean(creation))
        if (!parent || parent.find(item => item.fileName === name)) {
            return
        }
        parent.push(folderNode(name, folder?.children ?? []))
        sorted(parent)
        if (creation) {
            loadedDirectories.add(path)
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

    function renderNodes(itemsArr: FileAttributes[]) {
        fileTree.value = []
        
        for (const {type, fileName} of itemsArr) {
            if (!fileName) continue
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
        const segments = [...pathSegments(parentPath ?? ""), ...pathSegments(`${fileName}${extension ? `.${extension}` : ""}`)]
        const NAME = segments.pop()
        if (!NAME) {
            return {}
        }
        const path = [...segments, NAME].join("/")

        const NEW: TreeNodeFile = {
            id: Utils.uid(),
            fileName: NAME,
            extension: getFileNameWithExtension(NAME)[1],
            content,
            type: "File",
            leaf,
        }
        if (creation) {
            let siblings: TreeNode[] | undefined
            try {
                siblings = await resolveSiblingsForCreation(segments)
            } catch (error) {
                console.error(`Failed to create file: ${NAME}`, error)
                toast.error(t("namespace files.create.file_error"))
                return {}
            }
            if (siblings === undefined) {
                toast.error(t("namespace files.create.folder_conflicts_with_file"))
                return {}
            }
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
                toast.success(t("namespace files.create.file_success", {name: NAME}))
            } catch (error) {
                console.error(`Failed to create file: ${NAME}`, error)
                toast.error(t("namespace files.create.file_error"))
                return {}
            }
        }
        const parent = ensureFolderPath(segments, Boolean(creation))
        if (parent && !parent.find(item => item.fileName === NAME)) {
            parent.push(NEW)
            sorted(parent)
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
        reject?: () => void,
    ) {
        if (namespaceId.value === undefined) return
        if (node.level === 0) {
            rootLoaded.value = false
            loadedDirectories.clear()
            const payload = {namespace: namespaceId.value}
            try {
                const rootTreeNodes = await namespacesStore.readDirectory(payload)
                renderNodes(rootTreeNodes)
                fileTree.value = sorted(fileTree.value)
            } catch (e) {
                // Defensive: the backend self-heals the root directory, so a 404 here is not
                // expected, and any non-404 has already been toasted centrally. Swallow either way
                // so this fire-and-forget call cannot raise an unhandled rejection; the finally
                // still releases the spinner (see below). Drop the tree too: `renderNodes` never ran,
                // so anything left in it belongs to the namespace the user was on before.
                fileTree.value = []
                console.error(e)
            } finally {
                // Always resolve and clear the loading flag: a rejected read (e.g. a 404 after the
                // directory was deleted server-side) must not pin the whole file browser on an
                // indefinite spinner (NamespaceFilesEditorView gates the view on `rootLoaded`).
                rootLoaded.value = true
                resolve?.(fileTree.value ?? [])
            }
        } else if (isNotRootTreeNode(node)) {
            const payload = {
                namespace: namespaceId.value,
                path: getPath(node.data.id),
            }
            let entries: FileAttributes[]
            try {
                entries = await namespacesStore.readDirectory(payload)
            } catch (e) {
                // Only a 404 means the folder was deleted server-side: render it empty rather than
                // leaving the el-tree node stuck loading. Anything else still propagates and is toasted
                // centrally, but el-tree clears its own `loading` flag only through the `reject` callback
                // it passes here, and a node left loading is never re-fetched on a later expand.
                if ((e as KestraHttpError)?.status !== 404) {
                    reject?.()
                    throw e
                }
                resolve?.([])
                return
            }
            const children = mapEntriesToTreeNodes(entries)
            const updateChildren = (itemsArr: TreeNode[], path: string, newChildren: TreeNode[]): boolean => {
                for(const item of itemsArr){
                    if(!isDirectory(item)) continue
                    if (getPath(item.id) === path) {
                        item.children = newChildren
                        return true
                    } else if (Array.isArray(item.children)) {
                        if (updateChildren(item.children, path, newChildren)) {
                            return true
                        }
                    }
                }
                return false
            }
            const rootNodePath = getPath(node.data.id)
            if(rootNodePath){
                updateChildren(fileTree.value!, rootNodePath, children)
                markDirectoryLoaded(rootNodePath)
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

    /** The tree is reloaded rather than updated in place: a zip is expanded server-side. */
    async function importFiles(importedFiles: FileList) {
        if(!namespaceId.value) return
        try {
            for (const file of Array.from(importedFiles)) {
                await namespacesStore.importFileDirectory({
                    namespace: namespaceId.value,
                    path: file.webkitRelativePath || file.name,
                    file,
                })
            }
        } finally {
            await loadNodes()
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
  