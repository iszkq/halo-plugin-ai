import { definePlugin } from '@halo-dev/console-shared'
import { markRaw } from 'vue'
import AiAssistantTab from './views/AiAssistantTab.vue'

export default definePlugin({
  components: {},
  routes: [],
  extensionPoints: {
    'plugin:self:tabs:create': () => {
      return [
        {
          id: 'ai-assistant',
          label: 'AI 助手',
          component: markRaw(AiAssistantTab),
        },
      ]
    },
  },
})

