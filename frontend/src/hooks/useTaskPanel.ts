import { useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

/** The open task lives in the URL (?task=<id>) so it is linkable and survives refresh. */
export function useTaskPanel() {
  const [params, setParams] = useSearchParams()
  const taskId = params.get('task')
  const open = useCallback(
    (id: string) =>
      setParams((p) => {
        const n = new URLSearchParams(p)
        n.set('task', id)
        return n
      }),
    [setParams],
  )
  const close = useCallback(
    () =>
      setParams((p) => {
        const n = new URLSearchParams(p)
        n.delete('task')
        return n
      }),
    [setParams],
  )
  return { taskId, open, close }
}
