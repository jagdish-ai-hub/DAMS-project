import { useEffect, useState } from 'react'
import { X, ZoomIn, ZoomOut, RotateCcw, ExternalLink, Download } from 'lucide-react'
import { ghostBtn } from '../shell/ui'

interface Props {
  url: string
  filename: string
  contentType?: string
  onClose: () => void
}

export default function AttachmentLightbox({ url, filename, contentType, onClose }: Props) {
  const [zoom, setZoom] = useState(1)

  const isPdf = Boolean(
    (contentType && contentType.includes('pdf')) ||
    filename.toLowerCase().endsWith('.pdf')
  )

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      role="dialog"
      aria-modal="true"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 9999,
        background: 'rgba(15, 23, 42, 0.85)',
        backdropFilter: 'blur(4px)',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {/* Lightbox Header Bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 20px',
          background: 'rgba(15, 23, 42, 0.95)',
          borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
          color: '#fff',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
          <span
            style={{
              fontWeight: 700,
              fontSize: '0.92rem',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: 400,
            }}
            title={filename}
          >
            {filename}
          </span>
          <span
            style={{
              fontSize: '0.68rem',
              fontWeight: 800,
              padding: '2px 8px',
              borderRadius: 4,
              background: isPdf ? '#B91C1C' : '#1E7F4F',
              color: '#fff',
              textTransform: 'uppercase',
            }}
          >
            {isPdf ? 'PDF' : 'IMAGE'}
          </span>
        </div>

        {/* Action Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {!isPdf && (
            <>
              <button
                type="button"
                onClick={() => setZoom((z) => Math.max(0.5, z - 0.25))}
                title="Zoom Out"
                style={{
                  ...ghostBtn,
                  color: '#fff',
                  border: '1px solid rgba(255, 255, 255, 0.2)',
                  minHeight: 32,
                  padding: '4px 8px',
                }}
              >
                <ZoomOut size={16} />
              </button>
              <button
                type="button"
                onClick={() => setZoom(1)}
                title="Reset Zoom"
                style={{
                  ...ghostBtn,
                  color: '#fff',
                  border: '1px solid rgba(255, 255, 255, 0.2)',
                  minHeight: 32,
                  padding: '4px 8px',
                  fontSize: '0.75rem',
                }}
              >
                <RotateCcw size={14} style={{ marginRight: 4 }} /> {Math.round(zoom * 100)}%
              </button>
              <button
                type="button"
                onClick={() => setZoom((z) => Math.min(3, z + 0.25))}
                title="Zoom In"
                style={{
                  ...ghostBtn,
                  color: '#fff',
                  border: '1px solid rgba(255, 255, 255, 0.2)',
                  minHeight: 32,
                  padding: '4px 8px',
                }}
              >
                <ZoomIn size={16} />
              </button>
            </>
          )}

          <a
            href={url}
            target="_blank"
            rel="noopener noreferrer"
            style={{
              ...ghostBtn,
              color: '#fff',
              border: '1px solid rgba(255, 255, 255, 0.2)',
              minHeight: 32,
              padding: '4px 10px',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontSize: '0.8rem',
              textDecoration: 'none',
            }}
          >
            <ExternalLink size={14} />
            <span className="hidden sm:inline">Open New Tab</span>
          </a>

          <a
            href={url}
            download={filename}
            style={{
              ...ghostBtn,
              color: '#fff',
              border: '1px solid rgba(255, 255, 255, 0.2)',
              minHeight: 32,
              padding: '4px 10px',
              display: 'inline-flex',
              alignItems: 'center',
              gap: 6,
              fontSize: '0.8rem',
              textDecoration: 'none',
            }}
          >
            <Download size={14} />
            <span className="hidden sm:inline">Download</span>
          </a>

          <button
            type="button"
            onClick={onClose}
            style={{
              background: 'transparent',
              border: 'none',
              color: '#fff',
              cursor: 'pointer',
              padding: 6,
              marginLeft: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
            aria-label="Close Preview"
          >
            <X size={22} />
          </button>
        </div>
      </div>

      {/* Lightbox Content Viewer */}
      <div
        style={{
          flex: 1,
          overflow: 'auto',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 20,
        }}
        onClick={(e) => {
          if (e.target === e.currentTarget) onClose()
        }}
      >
        {isPdf ? (
          <iframe
            src={url}
            title={filename}
            style={{
              width: '100%',
              maxWidth: 1000,
              height: '100%',
              maxHeight: '85vh',
              border: 'none',
              borderRadius: 8,
              background: '#fff',
              boxShadow: '0 10px 25px rgba(0,0,0,0.5)',
            }}
          />
        ) : (
          <img
            src={url}
            alt={filename}
            style={{
              maxWidth: '90vw',
              maxHeight: '82vh',
              objectFit: 'contain',
              transform: `scale(${zoom})`,
              transformOrigin: 'center center',
              transition: 'transform 0.15s ease-out',
              borderRadius: 6,
              boxShadow: '0 10px 30px rgba(0,0,0,0.6)',
            }}
          />
        )}
      </div>
    </div>
  )
}
