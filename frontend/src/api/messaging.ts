import api from './axios'

export interface MessageTemplate {
  id: number
  code: string
  channel: string
  body: string
  active: boolean
}

export interface MessageLogEntry {
  id: number
  channel: string
  toPhone: string
  templateCode: string | null
  body: string
  status: string
  relatedType: string | null
  relatedId: number | null
  error: string | null
  createdAt: string
}

export const messagesApi = {
  templates() {
    return api.get<MessageTemplate[]>('/api/v1/messages/templates')
  },
  log() {
    return api.get<MessageLogEntry[]>('/api/v1/messages/log')
  },
  send(data: {
    templateCode: string
    toPhone: string
    variables?: Record<string, string>
    relatedType?: string
    relatedId?: number
  }) {
    return api.post<MessageLogEntry>('/api/v1/messages/send', data)
  },
}
