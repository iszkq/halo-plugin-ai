import axios from 'axios'

export interface AiSource {
  id: string
  title: string
  snippet: string
}

export interface AiMessage {
  role: 'system' | 'user' | 'assistant'
  content: string
}

export interface AiChatRequest {
  question: string
  history?: AiMessage[]
  topK?: number
}

export interface AiChatResponse {
  answer: string
  sources: AiSource[]
}

export interface KnowledgeItem {
  id: string
  title: string
  content: string
}

const PLUGIN_API_PREFIX = '/apis/api.plugin.halo.run/v1alpha1/plugins/ai-assistant/assistant'

export async function chatWithAi(request: AiChatRequest): Promise<AiChatResponse> {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  const url = origin + PLUGIN_API_PREFIX + '/chat'
  const res = await axios.post<AiChatResponse>(url, request)
  return res.data
}

export async function listKnowledge(): Promise<KnowledgeItem[]> {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  const url = origin + PLUGIN_API_PREFIX + '/knowledge'
  const res = await axios.get<KnowledgeItem[]>(url)
  return res.data
}

export async function createKnowledge(payload: { title: string; content: string }): Promise<KnowledgeItem> {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  const url = origin + PLUGIN_API_PREFIX + '/knowledge'
  const res = await axios.post<KnowledgeItem>(url, payload)
  return res.data
}

export async function updateKnowledge(id: string, payload: { title: string; content: string }): Promise<KnowledgeItem> {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  const url = origin + PLUGIN_API_PREFIX + `/knowledge/${encodeURIComponent(id)}`
  const res = await axios.put<KnowledgeItem>(url, payload)
  return res.data
}

export async function deleteKnowledge(id: string): Promise<void> {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  const url = origin + PLUGIN_API_PREFIX + `/knowledge/${encodeURIComponent(id)}`
  await axios.delete(url)
}

