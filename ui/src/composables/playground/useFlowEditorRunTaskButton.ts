import {computed, ref, Ref, watch} from "vue";
import * as FlowYamlUtils from "@kestra-io/ui-libs/flow-yaml-utils";
import {usePlaygroundStore} from "../../stores/playground";
import Editor from "../../components/inputs/Editor.vue";

export default function useFlowEditorRunTaskButton(isCurrentTabFlow: Ref<boolean>, editorRefElement: Ref<InstanceType<typeof Editor> | undefined>, source: Ref<string>) {
    const taskLineMap = computed(() => {
        return isCurrentTabFlow.value ? FlowYamlUtils.getTasksLines(source.value) : {}
    })

    const playgroundStore = usePlaygroundStore()

    const highlightedLines = ref<{
        taskId: string,
        start: number,
        end: number,
        longestLineLength: number,
        firstLineLength: number
    }>();

    const ln = ref<number>(-1);

    const hoveredTaskProperties = computed(() => {
        const lineNumber = ln.value
        const hoveredTaskIds = Object.keys(taskLineMap.value).filter(taskId => {
            const {start, end} = taskLineMap.value[taskId];
            return start <= lineNumber && end >= lineNumber;
        }).sort((aId, bId) => {
            const a = taskLineMap.value[aId];
            const b = taskLineMap.value[bId];
            // make the longest distance between start and end appear last
            return (a.end - a.start) - (b.end - b.start);
        })

        // take the shortest task that matches the line number
        // in case of task nesting
        const taskId = hoveredTaskIds[0];

        if(!taskId) {
            return undefined;
        }

        const {start, end} = taskLineMap.value[taskId]

        // get this hovered tasks code, find the longest line
        const taskCodeLines = source.value.split("\n").slice(start - 1, end);
        const longestLineLength = taskCodeLines.reduce((longest, current) => {
            return Math.max(longest, current.length);
        }, 0);

        return {
            taskId,
            start,
            end,
            longestLineLength,
            firstLineLength: taskCodeLines[0].length
        };
    })

    function highlightLines(range?: {start: number, end: number}) {
        if(!range) {
            editorRefElement.value?.clearLinesRangeHighlights();
            return;
        }

        editorRefElement.value?.highlightLinesRange(range);
    }

    function addButtonToHoveredTask(taskCode?: {taskId: string, start: number, end: number, longestLineLength:number, firstLineLength: number}) {
        if(!taskCode || playgroundStore.dropdownOpened) {
            return
        }

        editorRefElement.value?.removeContentWidget(`task-hovered-${taskCode.taskId}`);

        // now the size of this longest line determines where
        // we will want to add the editor content widget
        editorRefElement.value?.addContentWidget({
            id: `task-hovered-${taskCode.taskId}`,
            position: {
                lineNumber: taskCode.start,
                column: 0
            },
            height: Math.max(taskCode.end - taskCode.start + 1, 1),
            right: "1rem",
        });
    }

    const highlightedTaskId = ref<string | undefined>(undefined);

    watch(hoveredTaskProperties, (res) => {
        if (playgroundStore.dropdownOpened) {
            return;
        }

        if(!res || !playgroundStore.enabled || !isCurrentTabFlow.value) {
            highlightedLines.value = undefined;
            editorRefElement.value?.clearLinesRangeHighlights();
            editorRefElement.value?.removeContentWidget(`task-hovered-${highlightedTaskId.value}`);
            highlightedTaskId.value = undefined;
            return;
        }

        const hv = highlightedLines.value as Record<string, any> | undefined;

        // in case identical setting change nothing
        if(hv && !Object.keys(hv).some((key) => hv[key] !== (res as Record<string, any>)[key])) {
            return;
        }

        highlightedTaskId.value = res.taskId;

        highlightLines(res)
        addButtonToHoveredTask(res);

        highlightedLines.value = res;
    }, {deep: true});


    function highlightHoveredTask(lineNumber?:number){
        if(!playgroundStore.enabled || !isCurrentTabFlow.value){
            ln.value = -1;
            return;
        }
        if(lineNumber === undefined) return
        ln.value = lineNumber;
    }

    return {
        highlightHoveredTask,
        playgroundStore,
        highlightedLines,
    }
}
