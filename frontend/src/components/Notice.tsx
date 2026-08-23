export type NoticeTone = 'stop' | 'note'

export interface NoticeState {
  tone: NoticeTone
  message: string
}

interface Props {
  notice: NoticeState | null
  onDismiss: () => void
}

export function Notice({ notice, onDismiss }: Props) {
  if (!notice) return null

  return (
    <div className={`notice notice--${notice.tone}`} role="status">
      <span className="notice__mark" aria-hidden="true" />
      <p className="notice__text">{notice.message}</p>
      <button type="button" className="notice__close" onClick={onDismiss} aria-label="Dismiss">
        ×
      </button>
    </div>
  )
}
