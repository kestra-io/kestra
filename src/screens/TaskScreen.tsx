import { TaskList } from '../components/tasks/TaskList'

export function TaskScreen() {
  const tasks = [
    { id: 'hello', type: 'io.kestra.plugin.core.log.Log' },
    { id: 'fail', type: 'io.kestra.plugin.core.execution.Fail', isError: true },
    { id: 'return', type: 'io.kestra.plugin.core.output.OutputValues' },
    { id: 'error_log', type: 'io.kestra.plugin.core.log.Log' }
  ]

  return (
    <TaskList tasks={tasks} />
  )
} 