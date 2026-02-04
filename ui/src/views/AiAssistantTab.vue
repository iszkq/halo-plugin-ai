<template>
  <div class="ai-assistant-page">
    <div class="layout">
      <section class="knowledge-panel">
        <header>
          <h2>知识库</h2>
          <p>在这里维护用于 RAG 检索的知识条目，支持标题 + 长文本内容。</p>
          <button class="btn-primary" @click="startCreate">新增条目</button>
        </header>

        <div class="knowledge-list">
          <div
            v-for="item in knowledge"
            :key="item.id"
            class="knowledge-item"
            @click="editItem(item)"
          >
            <div class="title">{{ item.title || '（无标题）' }}</div>
            <div class="snippet">
              {{ (item.content || '').slice(0, 80) }}{{ (item.content || '').length > 80 ? '…' : '' }}
            </div>
          </div>
          <div v-if="!knowledge.length" class="empty-tip">
            暂无知识条目，请先新增几条再进行问答，以获得更好的效果。
          </div>
        </div>

        <div v-if="editing" class="knowledge-editor">
          <h3>{{ editing.id ? '编辑知识条目' : '新增知识条目' }}</h3>
          <label>
            标题
            <input v-model="editing.title" type="text" placeholder="例如：产品使用说明简介" />
          </label>
          <label>
            内容
            <textarea
              v-model="editing.content"
              rows="6"
              placeholder="详细描述该知识条目的内容，AI 会用这些文本来回答相关问题。"
            ></textarea>
          </label>
          <div class="editor-actions">
            <button class="btn-secondary" @click="cancelEdit">取消</button>
            <button class="btn-danger" v-if="editing.id" @click="handleDelete">删除</button>
            <button class="btn-primary" @click="handleSave">保存</button>
          </div>
        </div>
      </section>

      <section class="chat-panel">
        <header>
          <h2>AI 助手（RAG 问答）</h2>
          <p>基于知识库进行检索增强问答。请先确保左侧知识库中已有内容。</p>
        </header>

        <div class="chat-history" ref="historyRef">
          <div
            v-for="(m, idx) in messages"
            :key="idx"
            class="chat-message"
            :class="m.role === 'user' ? 'user' : 'assistant'"
          >
            <div class="role">
              {{ m.role === 'user' ? '我' : '助手' }}
            </div>
            <div class="content">
              <pre v-if="m.role === 'assistant'">{{ m.content }}</pre>
              <span v-else>{{ m.content }}</span>
            </div>
          </div>
          <div v-if="loading" class="chat-loading">正在思考中，请稍候……</div>
        </div>

        <form class="chat-input" @submit.prevent="handleSend">
          <textarea
            v-model="question"
            rows="3"
            placeholder="请输入你的问题，例如：\n“如何向新用户解释我们产品的核心价值？”"
          ></textarea>
          <div class="actions">
            <button type="button" class="btn-secondary" @click="clearChat">清空对话</button>
            <button type="submit" class="btn-primary" :disabled="loading || !question.trim()">
              {{ loading ? '回答中…' : '发送' }}
            </button>
          </div>
        </form>

        <div v-if="sources.length" class="sources">
          <h3>参考知识条目</h3>
          <ul>
            <li v-for="s in sources" :key="s.id">
              <div class="title">{{ s.title }}</div>
              <div class="snippet">{{ s.snippet }}</div>
            </li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import type { AiMessage, AiSource, KnowledgeItem } from '../api/aiAssistant'
import { chatWithAi, listKnowledge, createKnowledge, updateKnowledge, deleteKnowledge } from '../api/aiAssistant'

const knowledge = ref<KnowledgeItem[]>([])
const editing = ref<KnowledgeItem | null>(null)

const question = ref('')
const loading = ref(false)
const messages = ref<AiMessage[]>([])
const sources = ref<AiSource[]>([])

const historyRef = ref<HTMLElement | null>(null)

async function loadKnowledge() {
  try {
    knowledge.value = await listKnowledge()
  } catch (e: any) {
    console.error('加载知识库失败', e)
  }
}

onMounted(() => {
  loadKnowledge()
})

function startCreate() {
  editing.value = { id: '', title: '', content: '' }
}

function editItem(item: KnowledgeItem) {
  editing.value = { ...item }
}

function cancelEdit() {
  editing.value = null
}

