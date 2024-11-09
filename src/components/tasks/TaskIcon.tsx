import React from 'react';
// Import necessary Tamagui components
import { YStack } from 'tamagui'
import { ErrorIcon, LogIcon, OutputIcon, DefaultTaskIcon } from '../icons'

interface TaskIconProps {
  taskType: string
  isError?: boolean
  size?: number
}

export function TaskIcon({ 
  taskType, 
  isError,
  size = 20 
}: TaskIconProps) {
  // Helper function to determine icon based on task type
  function getIconForTaskType(type: string) {
    const iconProps = {
      size,
      'data-testid': 'default-icon',
      color: isError ? 'var(--kestra-error-color, #ff41b9)' : 'var(--bs-gray-600)'
    }

    // Handle error-related tasks
    if (type.includes('error') || type.includes('fail') || isError) {
      return <ErrorIcon {...iconProps} data-testid="error-icon" />
    }
    
    // Handle log tasks
    if (type.includes('log')) {
      return <LogIcon {...iconProps} data-testid="log-icon" />
    }
    
    // Handle output tasks
    if (type.includes('output')) {
      return <OutputIcon {...iconProps} data-testid="output-icon" />
    }
    
    return <DefaultTaskIcon {...iconProps} />
  }

  return (
    <YStack 
      data-testid="task-icon-wrapper"
      style={{
        width: size + 16,
        padding: 6,
        borderRadius: 'var(--bs-border-radius-lg)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center'
      }}
    >
      {getIconForTaskType(taskType)}
    </YStack>
  )
} 