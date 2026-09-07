import { useEffect, useState, useCallback, useRef } from 'react'

interface DraftRecord<T> {
  savedAt: string
  payload: T
}

export function useDraftRecovery<T>(key: string, currentData: T, isDirty: boolean) {
  const [savedDraft, setSavedDraft] = useState<DraftRecord<T> | null>(null)
  const isDirtyRef = useRef(isDirty)
  isDirtyRef.current = isDirty

  // Check for existing draft on mount
  useEffect(() => {
    try {
      const raw = localStorage.getItem(key)
      if (raw) {
        const parsed = JSON.parse(raw) as DraftRecord<T>
        // Only consider drafts saved in the last 48 hours
        const age = Date.now() - new Date(parsed.savedAt).getTime()
        if (age < 48 * 60 * 60 * 1000) {
          setSavedDraft(parsed)
        } else {
          localStorage.removeItem(key)
        }
      }
    } catch {
      localStorage.removeItem(key)
    }
  }, [key])

  // Auto-save active draft when dirty
  useEffect(() => {
    if (!isDirtyRef.current) return
    const timer = setTimeout(() => {
      try {
        const record: DraftRecord<T> = {
          savedAt: new Date().toISOString(),
          payload: currentData,
        }
        localStorage.setItem(key, JSON.stringify(record))
      } catch (err) {
        console.warn('Could not cache draft to localStorage:', err)
      }
    }, 800)

    return () => clearTimeout(timer)
  }, [key, currentData])

  const discardDraft = useCallback(() => {
    try {
      localStorage.removeItem(key)
    } catch {}
    setSavedDraft(null)
  }, [key])

  const clearDraft = useCallback(() => {
    try {
      localStorage.removeItem(key)
    } catch {}
    setSavedDraft(null)
  }, [key])

  const restoreDraft = useCallback((): T | null => {
    const data = savedDraft?.payload ?? null
    setSavedDraft(null)
    return data
  }, [savedDraft])

  return {
    savedDraft,
    hasDraft: savedDraft != null,
    draftTimestamp: savedDraft ? new Date(savedDraft.savedAt) : null,
    restoreDraft,
    discardDraft,
    clearDraft,
  }
}
