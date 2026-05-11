import { create } from 'zustand'

export type ToolInvocationStatus = 'queued' | 'running' | 'success' | 'error'

export interface ToolInvocationRuntimeState {
  toolCallId: string
  toolName: string
  status: ToolInvocationStatus
  args?: unknown
  output?: unknown
  error?: string
  retryCount: number
  updatedAt: number
}

interface ToolInvocationStore {
  items: Record<string, ToolInvocationRuntimeState>
  upsertFromInvocation: (invocation: {
    toolCallId: string
    toolName: string
    state?: string
    args?: unknown
    result?: unknown
  }) => void
  markRetrying: (toolCallId: string) => void
  markError: (toolCallId: string, error: string) => void
  getById: (toolCallId: string) => ToolInvocationRuntimeState | undefined
}

const toStatus = (state?: string): ToolInvocationStatus => {
  const normalized = (state || '').toLowerCase()
  if (normalized === 'result' || normalized === 'success' || normalized === 'done') {
    return 'success'
  }
  if (normalized === 'error' || normalized === 'failed') {
    return 'error'
  }
  if (normalized === 'call' || normalized === 'running' || normalized === 'in_progress') {
    return 'running'
  }
  return 'queued'
}

const useToolInvocationStore = create<ToolInvocationStore>((set, get) => ({
  items: {},
  upsertFromInvocation: (invocation) => {
    set((state) => {
      const prev = state.items[invocation.toolCallId]
      const status = toStatus(invocation.state)
      return {
        items: {
          ...state.items,
          [invocation.toolCallId]: {
            toolCallId: invocation.toolCallId,
            toolName: invocation.toolName,
            status,
            args: invocation.args,
            output: invocation.result,
            error: status === 'error' ? String(invocation.result || prev?.error || '') : undefined,
            retryCount: prev?.retryCount || 0,
            updatedAt: Date.now()
          }
        }
      }
    })
  },
  markRetrying: (toolCallId) => {
    set((state) => {
      const prev = state.items[toolCallId]
      if (!prev) return state
      return {
        items: {
          ...state.items,
          [toolCallId]: {
            ...prev,
            status: 'running',
            error: undefined,
            retryCount: prev.retryCount + 1,
            updatedAt: Date.now()
          }
        }
      }
    })
  },
  markError: (toolCallId, error) => {
    set((state) => {
      const prev = state.items[toolCallId]
      if (!prev) return state
      return {
        items: {
          ...state.items,
          [toolCallId]: {
            ...prev,
            status: 'error',
            error,
            updatedAt: Date.now()
          }
        }
      }
    })
  },
  getById: (toolCallId) => get().items[toolCallId]
}))

export default useToolInvocationStore