async function handleSave() {
  if (!editing.value) return
  const payload = { title: editing.value.title || '', content: editing.value.content || '' }
  try {
    if (editing.value.id) {
      await updateKnowledge(editing.value.id, payload)
    } else {
      await createKnowledge(payload)
    }
    editing.value = null
    await loadKnowledge()
  } catch (e: any) {
    console.error('保存知识条目失败', e)
  }
}

async function handleDelete() {
  if (!editing.value || !editing.value.id) return
  const id = editing.value.id
  try {
    await deleteKnowledge(id)
    editing.value = null
    await loadKnowledge()
  } catch (e: any) {
    console.error('删除知识条目失败', e)
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (historyRef.value) {
      historyRef.value.scrollTop = historyRef.value.scrollHeight
    }
  })
}

watch(
  () => messages.value.length,
  () => scrollToBottom()
)

async function handleSend() {
  const q = question.value.trim()
  if (!q || loading.value) return
  messages.value.push({ role: 'user', content: q })
  question.value = ''
  loading.value = true
  try {
    const history = messages.value.filter((m) => m.role !== 'assistant' || m.content.trim() !== '')
    const resp = await chatWithAi({
      question: q,
      history,
      topK: 5,
    })
    messages.value.push({ role: 'assistant', content: resp.answer })
    sources.value = resp.sources ?? []
  } catch (e: any) {
    const msg = e?.message || '请求失败，请检查 AI 助手后端是否可用。'
    messages.value.push({
      role: 'assistant',
      content: `调用 AI 助手接口失败：${msg}`,
    })
  } finally {
    loading.value = false
  }
}

function clearChat() {
  messages.value = []
  sources.value = []
}
</script>

<style scoped>
.ai-assistant-page {
  padding: 16px;
}

.layout {
  display: grid;
  grid-template-columns: 1.2fr 2fr;
  gap: 16px;
}

section {
  border-radius: 8px;
  background-color: #ffffff;
  padding: 12px 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

header h2 {
  margin: 0 0 4px;
  font-size: 18px;
}

header p {
  margin: 0 0 8px;
  font-size: 12px;
  color: #6b7280;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.knowledge-panel header {
  align-items: flex-start;
}

.knowledge-list {
  margin-top: 8px;
  max-height: 220px;
  overflow-y: auto;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  padding: 6px;
  background-color: #f9fafb;
}

.knowledge-item {
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
}

.knowledge-item:hover {
  background-color: #e5e7eb;
}

.knowledge-item .title {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
}

.knowledge-item .snippet {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}

.empty-tip {
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  padding: 12px 4px;
}

.knowledge-editor {
  margin-top: 10px;
}

.knowledge-editor h3 {
  margin: 0 0 6px;
  font-size: 14px;
}

.knowledge-editor label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #4b5563;
}

.knowledge-editor input,
.knowledge-editor textarea {
  border-radius: 4px;
  border: 1px solid #d1d5db;
  padding: 6px 8px;
  font-size: 13px;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chat-history {
  margin-top: 8px;
  border-radius: 6px;
  background-color: #f3f4f6;
  padding: 8px;
  height: 260px;
  overflow-y: auto;
}

.chat-message {
  display: flex;
  gap: 6px;
  margin-bottom: 6px;
}

.chat-message .role {
  font-size: 12px;
  font-weight: 600;
  color: #4b5563;
  width: 40px;
}

.chat-message .content {
  flex: 1;
  font-size: 13px;
  color: #111827;
  white-space: pre-wrap;
}

.chat-message.assistant .content pre {
  margin: 0;
  font-family: inherit;
  white-space: pre-wrap;
}

.chat-loading {
  font-size: 12px;
  color: #6b7280;
}

.chat-input {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chat-input textarea {
  border-radius: 4px;
  border: 1px solid #d1d5db;
  padding: 6px 8px;
  font-size: 13px;
}

.chat-input .actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.sources {
  margin-top: 10px;
}

.sources h3 {
  margin: 0 0 4px;
  font-size: 14px;
}

.sources ul {
  list-style: none;
  padding: 0;
  margin: 0;
  font-size: 13px;
}

.sources li + li {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid #e5e7eb;
}

.sources .title {
  font-weight: 600;
}

.sources .snippet {
  color: #4b5563;
}

.btn-primary,
.btn-secondary,
.btn-danger {
  border-radius: 4px;
  border: none;
  padding: 6px 10px;
  font-size: 13px;
  cursor: pointer;
}

.btn-primary {
  background-color: #2563eb;
  color: #ffffff;
}

.btn-secondary {
  background-color: #e5e7eb;
  color: #111827;
}

.btn-danger {
  background-color: #ef4444;
  color: #ffffff;
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>

