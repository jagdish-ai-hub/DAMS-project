import { useState } from 'react'

/**
 * Shared clipboard helper for AI copy buttons (chat answers, claim drafts).
 * `copiedKey` holds whichever item confirmed most recently, so lists can show
 * per-row "Copied ✓" feedback with one hook instance.
 */
export function useCopy() {
  const [copiedKey, setCopiedKey] = useState<string | null>(null)
  const [copyError, setCopyError] = useState('')

  async function copy(key: string, text: string) {
    setCopyError('')
    try {
      await navigator.clipboard.writeText(text)
      setCopiedKey(key)
      setTimeout(() => setCopiedKey((cur) => (cur === key ? null : cur)), 1500)
    } catch {
      setCopyError('Copy failed — select the text manually.')
    }
  }

  return { copiedKey, copyError, copy }
}
