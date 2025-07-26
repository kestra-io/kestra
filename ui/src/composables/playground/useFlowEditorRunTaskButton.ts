import {computed, nextTick, ref, Ref, watch} from "vue";
import * as FlowYamlUtils from "@kestra-io/ui-libs/flow-yaml-utils";
import {usePlaygroundStore} from "../../stores/playground";

export default function useFlowEditorRunTaskButton(isCurrentTabFlow: Ref<boolean>, editorRefElement: Ref<any>, source: Ref<string>) {
    const taskLineMap = computed(() => {
        return isCurrentTabFlow.value ? FlowYamlUtils.getTasksLines(source.value) : {}
    })

    const playgroundStore = usePlaygroundStore()

    const highlightedLines = ref<{
        taskId: string,
        start: number,
        end: number
    }>();

    const showRunTaskButton = ref<boolean>(false);
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

        return {taskId, start, end, longestLineLength, firstLineLength: taskCodeLines[0].length};
    })

    function highlightLines(range?: {start: number, end: number}) {
        if(!range) {
            editorRefElement.value?.clearHighlights();
            return;
        }

        editorRefElement.value?.highlightLinesRange(range);
    }

    function addButtonToHoveredTask(taskCode?: {taskId: string, start: number, end: number, longestLineLength:number, firstLineLength: number}) {
        if(highlightedLines.value && highlightedLines.value.taskId !== taskCode?.taskId) {
            editorRefElement.value?.removeContentWidget(`task-hovered-${highlightedLines.value.taskId}`);
        }

        if(!taskCode) {
            showRunTaskButton.value = false;
            return
        }

        if(highlightedLines.value && highlightedLines.value.taskId === taskCode.taskId) {
            return;
        }

        // now the size of this longest line determines where
        // we will want to add the editor content widget
        editorRefElement.value?.addContentWidget({
            id: `task-hovered-${taskCode.taskId}`,
            position: {
                lineNumber: taskCode.start,
                column: taskCode.longestLineLength + 1
            },
            height: (taskCode.end - taskCode.start) + 1,
            marginLeft: (taskCode.longestLineLength - taskCode.firstLineLength),
        });

        nextTick(() => {
            showRunTaskButton.value = true;
        });
    }

    watch(hoveredTaskProperties, (res) => {
        if(!res || !playgroundStore.enabled || !isCurrentTabFlow.value) {
            highlightedLines.value = undefined;
            showRunTaskButton.value = false;
            editorRefElement.value?.clearHighlights();
            return;
        }

        // in case identical setting change nothing
        if(highlightedLines.value
            && highlightedLines.value.start === res.start
            && highlightedLines.value.end === res.end) {
            return;
        }

        highlightLines(res)
        addButtonToHoveredTask(res);

        highlightedLines.value = res;
    }, {deep: true});


    function highlightHoveredTask(lineNumber?:number){
        if(!playgroundStore.enabled || !playgroundStore.enabled || !isCurrentTabFlow.value){
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
        showRunTaskButton,
    }
}