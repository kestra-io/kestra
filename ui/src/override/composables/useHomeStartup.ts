import {onMounted} from "vue"

export function useHomeStartup(loadStats: () => void, haveExecutions: () => void) {
  onMounted(() => {
    loadStats()
    haveExecutions()
  })
}