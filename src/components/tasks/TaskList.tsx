import { TaskIcon } from './TaskIcon'
import { XStack, YStack, Text } from 'tamagui'

interface Task {
  id: string
  type: string
  isError?: boolean
}

interface TaskListProps {
  tasks: Task[]
}

export function TaskList({ tasks }: TaskListProps) {
  return (
    <YStack space="$2">
      {tasks.map((task) => (
        <XStack 
          key={task.id}
          space="$2" 
          alignItems="center"
          padding="$2"
        >
          <TaskIcon 
            taskType={task.type}
            isError={task.isError}
          />
          <Text>{task.id}</Text>
        </XStack>
      ))}
    </YStack>
  )
} 