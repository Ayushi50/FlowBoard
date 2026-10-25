import { useCallback, useSyncExternalStore } from 'react'

type Theme = 'light' | 'dark'
const KEY = 'flowboard.theme'
const listeners = new Set<() => void>()

function current(): Theme {
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light'
}

export function useTheme() {
  const theme = useSyncExternalStore(
    (cb) => {
      listeners.add(cb)
      return () => listeners.delete(cb)
    },
    current,
    () => 'light' as Theme,
  )
  const setTheme = useCallback((t: Theme) => {
    document.documentElement.classList.toggle('dark', t === 'dark')
    try {
      localStorage.setItem(KEY, t)
    } catch {
      /* ignore */
    }
    listeners.forEach((l) => l())
  }, [])
  return { theme, setTheme, toggle: () => setTheme(theme === 'dark' ? 'light' : 'dark') }
}
