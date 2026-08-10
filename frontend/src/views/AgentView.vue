<template>
  <main class="agent-center page" :class="{ 'is-workspace': activeTab !== 'overview', 'is-chat-workspace': activeTab === 'chats' }">
    <div class="container">
      <p v-if="loadNotice" class="agent-load-notice">{{ loadNotice }}</p>

      <div v-if="actionNotice" class="agent-action-notice" :class="`is-${actionNotice.type}`" role="status">{{ actionNotice.message }}</div>
      <div v-if="actionNotice" class="agent-action-notice" :class="`is-${actionNotice.type}`" role="status">{{ actionNotice.message }}</div>
      <nav class="agent-tabs">
        <button v-for="tab in tabs" :key="tab.id" :class="{ active: activeTab === tab.id }" @click="selectTab(tab.id)">{{ tab.label }}</button>
      </nav>

      <section v-if="activeTab === 'overview'" class="agent-dashboard">
        <div v-if="pageLoading" class="agent-page-loading card" aria-live="polite">
          <span class="loading-spinner" />正在连接 Agent 服务并加载你的阅读上下文……
        </div>
        <template v-else>
          <header class="dashboard-head">
            <div>
              <span class="agent-eyebrow">阅见智能阅读</span>
              <h1>从你读到的地方，继续读下去。</h1>
              <p>对话、书籍洞察与知识图谱都围绕你的书架和已读进度工作。</p>
            </div>
            <button class="dashboard-primary-action" type="button" @click="selectTab('chats')">开始对话 <b>↗</b></button>
          </header>

          <div class="dashboard-metrics">
            <article class="dashboard-credit">
              <span>可用积分</span>
              <strong>{{ credits?.availableCredits ?? '--' }}</strong>
              <small>使用平台模型时按调用结算；自配模型不消耗平台积分。</small>
              <button type="button" @click="selectTab('models')">管理模型与积分 →</button>
            </article>
            <article class="dashboard-status">
              <span class="dashboard-label">服务状态</span>
              <strong><i :class="statusDot(infrastructure)" />{{ infrastructure ? '阅读智能体在线' : '正在确认服务状态' }}</strong>
              <p>{{ infrastructure?.retrievalArchitecture || 'LightRAG 多路检索' }}</p>
              <div class="status-tags">
                <span>{{ infrastructure?.milvusEnabled ? '向量检索已启用' : '本地向量检索' }}</span>
                <span>{{ infrastructure?.neo4jEnabled ? '知识图谱已启用' : '本地知识图谱' }}</span>
              </div>
            </article>
            <article class="dashboard-task">
              <template v-if="buildTasks.length">
                <span class="dashboard-label">最近知识图谱任务</span>
                <strong>{{ buildTasks[0].status === 'COMPLETED' ? '最近任务已完成' : buildTasks[0].status === 'FAILED' ? '最近任务需要处理' : '正在构建知识图谱' }}</strong>
                <p>{{ buildTasks[0].message || buildTasks[0].errorMessage || '等待任务状态更新' }}</p>
                <div class="task-progress"><i :style="{ width: `${Math.min(100, Math.round((buildTasks[0].completedChapters || 0) / Math.max(1, buildTasks[0].totalChapters || 1) * 100))}%` }" /></div>
                <button type="button" @click="selectTab('tasks')">查看任务进度 →</button>
              </template>
              <template v-else>
                <span class="dashboard-label">知识图谱</span>
                <strong>让故事脉络变得可读</strong>
                <p>从书架选择作品，构建基于已读内容的 LightRAG 知识图谱。</p>
                <button type="button" @click="selectTab('insights')">打开书籍洞察 →</button>
              </template>
            </article>
          </div>

          <section class="dashboard-shortcuts" aria-label="常用功能">
            <button type="button" @click="selectTab('chats')"><span>01</span><b>阅读对话</b><small>引用书架作品，在无剧透边界内继续讨论。</small><em>→</em></button>
            <button type="button" @click="selectTab('insights')"><span>02</span><b>书籍洞察</b><small>回顾剧情、查看人物关系与追踪伏笔。</small><em>→</em></button>
            <button type="button" @click="selectTab('tasks')"><span>03</span><b>构建任务</b><small>查看知识图谱的构建进度和结果。</small><em>→</em></button>
          </section>
        </template>
      </section>

      <section v-else-if="activeTab === 'chats'" class="agent-workbench">
        <aside class="session-list card">
          <div class="session-list-head"><span>会话档案</span><button class="new-session" @click="newSession">新建</button></div>
          <label class="session-search-wrap"><span>搜索</span><input v-model="sessionSearch" class="session-search" placeholder="检索你的对话" @input="searchSessions" /></label>
          <div class="session-scroll">
            <p v-if="!sessions.length" class="session-empty">还没有对话。新建一个会话，从正在读的书开始。</p>
            <button v-for="session in sessions" :key="session.id" :class="{ selected: session.id === activeSession?.id }" @click="selectSession(session)"><span class="session-bullet" />{{ session.title || '未命名对话' }}</button>
          </div>
          <div class="session-list-foot"><span>{{ sessions.length }} 段已保存对话</span><span>私密存储</span></div>
        </aside>
        <section class="chat-pane card">
          <template v-if="activeSession">
            <div class="chat-header"><div class="chat-title-block"><span class="agent-eyebrow">阅读对话</span><div class="conversation-title-row"><input v-if="editingSessionTitle" ref="sessionTitleInput" v-model="sessionTitleDraft" class="conversation-title-input" maxlength="80" @keydown.enter.prevent="saveSessionTitle" @keydown.esc.prevent="cancelSessionTitle" @blur="saveSessionTitle" /><strong v-else>{{ activeSession.title || '未命名对话' }}</strong><button class="conversation-title-edit" type="button" :title="editingSessionTitle ? '保存标题' : '重命名对话'" @mousedown.prevent @click="editingSessionTitle ? saveSessionTitle() : startSessionTitleEdit()">{{ editingSessionTitle ? '保存' : '编辑标题' }}</button></div><small>{{ chatReferenceBook ? `围绕《${chatReferenceBook.bookName}》展开` : '尚未绑定书籍，你也可以随时开始闲聊' }}</small></div><div class="chat-actions"><span v-if="insightBookId" class="context-chip">安全边界 · 第 {{ insightChapter }} 章</span><button class="icon-action" title="导出对话" @click="exportConversation">导出</button><button class="icon-action danger" title="删除对话" @click="deleteConversation">删除</button></div></div>
            <div class="chat-history">
              <div v-if="!messages.length && !sending" class="chat-welcome"><span class="welcome-mark">阅</span><div><span class="agent-eyebrow">小说阅读，不只是提问</span><h2>从你读到的地方继续。</h2><p>把书架、人物关系和已读章节都交给我。每个可核验回答都会带上它来自哪一章。</p><div class="starter-prompts"><button v-for="prompt in starterPrompts" :key="prompt" @click="useStarterPrompt(prompt)">{{ prompt }} <b>→</b></button></div></div></div>
              <article v-for="message in messages" :key="message.id" :class="['center-message', message.role === 'USER' ? 'user' : 'assistant']">
                <template v-if="message.role === 'USER' && editingMessageId === message.id">
                  <textarea v-model="editingMessageContent" class="message-editor" rows="3" @keydown.esc.prevent="cancelMessageEdit" @keydown.ctrl.enter.prevent="saveMessageEdit(message)" />
                  <div class="message-edit-actions"><button type="button" @click="cancelMessageEdit">取消</button><button type="button" :disabled="sending || !editingMessageContent.trim()" @click="saveMessageEdit(message)">保存并重新生成</button></div>
                </template>
                <template v-else>
                  <span>{{ message.content }}</span>
                  <button v-if="message.role === 'USER' && !String(message.id).startsWith('local-')" class="message-edit-button" type="button" title="编辑这条提问并重新生成后续回答" @click="startMessageEdit(message)"><span>✎</span> 编辑并重新生成</button>
                </template>
                <small v-if="message.role === 'ASSISTANT' && isInterviewResponse(message.content)" class="interview-contract">角色访谈：原文事实、推断与未知内容已分区。</small>
                <div v-if="citationItems(message).length" class="citation-list"><button v-for="citation in citationItems(message)" :key="`${citation.canonicalBookId}-${citation.chapterIndex}-${citation.excerpt}`" class="citation-link" @click="openCitation(citation)">引用：第 {{ citation.chapterIndex + 1 }} 章 · {{ citation.excerpt }}</button></div>
              </article>
            </div>
            <form class="center-input" @submit.prevent="send">
              <div v-if="insightBookId" class="context-bar"><span class="context-orbit" />本次回答限定在 <b>{{ chatReferenceBook?.bookName || `作品 #${insightBookId}` }}</b> 的第 {{ insightChapter }} 章以内 <button type="button" @click="clearReadingContext">清除</button></div>
              <div class="chat-tools" aria-label="对话快捷插件">
                <button type="button" class="chat-tool primary-tool" :class="{ active: showChatPlugins }" @click="showChatPlugins = !showChatPlugins">＋ 引用书籍</button>
                <span class="chat-tool protected">已读范围保护</span>
                <button type="button" class="chat-tool" @click="applyCurrentReadingContext">同步当前阅读</button>
                <span v-if="chatReferenceBook" class="chat-reference">《{{ chatReferenceBook.bookName }}》</span>
              </div>
              <div v-if="showChatPlugins" class="chat-plugin-panel">
                <label>书架引用<select v-model="chatReferenceBookId" @change="selectChatBook"><option value="">请选择一本书</option><option v-for="book in usableShelfBooks" :key="book.canonicalBookId" :value="String(book.canonicalBookId)">{{ book.bookName }}<template v-if="book.author"> · {{ book.author }}</template></option></select></label>
                <div class="plugin-divider" />
                <button type="button" class="plugin-action" @click="useStarterPrompt('分析这本书最近的剧情和人物关系')">人物关系</button>
                <button type="button" class="plugin-action" @click="useStarterPrompt('帮我找出这本书目前已读范围内的伏笔')">伏笔雷达</button>
                <button type="button" class="plugin-action" @click="useStarterPrompt('请以当前角色的第一人称接受一次访谈')">角色访谈</button>
                <button type="button" class="plugin-action" @click="useStarterPrompt('推荐几本和这本书气质相近的作品')">相似作品</button>
              </div>
              <textarea v-model="draft" rows="3" placeholder="写下你的问题，或从书架中引用一部作品…" @keydown.enter.exact.prevent="send" />
              <div class="chat-model-select" :class="{ open: showModelPicker }">
                <button class="model-picker-trigger" type="button" @click="showModelPicker = !showModelPicker"><span class="model-picker-signal" /><span class="model-picker-copy"><small>本次对话模型</small><strong>{{ selectedChatModelLabel }}</strong></span><b>⌄</b></button>
                <div v-if="showModelPicker" class="chat-model-menu">
                  <p>选择本次回答使用的模型</p>
                  <button type="button" :class="{ selected: !selectedModelConfigId }" @click="selectedModelConfigId = ''; showModelPicker = false"><i /><span><b>平台试用模型</b><small>按调用消耗平台积分</small></span><em>平台</em></button>
                  <button v-for="model in enabledModels" :key="model.id" type="button" :class="{ selected: selectedModelConfigId === String(model.id) }" @click="selectedModelConfigId = String(model.id); showModelPicker = false"><i /><span><b>{{ model.model }}</b><small>你的 OpenAI 兼容接口</small></span><em>自配</em></button>
                  <button v-if="!enabledModels.length" type="button" class="model-picker-manage" @click="selectTab('models'); showModelPicker = false">添加个人模型 →</button>
                </div>
              </div>
              <button class="send-button" :disabled="sending || !draft.trim()"><span>{{ sending ? '整理中' : '发送问题' }}</span><b>↗</b></button>
            </form>
          </template>
          <div v-else class="empty-state">创建一段对话，开始整理你的阅读世界。</div>
        </section>
        <aside class="workspace-notes">
          <article class="context-card card"><span class="note-index">01 / 阅读上下文</span><h3>当前阅读上下文</h3><template v-if="chatReferenceBook"><strong>《{{ chatReferenceBook.bookName }}》</strong><p>{{ chatReferenceBook.author || '作者信息待补充' }}</p><div class="reading-meter"><i :style="readingProgressStyle(chatReferenceBook)" /></div><small>安全讨论至第 {{ insightChapter }} 章</small></template><template v-else><p>先引用书架中的一部作品，回答会自动对齐到你的已读进度。</p><button class="text-action" @click="showChatPlugins = true">选择书架作品 →</button></template></article>
          <article class="shortcut-card card"><span class="note-index">02 / 开始探索</span><h3>从这里开始</h3><button @click="selectTab('insights')"><b>书籍洞察</b><span>图谱、伏笔与时间线</span></button><button @click="useStarterPrompt('推荐一本适合今晚读的书')"><b>今晚读什么</b><span>从书架和偏好出发</span></button></article>
          <div class="privacy-note"><span class="status-dot online" />你的书架、会话和模型密钥均按账户隔离</div>
        </aside>
      </section>

      <section v-else-if="activeTab === 'models'" class="model-layout">
        <header class="model-page-head"><div><span class="agent-eyebrow">模型接入</span><h2>把模型选择权交给你</h2><p>统一使用 OpenAI Chat Completions 格式。平台模型便于试用；自配模型只使用你的额度。</p></div><div class="model-credit-chip"><span>可用积分</span><b>{{ credits?.availableCredits ?? '--' }}</b><small>平台模型按次结算</small></div></header>
        <div class="model-settings-grid">
          <article class="model-platform-card">
            <div><span class="model-card-kicker">平台模型</span><h3>随时可用的阅读助手</h3><p>适合快速体验角色访谈、剧情回忆与 LightRAG 检索回答。</p></div>
            <ul><li>无需配置密钥</li><li>每次调用消耗 1 积分</li><li>会话中可随时切换</li></ul>
          </article>
          <form class="model-form" @submit.prevent="saveModel">
            <div class="model-form-head"><div><span class="model-card-kicker">自配模型</span><h3>添加一个兼容接口</h3></div><span>OpenAI 格式</span></div>
            <label>模型 ID<input v-model="modelForm.model" required maxlength="128" spellcheck="false" placeholder="例如：mimo-v2.5-pro" /></label>
            <p class="model-field-help">填写接口官方返回的精确模型 ID，例如 MiMo 接口应填写 <code>mimo-v2.5-pro</code>。</p>
            <label>兼容接口地址<input v-model="modelForm.baseUrl" maxlength="512" type="url" spellcheck="false" placeholder="https://api.xiaomimimo.com/v1" required /></label>
            <p class="model-field-help">填写 OpenAI-compatible API 的 Base URL，通常以 <code>/v1</code> 结尾。</p>
            <label>API 密钥<input v-model="modelForm.apiKey" required type="password" autocomplete="new-password" placeholder="仅在保存时提交并加密保存" /></label>
            <button class="model-save-button">加密保存模型 <b>→</b></button>
          </form>
          <article class="credit-task-card">
            <div class="credit-task-head"><div><span class="model-card-kicker">积分来源</span><h3>今天还能做什么</h3></div><small>每日最多 5 积分</small></div>
            <div v-if="levelInfo?.dailyTasks?.length" class="credit-task-list"><div v-for="task in levelInfo.dailyTasks" :key="task.taskId" :class="{ done: task.completed }"><span>{{ task.title }}</span><b>+{{ task.rewardCredits || 0 }}</b><small>{{ task.completed ? '已完成' : task.description }}</small></div></div>
            <p v-else>完成签到、有效阅读、点评或评分后即可获得平台试用积分。</p>
          </article>
          <article class="saved-models">
            <div class="saved-models-head"><div><span class="model-card-kicker">我的模型</span><h3>已接入 {{ models.length }} 个模型</h3></div><small>密钥不会再次展示</small></div>
            <p v-if="!models.length" class="model-empty">还没有个人模型。添加后，可在对话输入框右侧一键切换。</p>
            <div v-for="model in models" :key="model.id" class="saved-model"><span class="saved-model-name"><i :class="{ disabled: !model.enabled }" /><strong>{{ model.model }}</strong><small>{{ model.baseUrl }} · {{ model.keyHint }}</small></span><div class="model-actions"><button type="button" :disabled="testingModelId === model.id" @click="testModel(model.id)">{{ testingModelId === model.id ? '连接中…' : '测试' }}</button><button type="button" @click="toggleModel(model)">{{ model.enabled ? '停用' : '启用' }}</button><button type="button" class="danger" @click="removeModel(model.id)">删除</button></div></div>
          </article>
        </div>
      </section>

      <section v-else-if="activeTab === 'tasks'" class="task-center-panel">
        <header class="task-center-head"><div><span class="agent-eyebrow">后台任务中心</span><h2>知识图谱构建</h2><p>任务会自动追踪进度；每一次构建只读取你选定的章节范围。</p></div><button class="task-refresh" type="button" title="刷新任务列表" @click="loadBuildTasks"><span>↻</span> 刷新</button></header>
        <div class="task-list">
          <p v-if="!buildTasks.length" class="task-empty">还没有图谱构建任务。进入书籍洞察后，选择章节范围即可创建第一个任务。</p>
            <article v-for="task in buildTasks" :key="task.id" class="task-row">
              <div class="task-row-main"><span class="task-status" :class="task.status.toLowerCase()">{{ task.status === 'COMPLETED' ? '已完成' : task.status === 'FAILED' ? '失败' : task.status === 'RUNNING' ? '构建中' : '等待中' }}</span><strong>《{{ taskBookTitle(task) }}》</strong><small>作品编号 {{ task.canonicalBookId }} · 第 {{ task.startChapter || 1 }} 章至第 {{ task.endChapter || task.totalChapters || 1 }} 章 · {{ task.modelMode === 'BYOK' ? '个人模型' : `预计 ${task.estimatedCredits || 0} 积分` }}</small></div>
              <div class="task-row-progress"><div class="task-progress-label"><b>{{ task.completedChapters || 0 }} / {{ task.totalChapters || 0 }} 章</b><small>{{ taskProgressPercent(task) }}%</small></div><div class="task-progress-track" :class="task.status.toLowerCase()"><i :style="{ width: `${taskProgressPercent(task)}%` }" /></div><small>{{ task.message || task.errorMessage || '等待任务状态更新' }}</small></div>
              <button v-if="canDeleteBuildTask(task)" type="button" class="task-delete" @click="deleteBuildTask(task)">删除记录</button>
            </article>
        </div>
      </section>

      <section v-else-if="activeTab === 'insights'" class="insights-panel">
        <header class="insight-page-head">
          <div><span class="agent-eyebrow">阅读工作台 / 洞察</span><h2>把故事看得更清楚</h2><p>选择书架作品和阅读位置；所有结果都标明它们来自哪一段已读内容。</p></div>
          <div v-if="selectedInsightBook" class="insight-book-stamp"><b>《{{ selectedInsightBook.bookName }}》</b><span>书架已读至第 {{ safeInsightChapter }} 章</span></div>
        </header>
        <form class="insight-console" @submit.prevent="loadInsights">
          <label class="insight-field"><span>作品</span><select v-model="insightBookId" required @change="selectInsightBook"><option value="">从书架选择一本作品</option><option v-for="book in usableShelfBooks" :key="book.canonicalBookId" :value="String(book.canonicalBookId)">{{ book.bookName }}<template v-if="book.author"> · {{ book.author }}</template></option></select><small v-if="!usableShelfBooks.length">书架中还没有可关联的作品。</small></label>
          <label class="insight-field"><span>分析边界</span><div class="chapter-stepper"><button type="button" aria-label="减少一章" @click="adjustInsightChapter(-1)">−</button><span>第</span><input v-model.number="insightChapter" required inputmode="numeric" pattern="[0-9]*" @change="normalizeInsightChapter" /><span>章</span><button type="button" aria-label="增加一章" @click="adjustInsightChapter(1)">＋</button></div><small v-if="selectedInsightBook">已读进度：第 {{ safeInsightChapter }} 章</small></label>
          <p v-if="selectedInsightBook" class="insight-safety-state" :class="{ warning: insightChapter > safeInsightChapter }"><b>{{ insightChapter > safeInsightChapter ? '可能剧透' : '无剧透模式' }}</b><span>{{ insightChapter > safeInsightChapter ? '继续后将先请你确认。' : '结果严格限定在已读范围内。' }}</span></p>
          <button class="btn btn-gold insight-run" :disabled="insightLoading || !usableShelfBooks.length">{{ insightLoading ? '正在整理…' : usableShelfBooks.length ? '生成洞察' : '请先加入书架' }}</button>
        </form>
        <p v-if="effectiveInsightChapter !== null" class="insight-boundary-note" :class="{ limited: effectiveInsightChapter !== insightChapter }">
          <template v-if="effectiveInsightChapter !== insightChapter">当前返回内容止于第 {{ effectiveInsightChapter }} 章，未能覆盖你选择的第 {{ insightChapter }} 章。</template>
          <template v-else>本次洞察覆盖至第 {{ effectiveInsightChapter }} 章。</template>
        </p>
        <div v-if="insightBookId" class="insight-workspace">
          <aside class="insight-nav" aria-label="洞察功能">
            <span>功能导航</span>
            <button v-for="item in [{ id: 'capsule', label: '剧情胶囊', note: '已读回顾' }, { id: 'graph', label: '人物关系', note: '关系图谱' }, { id: 'clues', label: '线索板', note: '明确未解' }, { id: 'map', label: '阅读地图', note: '事件脉络' }, { id: 'dna', label: '相似书 DNA', note: '作品气质' }, { id: 'shelf', label: '书架管家', note: '阅读安排' }]" :key="item.id" :class="{ active: insightMode === item.id }" type="button" @click="insightMode = item.id"><b>{{ item.label }}</b><small>{{ item.note }}</small></button>
          </aside>
          <section class="insight-stage">
            <header class="insight-stage-head"><div><span class="agent-eyebrow">{{ insightModeLabel }}</span><h3>{{ insightModeTitle }}</h3></div><div class="insight-stage-actions"><div v-if="insightMode === 'shelf'" class="insight-subtabs"><button :class="{ active: shelfPanel === 'recommendations' }" type="button" @click="shelfPanel = 'recommendations'">推荐</button><button :class="{ active: shelfPanel === 'plan' }" type="button" @click="shelfPanel = 'plan'">计划</button><button :class="{ active: shelfPanel === 'groups' }" type="button" @click="shelfPanel = 'groups'">分组</button></div><button v-if="knowledgeBuild?.status !== 'READY'" type="button" class="build-index-button" @click="openKnowledgeBuildDialog">{{ knowledgeBuild?.status === 'RUNNING' || knowledgeBuild?.status === 'QUEUED' ? '查看构建任务' : '构建 AI 知识图谱' }}</button></div></header>
            <div v-if="insightError" class="card insight-error" role="alert"><strong>这次洞察没有完成</strong><p>{{ insightError }}</p><button class="btn btn-ghost btn-sm" type="button" @click="loadInsights">重新分析</button></div>
            <div v-else-if="insightLoaded" class="insight-grid">
          <article v-show="insightMode === 'capsule'" class="insight-card card"><span>00</span><h2>无剧透剧情回忆胶囊</h2><p v-if="!capsule?.timeline?.length">暂时没有可用的已读剧情摘要。</p><ul v-else><li v-for="item in capsule.timeline.slice(0, 4)" :key="item">{{ item }} <button class="evidence-jump" @click="openInsightChapter(timelineChapter(item))">查看原文</button></li></ul><small>{{ capsule?.safetyNote }}</small></article>
          <article v-show="insightMode === 'graph'" class="insight-card card graph-card">
            <div class="graph-card-title"><span>01</span><div><h2>人物关系星球</h2><p>把关系放到同一颗星球上看：拖动即可从不同视角观察人物、地点与事件。</p></div></div>
            <p v-if="!graph.nodes?.length">当前还没有足够的已读内容来建立关系图谱。</p>
            <template v-else>
              <div class="globe-toolbar">
                <div class="graph-tools" aria-label="图谱类型筛选"><button v-for="type in graphTypes" :key="type" :class="{ active: graphTypeFilter === type }" @click="graphTypeFilter = type">{{ graphTypeLabel(type) }}</button></div>
                <div class="globe-toolbar-status"><b>{{ visibleGraphNodes.length }}</b> 个节点 · <b>{{ visibleGraphEdges.length }}</b> 条已验证关系 <button type="button" @click="resetRelationshipGlobe">回到正面</button><button type="button" class="globe-expand" @click="openRelationshipCanvas">全屏查看</button></div>
              </div>
              <div class="relationship-globe-shell">
                <canvas ref="relationshipGlobe" class="relationship-globe" role="img" tabindex="0" aria-label="可旋转的人物关系星球。拖动旋转，点击人物或连线查看依据。" @pointerdown="onGlobePointerDown" @pointermove="onGlobePointerMove" @pointerup="onGlobePointerUp" @pointercancel="onGlobePointerUp" @wheel.prevent="onGlobeWheel"></canvas>
                <div class="globe-hud globe-hud-top"><span>LightRAG 关系星球</span><i></i><span>拖动旋转 · 点击查看依据</span></div>
                <div class="globe-hud globe-hud-bottom"><span><i class="legend-dot character"></i>人物</span><span><i class="legend-dot location"></i>地点/组织</span><span><i class="legend-dot event"></i>事件/线索</span></div>
              </div>
              <div class="graph-inspector">
                <div v-if="selectedGraphEvidence" class="graph-evidence"><span>当前选中</span><b>{{ selectedGraphEvidence.label }}</b><small>第 {{ selectedGraphEvidence.chapter + 1 }} 章 · 可信度 {{ Math.round((selectedGraphEvidence.confidence || 0) * 100) }}%</small><p>{{ selectedGraphEvidence.evidence || '暂时没有可展示的原文依据。' }}</p><button class="evidence-jump" @click="showEvidenceSource(selectedGraphEvidence)">查看原文依据</button></div>
                <div v-else class="graph-evidence graph-evidence-empty"><span>关系阅读提示</span><b>先旋转，再进入一条关系</b><p>前景节点更清晰；点击人物、地点或关系连线，可查看这条图谱的原文依据。</p></div>
                <div class="graph-node-list" aria-label="当前展示的核心节点"><button v-for="node in visibleGraphNodes.slice(0, 5)" :key="node.id" type="button" @click="selectGraphEvidence(node, 'NODE')"><i :class="node.type"></i><span>{{ node.name }}</span><small>{{ graphTypeLabel(node.type) }}</small></button></div>
              </div>
            </template>
            <small class="graph-footnote">{{ graph.edges?.length || 0 }} 条关系仅来自已读章节；较淡的连线表示可信度较低。</small>
          </article>
          <article v-show="insightMode === 'clues'" class="insight-card card clue-board-card"><span>02</span><h2>未解线索</h2><p v-if="!clues.length">已读范围内没有达到“明确未解”阈值的线索，因此不把普通物品或陌生名词误报为伏笔。</p><ul v-else><li v-for="clue in clues.slice(0, 5)" :key="`${clue.chapterIndex}-${clue.excerpt}`">第 {{ clue.chapterIndex + 1 }} 章 · {{ clue.excerpt }} <em class="clue-status">{{ clueStatusLabel(clue.status) }}</em><button class="evidence-jump" @click="showEvidenceSource({ label: '线索原文', chapter: clue.chapterIndex, evidence: clue.excerpt, confidence: 1 })">查看原文依据</button></li></ul><small>这里只显示原文中有明确未解信号的线索；后续揭示仅会在读到对应章节后出现。</small></article>
          <article v-show="insightMode === 'map'" class="insight-card card reading-map-card">
            <div class="map-card-title"><span>03</span><div><h2>故事事件地图</h2><p>不是重复剧情摘要：这里按事件顺序展示主线推进，并标明它与下一段故事的关联方式。</p></div></div>
            <p v-if="!readingMap.events?.length">继续阅读并建立索引后，这里会生成可追溯的故事事件地图。</p>
            <template v-else>
              <div class="map-toolbar"><div class="graph-tools"><button v-for="branch in readingMapBranches" :key="branch" :class="{ active: readingMapBranch === branch }" @click="readingMapBranch = branch">{{ branchLabel(branch) }}</button></div><small><b>{{ visibleReadingMapLinks.length }}</b> 条事件链路</small></div>
              <ol class="reading-map-events story-thread"><li v-for="(event, index) in visibleReadingMapEvents" :key="event.id"><div class="event-marker"><b>{{ String(event.chapterIndex + 1).padStart(2, '0') }}</b><i /></div><div class="event-card"><div><em>{{ branchLabel(event.branch) }}</em><strong>{{ event.name }}</strong></div><p>{{ event.evidence || '暂时没有可展示的事件依据。' }}</p><footer><small>可信度 {{ Math.round((event.confidence || 0) * 100) }}%</small><span v-if="mapNextLink(event.id)">{{ mapNextLink(event.id).relation }}</span><button class="evidence-jump" @click="showEvidenceSource({ label: event.name, chapter: event.chapterIndex, evidence: event.evidence, confidence: event.confidence })">查看原文依据</button></footer></div><div v-if="index < visibleReadingMapEvents.length - 1" class="event-connector"><span>{{ mapConnectorLabel(event.id, visibleReadingMapEvents[index + 1]?.id) }}</span></div></li></ol>
            </template>
            <small class="map-footnote">“角色串联”表示相邻事件共享已验证人物，不会被误写成因果；只有明确抽取到的因果关系才会标注为“导致”“推动”等。</small>
          </article>
          <article v-show="insightMode === 'dna'" class="insight-card card"><span>04</span><h2>相似书籍 DNA</h2><p v-if="!similarBooks.length">目前没有足够的已读作品可供比较。</p><ul v-else><li v-for="book in similarBooks" :key="book.canonicalBookId"><strong>{{ book.title || `作品 #${book.canonicalBookId}` }}</strong><span v-if="book.author"> · {{ book.author }}</span> · 相似度 {{ Math.round(book.similarity * 100) }}%<br />{{ book.explanation }} <button class="btn btn-ghost btn-sm" @click="openRecommendedBook(book)">打开作品</button></li></ul><small>依据已索引的文本特征比较，不凭空添加标签。</small></article>
          <article v-show="insightMode === 'shelf' && shelfPanel === 'recommendations'" class="insight-card card"><span>05</span><h2>动态书架管家</h2><p v-if="!shelfRecommendations.length">你的书架还在等待第一次阅读反馈。</p><ul v-else><li v-for="item in shelfRecommendations" :key="item.title"><strong>{{ item.title }}</strong><br />{{ item.reason }}<span v-if="item.canonicalBookId" class="recommendation-actions"><button @click="saveRecommendationFeedback(item, 'OPEN')">查看</button><button @click="saveRecommendationFeedback(item, 'LIKE')">有帮助</button><button @click="confirmAddToShelf(item)">加入书架</button><button @click="saveRecommendationFeedback(item, 'DISMISS')">暂不推荐</button></span></li></ul><small>加入书架前一定会征得你的确认。</small></article>
          <article v-show="insightMode === 'shelf' && shelfPanel === 'plan'" class="insight-card card reading-plan-card"><span>06</span><h2>阅读计划</h2><p>{{ readingPlan?.summary || '正在根据你的阅读记录生成计划…' }}</p><ul v-if="readingPlan?.items?.length"><li v-for="item in readingPlan.items" :key="item.canonicalBookId"><strong>{{ item.title }}</strong><br />今天建议阅读 {{ item.suggestedChaptersToday }} 章 · 当前读到第 {{ item.currentChapter + 1 }} 章<span v-if="item.totalChapters"> / 共 {{ item.totalChapters }} 章</span><br /><small>{{ item.reason }}</small></li></ul><small>计划按规则生成并遵守防剧透原则，不会提前读取或把书架内容发送给模型。</small></article>
          <article v-show="insightMode === 'shelf' && shelfPanel === 'groups'" class="insight-card card shelf-groups-card"><span>07</span><h2>书架分组</h2><p v-if="!shelfGroups.length">暂时没有可分组的已读书架内容。</p><ul v-else><li v-for="item in shelfGroups" :key="item.canonicalBookId"><strong>{{ item.title }}</strong><label><select :value="item.groupCode" @change="saveShelfGroup(item, $event.target.value)"><option value="FOLLOWING">正在追更</option><option value="SHORT_SESSION">短时阅读</option><option value="WEEKEND">周末沉浸</option><option value="RESTART">准备重读</option><option value="CLEANUP">考虑整理</option><option value="AUTO">自动安排</option></select></label><small>{{ item.pinned ? '当前分组已手动固定；选择“自动安排”可恢复智能建议。' : '根据安全的书架阅读记录生成建议。' }}</small></li></ul><small>这里只调整 Agent 的展示和排序提示，不会移动或删除书架中的作品。</small></article>
            </div>
            <div v-else class="insight-empty-stage"><span>从阅读进度出发</span><h4>选择范围后生成这一段故事的洞察</h4><p>剧情回顾、人物关系和线索板会在同一个阅读边界下协同更新。</p></div>
          </section>
        </div>
      </section>

      <section v-else class="privacy-layout">
        <form class="card preference-form" @submit.prevent="savePreferences">
          <span class="agent-eyebrow">偏好与隐私</span>
          <h2>阅读偏好</h2>
          <label>喜欢的题材 <input v-model="preferenceGenres" maxlength="160" placeholder="玄幻、悬疑、言情" /></label>
          <label>不想看的内容 <input v-model="avoidedThemes" maxlength="160" placeholder="例如：恐怖" /></label>
          <label>防剧透范围 <select v-model="preferences.spoilerLevel"><option value="STRICT">严格：只讨论已读章节</option><option value="STANDARD">标准：在已读范围内展开讨论</option></select></label>
          <label class="switch-line"><input v-model="preferences.personalizationEnabled" type="checkbox" /> 根据我的偏好推荐作品</label>
          <label class="switch-line"><input v-model="preferences.retainConversations" type="checkbox" /> 保存对话记录</label>
          <button class="btn btn-primary">保存设置</button>
        </form>
        <article class="card privacy-danger"><h2>个人数据</h2><p>你可以清除 Agent 的偏好设置，也可以一并删除全部对话记录。删除后无法恢复。</p><label class="switch-line"><input v-model="eraseConversations" type="checkbox" /> 同时删除对话记录</label><button class="btn btn-ghost" @click="erasePersonalData">清除 Agent 数据</button></article>
      </section>
    </div>
  </main>
  <Teleport to="body">
    <div v-if="showSpoilerConfirm" class="spoiler-dialog-backdrop" role="presentation" @click.self="cancelSpoilerAnalysis">
      <section class="spoiler-dialog" role="dialog" aria-modal="true" aria-labelledby="spoiler-dialog-title">
        <span class="spoiler-dialog-mark">!</span>
        <p class="spoiler-dialog-kicker">阅读边界提醒</p>
        <h2 id="spoiler-dialog-title">继续会看到尚未读到的剧情</h2>
        <p>你选择分析到第 {{ pendingSpoilerChapter }} 章，但书架记录显示你目前只读到第 {{ safeInsightChapter }} 章。继续后最多会提前看到 {{ Math.max(0, pendingSpoilerChapter - safeInsightChapter) }} 章内容，人物关系、伏笔与剧情回忆都会包含这段信息。</p>
        <div class="spoiler-dialog-actions"><button type="button" class="btn btn-ghost" @click="cancelSpoilerAnalysis">保持无剧透</button><button type="button" class="btn btn-primary" @click="confirmSpoilerAnalysis">我已知晓，继续分析</button></div>
      </section>
    </div>
  </Teleport>
  <Teleport to="body">
    <section v-if="showRelationshipCanvas" class="relationship-canvas-dialog" role="dialog" aria-modal="true" aria-label="人物关系星球全屏画布" @keydown.esc="closeRelationshipCanvas">
      <header class="relationship-canvas-head"><div><span>LightRAG / 关系视图</span><h2>{{ selectedInsightBook?.bookName || '当前作品' }}的人物关系星球</h2><p>拖动旋转，滚轮或触控板可缩放视角；每条连线均标注关系类型，点击可查看可追溯的章节原文。</p></div><div><span>{{ visibleGraphNodes.length }} 个节点</span><span>{{ visibleGraphEdges.length }} 条关系</span><button type="button" @click="resetRelationshipGlobe">回到正面</button><button type="button" class="relationship-canvas-close" @click="closeRelationshipCanvas">退出全屏</button></div></header>
      <div class="relationship-canvas-stage"><canvas ref="fullRelationshipGlobe" class="relationship-globe" tabindex="0" role="img" aria-label="可旋转的人物关系星球全屏画布" @pointerdown="onGlobePointerDown" @pointermove="onGlobePointerMove" @pointerup="onGlobePointerUp" @pointercancel="onGlobePointerUp" @wheel.prevent="onGlobeWheel"></canvas><div class="canvas-corner-note">关系均来自已读章节 · 点击可核验</div></div>
      <footer class="relationship-canvas-foot"><div class="globe-hud-bottom"><span><i class="legend-dot character"></i>人物</span><span><i class="legend-dot location"></i>地点 / 组织</span><span><i class="legend-dot event"></i>事件 / 线索</span></div><div v-if="selectedGraphEvidence" class="canvas-selection"><span>已选中</span><b>{{ selectedGraphEvidence.label }}</b><small>第 {{ selectedGraphEvidence.chapter + 1 }} 章</small><button type="button" @click="showEvidenceSource(selectedGraphEvidence)">查看原文依据</button></div><p v-else>选中一条关系，查看它为什么会出现在图谱中。</p></footer>
    </section>
  </Teleport>
  <Teleport to="body">
    <div v-if="evidenceSource" class="evidence-source-backdrop" role="presentation" @click.self="evidenceSource = null">
      <section class="evidence-source-dialog" role="dialog" aria-modal="true" aria-labelledby="evidence-source-title">
        <span>图谱依据 / 已读范围</span><h2 id="evidence-source-title">{{ evidenceSource.label }}</h2><small>第 {{ evidenceSource.chapter + 1 }} 章 · 可信度 {{ Math.round((evidenceSource.confidence || 0) * 100) }}%</small><blockquote>{{ evidenceSource.evidence || '这条图谱记录没有保存可展示的原文摘录。' }}</blockquote><p>这里展示的是构建图谱时保存的原文摘录；“打开阅读器”会定位到对应章节，便于你在完整上下文中核验。</p><div><button type="button" class="btn btn-ghost" @click="evidenceSource = null">关闭</button><button type="button" class="btn btn-primary" @click="openEvidenceChapter">打开阅读器核验</button></div>
      </section>
    </div>
  </Teleport>
  <Teleport to="body">
    <div v-if="showKnowledgeBuildDialog" class="knowledge-build-backdrop" @click.self="showKnowledgeBuildDialog = false">
      <section class="knowledge-build-dialog" role="dialog" aria-modal="true" aria-labelledby="knowledge-build-title">
        <span class="dialog-kicker">AI 知识图谱构建</span>
        <h2 id="knowledge-build-title">只构建你选定章节的故事关系</h2>
        <p v-if="knowledgeBuild">将读取第 {{ buildForm.startChapter || 1 }} 章至第 {{ buildForm.endChapter || knowledgeBuild.totalChapters || 1 }} 章，共 {{ knowledgeBuild.selectedChapters || 0 }} 章；约 {{ Number(knowledgeBuild.estimatedInputTokens || 0).toLocaleString() }} 输入 Token 与 {{ Number(knowledgeBuild.estimatedOutputTokens || 0).toLocaleString() }} 输出 Token。</p>
        <div class="build-cost-note"><b>预计 {{ knowledgeBuild?.estimatedCredits || 0 }} 平台积分</b><span>{{ knowledgeBuild?.creditRule }}</span></div>
        <div class="build-range"><label>起始章节<input v-model.number="buildForm.startChapter" min="1" inputmode="numeric" pattern="[0-9]*" @input="queueBuildEstimate" /></label><span>至</span><label>结束章节<input v-model.number="buildForm.endChapter" min="1" inputmode="numeric" pattern="[0-9]*" @input="queueBuildEstimate" /></label></div>
        <label>构建模型<select v-model="buildForm.modelMode"><option value="PLATFORM">平台模型（消耗预估积分）</option><option value="BYOK">我的模型（不消耗平台积分）</option></select></label>
        <label v-if="buildForm.modelMode === 'BYOK'">选择个人模型<select v-model="buildForm.modelConfigId"><option value="">请选择已启用的模型</option><option v-for="model in enabledModels" :key="model.id" :value="String(model.id)">{{ model.model }}</option></select></label>
        <label class="build-share"><input v-model="buildForm.sharePublic" type="checkbox" />公开这本书的知识图谱，完成后发布一条广场分享动态</label>
        <p class="dialog-help">公开的是实体、关系和摘要索引，不会公开小说原文、你的对话或个人模型密钥。</p>
        <div class="spoiler-dialog-actions"><button type="button" class="btn btn-ghost" @click="showKnowledgeBuildDialog = false">暂不构建</button><button type="button" class="btn btn-primary" :disabled="buildSubmitting || (buildForm.modelMode === 'BYOK' && !buildForm.modelConfigId)" @click="startKnowledgeBuild">{{ buildSubmitting ? '正在创建任务…' : '确认开始构建' }}</button></div>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '@/composables/useToast'
import { apiAddToShelf } from '@/api/bookshelf'
import { apiGetMyShelf } from '@/api/bookshelf'
import { apiGetMyLevel } from '@/api/user'
  import { apiCreateAgentSession, apiDeleteAgentModel, apiDeleteAgentSession, apiDeleteBookKnowledgeTask, apiEraseAgentPersonalData, apiExportAgentSession, apiGetAgentCredits, apiGetAgentGraph, apiGetAgentClues, apiGetAgentInfrastructure, apiGetAgentMessages, apiGetAgentPreferences, apiGetAgentTimeline, apiGetAgentReadingMap, apiGetAgentReadingPlan, apiGetAgentReaderLink, apiGetAgentShelfGroups, apiGetPlotCapsule, apiGetQuickRecommendations, apiGetSimilarBooks, apiListAgentModels, apiRenameAgentSession, apiSaveAgentModel, apiSaveAgentPreferences, apiSaveAgentShelfGroup, apiSaveRecommendationFeedback, apiSetAgentModelEnabled, apiTestAgentModel, apiListAgentSessions, apiSearchAgentSessions, apiPrepareBookKnowledgeBuild, apiStartBookKnowledgeBuild, apiGetBookKnowledgeStatus, apiGetBookKnowledgeTasks, apiUpdateAgentMessage, streamAgentMessage } from '@/api/agent'

const toast = useToast()
const actionNotice = ref(null)
function showActionNotice(type, message) { actionNotice.value = { type, message }; window.setTimeout(() => { actionNotice.value = null }, 3500) }
const router = useRouter()
const route = useRoute()
const activeTab = ref('overview')
const tabs = [{ id: 'overview', label: '概览' }, { id: 'chats', label: '对话' }, { id: 'insights', label: '洞察' }, { id: 'tasks', label: '任务' }, { id: 'models', label: '模型' }, { id: 'preferences', label: '设置' }]
const sessions = ref([])
const sessionSearch = ref('')
const activeSession = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const editingMessageId = ref(null)
const editingMessageContent = ref('')
const editingSessionTitle = ref(false)
const sessionTitleDraft = ref('')
const sessionTitleInput = ref(null)
const credits = ref(null)
const levelInfo = ref(null)
const infrastructure = ref(null)
const models = ref([])
const selectedModelConfigId = ref('')
const showModelPicker = ref(false)
const testingModelId = ref(null)
const modelForm = ref({ model: 'deepseek-chat', apiKey: '', baseUrl: 'https://api.deepseek.com' })
const insightBookId = ref('')
const insightChapter = ref(1)
const insightLoading = ref(false)
const insightLoaded = ref(false)
const showChatPlugins = ref(false)
const chatReferenceBookId = ref('')
const graph = ref({ nodes: [], edges: [] })
const clues = ref([])
const timeline = ref([])
const readingMap = ref({ events: [], links: [] })
const similarBooks = ref([])
const capsule = ref(null)
const shelfRecommendations = ref([])
const readingPlan = ref(null)
const shelfGroups = ref([])
const shelfBooks = ref([])
const starterPrompts = ['帮我回忆最近的剧情', '分析当前人物关系', '推荐一本适合今晚读的书', '这本书有哪些未解伏笔？']
const graphTypeFilter = ref('ALL')
const readingMapBranch = ref('ALL')
const selectedGraphEvidence = ref(null)
const relationshipGlobe = ref(null)
const fullRelationshipGlobe = ref(null)
const showRelationshipCanvas = ref(false)
const evidenceSource = ref(null)
const globeRotation = { pitch: -0.18, yaw: -0.72 }
let globeZoom = 1
let globeDrag = null
let globeProjectedNodes = []
let globeProjectedEdges = []
let activeGlobeCanvas = null
let globeRenderFrame = null
const preferences = ref({ preferredGenres: [], avoidedThemes: [], spoilerLevel: 'STRICT', personalizationEnabled: true, retainConversations: true })
const preferenceGenres = ref('')
const avoidedThemes = ref('')
const eraseConversations = ref(true)
const loadNotice = ref('')
const pageLoading = ref(true)
const insightError = ref('')
const effectiveInsightChapter = ref(null)
const showSpoilerConfirm = ref(false)
const pendingSpoilerChapter = ref(null)
const spoilersConfirmed = ref(false)
const insightMode = ref('capsule')
const shelfPanel = ref('recommendations')
const knowledgeBuild = ref(null)
const buildTasks = ref([])
const showKnowledgeBuildDialog = ref(false)
const buildSubmitting = ref(false)
const buildForm = ref({ modelMode: 'PLATFORM', modelConfigId: '', sharePublic: true, startChapter: 1, endChapter: 1 })
let taskPollTimer = null
let buildEstimateTimer = null
const graphTypes = computed(() => ['ALL', ...new Set((graph.value.nodes || []).map(node => node.type).filter(Boolean))])
const enabledModels = computed(() => models.value.filter(model => model.enabled))
const selectedChatModelLabel = computed(() => enabledModels.value.find(model => String(model.id) === selectedModelConfigId.value)?.model || '平台模型')
const usableShelfBooks = computed(() => shelfBooks.value.filter(book => book?.canonicalBookId && book.bookName))
const selectedInsightBook = computed(() => usableShelfBooks.value.find(book => String(book.canonicalBookId) === String(insightBookId.value)) || null)
const safeInsightChapter = computed(() => Math.max(1, Number(selectedInsightBook.value?.lastChapterIndex ?? 0) + 1))
const insightModeMeta = {
  capsule: { label: '剧情胶囊', title: '无剧透剧情回忆' },
  graph: { label: '人物关系', title: '人物关系图谱' },
  clues: { label: '线索板', title: '明确未解的线索' },
  map: { label: '阅读地图', title: '故事事件脉络' },
  dna: { label: '相似书 DNA', title: '相似作品与阅读偏好' },
  shelf: { label: '书架管家', title: '书架安排与阅读计划' }
}
const insightModeLabel = computed(() => insightModeMeta[insightMode.value]?.label || '书籍洞察')
const insightModeTitle = computed(() => insightModeMeta[insightMode.value]?.title || '书籍洞察')
const chatReferenceBook = computed(() => usableShelfBooks.value.find(book => String(book.canonicalBookId) === String(chatReferenceBookId.value)) || null)
const visibleGraph = computed(() => {
  const nodes = (graph.value.nodes || []).filter(node => node?.id != null)
  const nodeById = new Map(nodes.map(node => [String(node.id), node]))
  const allEdges = (graph.value.edges || []).filter(edge => nodeById.has(String(edge.source)) && nodeById.has(String(edge.target)))
  const isAllTypes = graphTypeFilter.value === 'ALL'
  const primaryIds = new Set(nodes
    .filter(node => isAllTypes || node.type === graphTypeFilter.value)
    .map(node => String(node.id)))
  // A type filter keeps its matching nodes and their one-hop context, so cross-type relations remain visible.
  const eligibleEdges = isAllTypes
    ? allEdges
    : allEdges.filter(edge => primaryIds.has(String(edge.source)) || primaryIds.has(String(edge.target)))
  const degree = new Map(nodes.map(node => [String(node.id), 0]))
  eligibleEdges.forEach(edge => {
    degree.set(String(edge.source), (degree.get(String(edge.source)) || 0) + 1)
    degree.set(String(edge.target), (degree.get(String(edge.target)) || 0) + 1)
  })
  const candidateIds = new Set(isAllTypes ? nodes.map(node => String(node.id)) : primaryIds)
  eligibleEdges.forEach(edge => {
    candidateIds.add(String(edge.source))
    candidateIds.add(String(edge.target))
  })
  const compareNodes = (left, right) => {
    const degreeDifference = (degree.get(String(right.id)) || 0) - (degree.get(String(left.id)) || 0)
    if (degreeDifference) return degreeDifference
    const chapterDifference = Number(right.lastChapter ?? right.firstChapter ?? -1) - Number(left.lastChapter ?? left.firstChapter ?? -1)
    if (chapterDifference) return chapterDifference
    return Number(right.confidence || 0) - Number(left.confidence || 0)
  }
  const selectedIds = new Set()
  // Select complete high-value relations first; this prevents the old "top 12 nodes, zero edges" result.
  const rankedEdges = [...eligibleEdges].sort((left, right) => {
    const leftDegree = (degree.get(String(left.source)) || 0) + (degree.get(String(left.target)) || 0)
    const rightDegree = (degree.get(String(right.source)) || 0) + (degree.get(String(right.target)) || 0)
    return rightDegree - leftDegree || Number(right.confidence || 0) - Number(left.confidence || 0)
  })
  for (const edge of rankedEdges) {
    const endpoints = [String(edge.source), String(edge.target)]
    const additions = endpoints.filter(id => !selectedIds.has(id))
    if (selectedIds.size + additions.length > 12) continue
    additions.forEach(id => selectedIds.add(id))
  }
  const rankedCandidates = [...candidateIds]
    .map(id => nodeById.get(id))
    .filter(Boolean)
    .sort(compareNodes)
  for (const node of rankedCandidates) {
    if (selectedIds.size >= 12) break
    selectedIds.add(String(node.id))
  }
  const selectedNodes = nodes
    .filter(node => selectedIds.has(String(node.id)))
    .sort(compareNodes)
  const selectedNodeIds = new Set(selectedNodes.map(node => String(node.id)))
  return {
    nodes: selectedNodes,
    edges: eligibleEdges.filter(edge => selectedNodeIds.has(String(edge.source)) && selectedNodeIds.has(String(edge.target)))
  }
})
const visibleGraphNodes = computed(() => visibleGraph.value.nodes)
const visibleGraphEdges = computed(() => visibleGraph.value.edges)
const readingMapBranches = computed(() => ['ALL', ...new Set((readingMap.value.events || []).map(event => event.branch).filter(Boolean))])
const visibleReadingMapEvents = computed(() => (readingMap.value.events || []).filter(event => readingMapBranch.value === 'ALL' || event.branch === readingMapBranch.value))
const visibleReadingMapLinks = computed(() => {
  const ids = new Set(visibleReadingMapEvents.value.map(event => event.id))
  return (readingMap.value.links || []).filter(link => ids.has(link.source) && ids.has(link.target))
})
function statusDot(value) { return value ? 'status-dot online' : 'status-dot pending' }
function graphTypeLabel(type) {
  return ({ ALL: '全部', CHARACTER: '人物', LOCATION: '地点', ORGANIZATION: '组织', EVENT: '事件', CLUE: '线索' })[type] || '其他'
}
function clueStatusLabel(status) {
  return ({ OPEN: '待解开', PARTIALLY_RESOLVED: '部分揭示', RESOLVED: '已解开' })[status] || '待确认'
}
function branchLabel(branch) {
  return ({ ALL: '全部线索', MAIN: '主线', SIDE: '支线' })[branch] || '其他线索'
}
function readingProgressStyle(book) {
  const chapter = Math.max(1, Number(book?.lastChapterIndex ?? 0) + 1)
  const total = Math.max(chapter, Number(book?.totalChapters ?? chapter))
  return { width: `${Math.min(100, Math.max(5, chapter / total * 100))}%` }
}

watch(() => route.query.tab, (tab) => {
  const next = ['overview', 'chats', 'models', 'insights', 'tasks', 'preferences'].includes(tab) ? tab : 'overview'
  if (activeTab.value !== next) activeTab.value = next
}, { immediate: true })

function selectTab(tab) {
  activeTab.value = tab
  router.replace({ query: { ...route.query, tab } })
}
const globeNodeColors = {
  CHARACTER: '#ffc65a',
  LOCATION: '#7edbd0',
  ORGANIZATION: '#9cb5ff',
  EVENT: '#f58e70',
  CLUE: '#d9a5ff'
}

function requestRelationshipGlobeRender () {
  if (globeRenderFrame) return
  globeRenderFrame = window.requestAnimationFrame(() => {
    globeRenderFrame = null
    renderRelationshipGlobe()
  })
}

function openRelationshipCanvas () {
  showRelationshipCanvas.value = true
  nextTick(requestRelationshipGlobeRender)
}

function closeRelationshipCanvas () {
  showRelationshipCanvas.value = false
  nextTick(requestRelationshipGlobeRender)
}

function showEvidenceSource (item) {
  evidenceSource.value = {
    label: item?.label || item?.relation || item?.name || '图谱依据',
    chapter: Math.max(0, Number(item?.chapter ?? item?.firstChapter ?? 0)),
    confidence: Number(item?.confidence || 0),
    evidence: item?.evidence || ''
  }
}

function openEvidenceChapter () {
  const chapter = evidenceSource.value?.chapter
  evidenceSource.value = null
  openInsightChapter(chapter)
}

function graphRelationLabel (relation) {
  return ({
    PARTICIPATES_IN: '参与事件',
    INTERACTS_WITH: '发生互动',
    KNOWS: '彼此相识',
    OPPOSES: '存在对立',
    ASSOCIATED_WITH: '存在关联',
    CLUE_FOR: '关联线索',
    CAUSES: '推动发生',
    LEADS_TO: '引出后续'
  })[relation] || relation || '相关'
}

function quadraticPoint (from, control, to, t = 0.5) {
  const inverse = 1 - t
  return {
    x: inverse * inverse * from.x + 2 * inverse * t * control.x + t * t * to.x,
    y: inverse * inverse * from.y + 2 * inverse * t * control.y + t * t * to.y
  }
}

function drawGlobeRelationLabel (context, item, alpha, radius) {
  // A line is only useful when its relation predicate remains visible in the canvas.
  if (item.depth < -0.22) return
  const label = graphRelationLabel(item.edge.relation)
  const point = quadraticPoint(item.source, item.control, item.target, 0.5)
  const fontSize = Math.max(10, Math.min(13, radius / 20))
  context.save()
  context.globalAlpha = Math.min(1, alpha + 0.2)
  context.font = `800 ${fontSize}px sans-serif`
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  const width = context.measureText(label).width + 12
  const height = fontSize + 8
  context.fillStyle = 'rgba(4, 24, 33, .9)'
  context.strokeStyle = 'rgba(218, 249, 226, .62)'
  context.lineWidth = 1
  context.beginPath()
  context.roundRect(point.x - width / 2, point.y - height / 2, width, height, height / 2)
  context.fill()
  context.stroke()
  context.fillStyle = '#fffdf2'
  context.fillText(label, point.x, point.y + .5)
  context.restore()
}

function spherePoint (index, total) {
  if (total <= 1) return { x: 0, y: 0, z: 1 }
  const vertical = 1 - (index / (total - 1)) * 2
  const horizontal = Math.sqrt(Math.max(0, 1 - vertical * vertical))
  const angle = index * Math.PI * (3 - Math.sqrt(5))
  return { x: Math.cos(angle) * horizontal, y: vertical, z: Math.sin(angle) * horizontal }
}

function rotateGlobePoint (point) {
  const yawCos = Math.cos(globeRotation.yaw)
  const yawSin = Math.sin(globeRotation.yaw)
  const pitchCos = Math.cos(globeRotation.pitch)
  const pitchSin = Math.sin(globeRotation.pitch)
  const yawX = point.x * yawCos + point.z * yawSin
  const yawZ = point.z * yawCos - point.x * yawSin
  return { x: yawX, y: point.y * pitchCos - yawZ * pitchSin, z: point.y * pitchSin + yawZ * pitchCos }
}

function projectGlobePoint (point, centerX, centerY, radius) {
  const rotated = rotateGlobePoint(point)
  const scale = radius / (1.24 - rotated.z * 0.31)
  return { x: centerX + rotated.x * scale, y: centerY - rotated.y * scale, z: rotated.z, scale }
}

function normalizeGlobeVector (point) {
  const length = Math.hypot(point.x, point.y, point.z) || 1
  return { x: point.x / length, y: point.y / length, z: point.z / length }
}

function drawGlobeGrid (context, centerX, centerY, radius) {
  context.save()
  context.beginPath()
  context.arc(centerX, centerY, radius, 0, Math.PI * 2)
  context.clip()
  context.lineWidth = 0.7
  const drawCurve = (points, alpha) => {
    context.beginPath()
    points.forEach((point, index) => {
      const projected = projectGlobePoint(point, centerX, centerY, radius)
      if (index === 0) context.moveTo(projected.x, projected.y)
      else context.lineTo(projected.x, projected.y)
    })
    context.strokeStyle = `rgba(198, 236, 221, ${alpha})`
    context.stroke()
  }
  ;[-60, -30, 0, 30, 60].forEach(latitude => {
    const radians = latitude * Math.PI / 180
    const points = Array.from({ length: 65 }, (_, index) => {
      const longitude = index / 64 * Math.PI * 2
      return { x: Math.cos(radians) * Math.cos(longitude), y: Math.sin(radians), z: Math.cos(radians) * Math.sin(longitude) }
    })
    drawCurve(points, latitude === 0 ? 0.24 : 0.13)
  })
  ;[-120, -60, 0, 60, 120].forEach(longitude => {
    const radians = longitude * Math.PI / 180
    const points = Array.from({ length: 65 }, (_, index) => {
      const latitude = -Math.PI / 2 + index / 64 * Math.PI
      return { x: Math.cos(latitude) * Math.cos(radians), y: Math.sin(latitude), z: Math.cos(latitude) * Math.sin(radians) }
    })
    drawCurve(points, 0.13)
  })
  context.restore()
}

function drawGlobeArrow (context, from, to, color, alpha) {
  const angle = Math.atan2(to.y - from.y, to.x - from.x)
  const size = 5
  context.save()
  context.fillStyle = color.replace(')', `, ${alpha})`).replace('rgb', 'rgba')
  context.translate(to.x, to.y)
  context.rotate(angle)
  context.beginPath()
  context.moveTo(0, 0)
  context.lineTo(-size, -size * 0.6)
  context.lineTo(-size, size * 0.6)
  context.closePath()
  context.fill()
  context.restore()
}

function renderRelationshipGlobe () {
  const canvas = showRelationshipCanvas.value ? fullRelationshipGlobe.value : relationshipGlobe.value
  if (!canvas || !visibleGraphNodes.value.length) return
  activeGlobeCanvas = canvas
  const bounds = canvas.getBoundingClientRect()
  if (!bounds.width || !bounds.height) return
  const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
  const width = Math.floor(bounds.width)
  const height = Math.floor(bounds.height)
  if (canvas.width !== width * pixelRatio || canvas.height !== height * pixelRatio) {
    canvas.width = width * pixelRatio
    canvas.height = height * pixelRatio
  }
  const context = canvas.getContext('2d')
  context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
  context.clearRect(0, 0, width, height)
  const centerX = width / 2
  const centerY = height / 2
  const radius = Math.min(width * 0.39, height * 0.43) * globeZoom
  const atmosphere = context.createRadialGradient(centerX - radius * 0.28, centerY - radius * 0.35, radius * 0.08, centerX, centerY, radius * 1.42)
  atmosphere.addColorStop(0, '#315d6a')
  atmosphere.addColorStop(0.5, '#143c48')
  atmosphere.addColorStop(0.79, '#092b38')
  atmosphere.addColorStop(1, 'rgba(7, 23, 34, 0)')
  context.fillStyle = atmosphere
  context.fillRect(0, 0, width, height)
  context.beginPath()
  context.arc(centerX, centerY, radius, 0, Math.PI * 2)
  context.fillStyle = '#123947'
  context.fill()
  context.lineWidth = 2
  context.strokeStyle = 'rgba(202, 241, 221, .34)'
  context.stroke()
  drawGlobeGrid(context, centerX, centerY, radius)

  const projectedNodes = visibleGraphNodes.value.map((node, index) => {
    const point = spherePoint(index, visibleGraphNodes.value.length)
    return { node, world: point, ...projectGlobePoint(point, centerX, centerY, radius) }
  })
  const nodeById = new Map(projectedNodes.map(item => [String(item.node.id), item]))
  const projectedEdges = visibleGraphEdges.value
    .map(edge => {
      const source = nodeById.get(String(edge.source))
      const target = nodeById.get(String(edge.target))
      if (!source || !target) return null
      const controlWorld = normalizeGlobeVector({ x: source.world.x + target.world.x, y: source.world.y + target.world.y, z: source.world.z + target.world.z })
      const control = projectGlobePoint(controlWorld, centerX, centerY, radius * 1.08)
      return { edge, source, target, control, depth: (source.z + target.z + control.z) / 3 }
    })
    .filter(Boolean)
    .sort((left, right) => left.depth - right.depth)
  projectedEdges.forEach(item => {
    const alpha = Math.max(0.16, Math.min(0.86, 0.42 + item.depth * 0.32))
    const isSelected = selectedGraphEvidence.value?.label === item.edge.relation && selectedGraphEvidence.value?.evidence === item.edge.evidence
    context.save()
    context.beginPath()
    context.moveTo(item.source.x, item.source.y)
    context.quadraticCurveTo(item.control.x, item.control.y, item.target.x, item.target.y)
    context.strokeStyle = isSelected ? `rgba(255, 213, 116, ${Math.min(.98, alpha + .22)})` : `rgba(174, 232, 211, ${alpha})`
    context.lineWidth = isSelected ? 2.8 : 1.2 + Math.max(0, item.depth) * 0.7
    if (Number(item.edge.confidence || 0) < 0.7) context.setLineDash([5, 5])
    context.stroke()
    context.setLineDash([])
    const arrowPoint = quadraticPoint(item.source, item.control, item.target, .78)
    drawGlobeArrow(context, item.control, arrowPoint, 'rgb(174, 232, 211)', alpha)
    drawGlobeRelationLabel(context, item, alpha, radius)
    context.restore()
  })
  projectedNodes.sort((left, right) => left.z - right.z).forEach(item => {
    const color = globeNodeColors[item.node.type] || '#d6e7d1'
    const frontFactor = 0.58 + (item.z + 1) * 0.22
    const nodeRadius = 5 + Math.max(0, item.z) * 3.3
    context.save()
    context.globalAlpha = frontFactor
    context.beginPath()
    context.arc(item.x, item.y, nodeRadius + 5, 0, Math.PI * 2)
    context.fillStyle = color.replace(')', ', .14)').replace('rgb', 'rgba')
    context.fill()
    context.beginPath()
    context.arc(item.x, item.y, nodeRadius, 0, Math.PI * 2)
    context.fillStyle = color
    context.fill()
    context.strokeStyle = 'rgba(255, 253, 247, .8)'
    context.lineWidth = 1
    context.stroke()
    if (item.z > -0.25) {
      context.globalAlpha = Math.min(1, frontFactor + .28)
      context.fillStyle = '#fffdf2'
      context.font = `700 ${Math.max(10, Math.min(13, radius / 18))}px serif`
      context.textAlign = item.x > centerX ? 'left' : 'right'
      context.textBaseline = 'middle'
      context.fillText(String(item.node.name || '').slice(0, 7), item.x + (item.x > centerX ? nodeRadius + 7 : -nodeRadius - 7), item.y)
    }
    context.restore()
  })
  globeProjectedNodes = projectedNodes
  globeProjectedEdges = projectedEdges
}

function distanceToSegment (point, start, end) {
  const dx = end.x - start.x
  const dy = end.y - start.y
  const lengthSquared = dx * dx + dy * dy || 1
  const position = Math.max(0, Math.min(1, ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared))
  return Math.hypot(point.x - (start.x + dx * position), point.y - (start.y + dy * position))
}

function selectGlobeAt (event) {
  const bounds = activeGlobeCanvas?.getBoundingClientRect()
  if (!bounds) return
  const point = { x: event.clientX - bounds.left, y: event.clientY - bounds.top }
  const nodeMatch = [...globeProjectedNodes]
    .sort((left, right) => right.z - left.z)
    .find(item => Math.hypot(item.x - point.x, item.y - point.y) <= 18)
  if (nodeMatch) {
    selectGraphEvidence(nodeMatch.node, 'NODE')
    requestRelationshipGlobeRender()
    return
  }
  const edgeMatch = globeProjectedEdges
    .map(item => ({ item, distance: Math.min(distanceToSegment(point, item.source, item.control), distanceToSegment(point, item.control, item.target)) }))
    .sort((left, right) => left.distance - right.distance)[0]
  if (edgeMatch?.distance <= 10) {
    selectGraphEvidence(edgeMatch.item.edge, 'EDGE')
    requestRelationshipGlobeRender()
  }
}

function onGlobePointerDown (event) {
  event.currentTarget.setPointerCapture?.(event.pointerId)
  globeDrag = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, moved: false }
}

function onGlobePointerMove (event) {
  if (!globeDrag || globeDrag.pointerId !== event.pointerId) return
  const deltaX = event.clientX - globeDrag.x
  const deltaY = event.clientY - globeDrag.y
  if (Math.abs(deltaX) + Math.abs(deltaY) > 2) globeDrag.moved = true
  globeRotation.yaw += deltaX * 0.009
  globeRotation.pitch = Math.max(-1.18, Math.min(1.18, globeRotation.pitch + deltaY * 0.008))
  globeDrag.x = event.clientX
  globeDrag.y = event.clientY
  requestRelationshipGlobeRender()
}

function onGlobePointerUp (event) {
  if (!globeDrag || globeDrag.pointerId !== event.pointerId) return
  event.currentTarget.releasePointerCapture?.(event.pointerId)
  const moved = globeDrag.moved
  globeDrag = null
  if (!moved) selectGlobeAt(event)
}

function onGlobeWheel (event) {
  globeZoom = Math.max(0.62, Math.min(1.65, globeZoom - event.deltaY * 0.0012))
  requestRelationshipGlobeRender()
}

function resetRelationshipGlobe () {
  globeRotation.pitch = -0.18
  globeRotation.yaw = -0.72
  globeZoom = 1
  requestRelationshipGlobeRender()
}

watch([visibleGraphNodes, visibleGraphEdges, insightMode, showRelationshipCanvas], () => nextTick(requestRelationshipGlobeRender), { deep: true })

async function load() {
  pageLoading.value = true
  const coreResults = await Promise.allSettled([apiListAgentSessions(), apiListAgentModels(), apiGetAgentCredits(), apiGetAgentPreferences()])
  const [sessionResult, modelResult, creditResult, preferenceResult] = coreResults
  const unavailable = []
  sessions.value = sessionResult.status === 'fulfilled' ? sessionResult.value : []
  models.value = modelResult.status === 'fulfilled' ? modelResult.value : []
  if (selectedModelConfigId.value && !models.value.some(model => String(model.id) === selectedModelConfigId.value)) selectedModelConfigId.value = ''
  credits.value = creditResult.status === 'fulfilled' ? creditResult.value : null
  if (preferenceResult.status === 'fulfilled') {
    preferences.value = preferenceResult.value
    preferenceGenres.value = preferenceResult.value.preferredGenres.join(', ')
    avoidedThemes.value = preferenceResult.value.avoidedThemes.join(', ')
  }
  ;[['会话', sessionResult], ['模型', modelResult], ['积分', creditResult], ['偏好', preferenceResult]].forEach(([name, result]) => {
    if (result.status === 'rejected') unavailable.push(name)
  })
  const optionalResults = await Promise.allSettled([apiGetAgentInfrastructure(), apiGetQuickRecommendations(), apiGetAgentReadingPlan(), apiGetAgentShelfGroups(), apiGetMyShelf(1, 50), apiGetMyLevel()])
  const [infrastructureResult, recommendationResult, planResult, shelfGroupResult, shelfResult, levelResult] = optionalResults
  infrastructure.value = infrastructureResult.status === 'fulfilled' ? infrastructureResult.value : null
  shelfRecommendations.value = recommendationResult.status === 'fulfilled' ? recommendationResult.value : []
  readingPlan.value = planResult.status === 'fulfilled' ? planResult.value : null
  shelfGroups.value = shelfGroupResult.status === 'fulfilled' ? shelfGroupResult.value : []
  shelfBooks.value = shelfResult.status === 'fulfilled' ? (shelfResult.value?.records || []) : []
  levelInfo.value = levelResult.status === 'fulfilled' ? levelResult.value : null
  ;[['运行状态', infrastructureResult], ['推荐', recommendationResult], ['阅读计划', planResult], ['书架视图', shelfGroupResult], ['书架作品', shelfResult]].forEach(([name, result]) => {
    if (result.status === 'rejected') unavailable.push(name)
  })
  loadNotice.value = unavailable.length ? `部分 Agent 能力暂不可用（${unavailable.join('、')}），其余功能仍可正常使用。` : ''
  const routeBookId = router.currentRoute.value.query.canonicalBookId
  const routeChapter = router.currentRoute.value.query.chapterIndex
  if (routeBookId) {
    insightBookId.value = String(routeBookId)
    chatReferenceBookId.value = String(routeBookId)
  }
  if (routeChapter !== undefined && routeChapter !== null && Number(routeChapter) >= 0) insightChapter.value = Number(routeChapter) + 1
  const requestedSessionId = router.currentRoute.value.query.sessionId
  const requestedSession = requestedSessionId && sessions.value.find(session => String(session.id) === String(requestedSessionId))
  if (requestedSession) await selectSession(requestedSession)
  else if (sessions.value.length) await selectSession(sessions.value[0])
  pageLoading.value = false
}
async function newSession() {
  const session = await apiCreateAgentSession({})
  sessions.value.unshift(session)
  await selectSession(session)
}

async function useStarterPrompt(prompt) {
  draft.value = prompt
  if (!activeSession.value) await newSession()
  await nextTick()
  await send()
}

function clearReadingContext() {
  insightBookId.value = ''
  insightChapter.value = 1
  chatReferenceBookId.value = ''
}
async function selectInsightBook() {
  const book = selectedInsightBook.value
  if (!book) return
  insightChapter.value = Math.max(1, Number(book.lastChapterIndex ?? 0) + 1)
  chatReferenceBookId.value = String(book.canonicalBookId)
  insightLoaded.value = false
  effectiveInsightChapter.value = null
  spoilersConfirmed.value = false
  insightError.value = ''
  buildForm.value.startChapter = 1
  buildForm.value.endChapter = ''
  await loadKnowledgeBuildPreparation()
}

async function loadKnowledgeBuildPreparation () {
  if (!insightBookId.value) return
  try {
    knowledgeBuild.value = await apiPrepareBookKnowledgeBuild(insightBookId.value, {
      startChapter: buildForm.value.startChapter || undefined,
      endChapter: buildForm.value.endChapter || undefined
    })
    buildForm.value.startChapter = knowledgeBuild.value.startChapter || 1
    buildForm.value.endChapter = knowledgeBuild.value.endChapter || knowledgeBuild.value.totalChapters || 1
  } catch (_) { knowledgeBuild.value = null }
}

function queueBuildEstimate() {
  clearTimeout(buildEstimateTimer)
  buildEstimateTimer = setTimeout(() => loadKnowledgeBuildPreparation(), 260)
}

async function loadBuildTasks () {
  try {
    buildTasks.value = await apiGetBookKnowledgeTasks()
    const active = buildTasks.value.some(task => ['QUEUED', 'RUNNING'].includes(task.status))
    clearTimeout(taskPollTimer)
    if (active) taskPollTimer = setTimeout(loadBuildTasks, 2000)
    } catch (_) { /* Task center should not interrupt reading features. */ }
  }

  function canDeleteBuildTask (task) {
    return ['COMPLETED', 'FAILED'].includes(task?.status)
  }

  async function deleteBuildTask (task) {
    if (!canDeleteBuildTask(task)) return
    if (!window.confirm('删除这条构建记录？知识图谱数据不会被删除。')) return
    try {
      await apiDeleteBookKnowledgeTask(task.id)
      toast.success('构建记录已删除')
      await loadBuildTasks()
    } catch (error) {
      toast.error(error.message || '删除构建记录失败')
    }
  }

  function openKnowledgeBuildDialog () {
  if (!knowledgeBuild.value) return loadKnowledgeBuildPreparation().then(() => { showKnowledgeBuildDialog.value = true })
  buildForm.value.startChapter = knowledgeBuild.value.startChapter || 1
  buildForm.value.endChapter = knowledgeBuild.value.endChapter || knowledgeBuild.value.totalChapters || 1
  showKnowledgeBuildDialog.value = true
}

function taskBookTitle (task) {
  return task?.bookTitle || '未知作品'
}

function taskProgressPercent (task) {
  if (task?.status === 'COMPLETED') return 100
  const total = Math.max(1, Number(task?.totalChapters || 0))
  const completed = Math.max(0, Number(task?.completedChapters || 0))
  return Math.min(100, Math.round(completed / total * 100))
}

async function startKnowledgeBuild () {
  if (!insightBookId.value || buildSubmitting.value) return
  buildSubmitting.value = true
  try {
    const dto = { ...buildForm.value, startChapter: Number(buildForm.value.startChapter), endChapter: Number(buildForm.value.endChapter), modelConfigId: buildForm.value.modelMode === 'BYOK' ? Number(buildForm.value.modelConfigId) : null }
    await apiStartBookKnowledgeBuild(insightBookId.value, dto)
    showKnowledgeBuildDialog.value = false
    toast.success('知识图谱任务已创建，可在任务中心查看实时进度。')
    await loadKnowledgeBuildPreparation()
    await loadBuildTasks()
  } catch (error) { toast.error(error.message) } finally { buildSubmitting.value = false }
}
function normalizeInsightChapter() {
  const max = Math.max(1, Number(selectedInsightBook.value?.totalChapters || insightChapter.value || 1))
  insightChapter.value = Math.min(max, Math.max(1, Number(insightChapter.value) || 1))
  spoilersConfirmed.value = false
}
function adjustInsightChapter(delta) {
  insightChapter.value = Number(insightChapter.value || 1) + delta
  normalizeInsightChapter()
}
function selectChatBook() {
  const book = chatReferenceBook.value
  if (!book) return
  insightBookId.value = String(book.canonicalBookId)
  insightChapter.value = Math.max(1, Number(book.lastChapterIndex ?? 0) + 1)
  insightLoaded.value = false
  effectiveInsightChapter.value = null
  spoilersConfirmed.value = false
  insightError.value = ''
}
function applyCurrentReadingContext() {
  const routeBookId = router.currentRoute.value.query.canonicalBookId
  const routeChapter = router.currentRoute.value.query.chapterIndex
  if (routeBookId) {
    insightBookId.value = String(routeBookId)
    insightChapter.value = Math.max(1, Number(routeChapter ?? 0) + 1)
    chatReferenceBookId.value = String(routeBookId)
    return
  }
  if (!chatReferenceBookId.value && usableShelfBooks.value.length) {
    chatReferenceBookId.value = String(usableShelfBooks.value[0].canonicalBookId)
  }
  selectChatBook()
}
async function selectSession(session) {
  cancelSessionTitle()
  activeSession.value = session
  messages.value = await apiGetAgentMessages(session.id)
  restoreSessionContext(session)
}
function startSessionTitleEdit() {
  if (!activeSession.value || sending.value) return
  sessionTitleDraft.value = activeSession.value.title || '未命名对话'
  editingSessionTitle.value = true
  nextTick(() => sessionTitleInput.value?.select())
}
function cancelSessionTitle() {
  editingSessionTitle.value = false
  sessionTitleDraft.value = ''
}
async function saveSessionTitle() {
  const title = sessionTitleDraft.value.trim()
  if (!editingSessionTitle.value) return
  if (!title) return cancelSessionTitle()
  if (!activeSession.value || title === activeSession.value.title) return cancelSessionTitle()
  try {
    const updated = await apiRenameAgentSession(activeSession.value.id, { title })
    activeSession.value = updated
    sessions.value = sessions.value.map(session => session.id === updated.id ? updated : session)
    toast.success('对话标题已更新')
    cancelSessionTitle()
  } catch (error) { toast.error(error.message || '更新对话标题失败') }
}

function restoreSessionContext(session) {
  if (!session?.context) return
  try {
    const context = typeof session.context === 'string' ? JSON.parse(session.context) : session.context
    if (context?.canonicalBookId) insightBookId.value = String(context.canonicalBookId)
    if (context?.canonicalBookId) chatReferenceBookId.value = String(context.canonicalBookId)
    if (context?.currentChapter !== undefined && Number(context.currentChapter) >= 0) insightChapter.value = Number(context.currentChapter) + 1
  } catch (_) {
    // Historical sessions may contain legacy opaque page context.
  }
}
async function searchSessions() {
  sessions.value = sessionSearch.value.trim() ? await apiSearchAgentSessions(sessionSearch.value.trim()) : await apiListAgentSessions()
}
async function exportConversation() {
  if (!activeSession.value) return
  try {
    const data = await apiExportAgentSession(activeSession.value.id)
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
    const link = document.createElement('a')
    link.href = url; link.download = `agent-session-${activeSession.value.id}.json`; link.click()
    URL.revokeObjectURL(url)
  } catch (error) { toast.error(error.message) }
}
async function deleteConversation() {
  if (!activeSession.value || !window.confirm('确定删除这段对话及其消息吗？')) return
  try {
    const deletedId = activeSession.value.id
    await apiDeleteAgentSession(deletedId)
    sessions.value = sessions.value.filter(session => session.id !== deletedId)
    activeSession.value = null
    messages.value = []
    if (sessions.value.length) await selectSession(sessions.value[0])
    toast.success('对话已删除')
  } catch (error) { toast.error(error.message) }
}
async function send(requestContext = {}, contentOverride = '') {
  const content = (contentOverride || draft.value).trim()
  if (!activeSession.value || !content || sending.value) return
  if (!contentOverride) draft.value = ''
  const localMessageId = Date.now()
  if (!requestContext.reuseExistingUserMessage) messages.value.push({ id: `local-user-${localMessageId}`, role: 'USER', content })
  messages.value.push({ id: `local-assistant-${localMessageId}`, role: 'ASSISTANT', content: '' })
  sending.value = true
  let answer = ''
  try {
    const modelRequest = selectedModelConfigId.value
      ? { mode: 'BYOK', modelConfigId: Number(selectedModelConfigId.value) }
      : { mode: 'PLATFORM' }
    const readingContext = insightBookId.value && insightChapter.value >= 1
      ? { canonicalBookId: String(insightBookId.value), currentChapter: Number(insightChapter.value) - 1, currentBookTitle: selectedInsightBook.value?.bookName || chatReferenceBook.value?.bookName }
      : {}
    await streamAgentMessage(activeSession.value.id, { content, ...modelRequest, ...readingContext, ...requestContext }, {
      onDelta: (delta) => { answer += delta; messages.value[messages.value.length - 1].content = answer },
      onRecommendations: (data) => { if (Array.isArray(data)) shelfRecommendations.value = data },
      onDone: (reply) => { messages.value[messages.value.length - 1].citations = reply?.citations || [] }
    })
    credits.value = await apiGetAgentCredits()
  } catch (error) { messages.value[messages.value.length - 1].content = error.message }
  finally { sending.value = false }
}
function startMessageEdit(message) {
  if (message?.role !== 'USER' || sending.value) return
  editingMessageId.value = message.id
  editingMessageContent.value = message.content || ''
}
function cancelMessageEdit() {
  editingMessageId.value = null
  editingMessageContent.value = ''
}
async function saveMessageEdit(message) {
  const content = editingMessageContent.value.trim()
  if (!activeSession.value || !message?.id || !content || sending.value) return
  try {
    await apiUpdateAgentMessage(activeSession.value.id, message.id, { content })
    const index = messages.value.findIndex(item => item.id === message.id)
    if (index < 0) return
    messages.value = messages.value.slice(0, index + 1)
    messages.value[index] = { ...messages.value[index], content }
    cancelMessageEdit()
    await send({ reuseExistingUserMessage: true }, content)
  } catch (error) { toast.error(error.message || '编辑消息失败') }
}
async function saveModel() {
  try { const saved = await apiSaveAgentModel(modelForm.value); modelForm.value.apiKey = ''; models.value = await apiListAgentModels(); selectedModelConfigId.value = String(saved.id); toast.success('模型 Key 已加密保存，并已用于后续对话'); showActionNotice('success', '模型已加密保存'); showActionNotice('success', '模型已加密保存') } catch (error) { toast.error(error.message) }
}
async function removeModel(id) {
  try {
    await apiDeleteAgentModel(id)
    models.value = models.value.filter((item) => item.id !== id)
    if (selectedModelConfigId.value === String(id)) selectedModelConfigId.value = ''
    toast.success('模型已删除')
  } catch (error) {
    toast.error(error.message || '删除模型失败')
  }
}
async function testModel(id) {
  testingModelId.value = id
  try { await apiTestAgentModel(id); toast.success('模型连接测试成功') } catch (error) { toast.error(error.message || '模型连接测试失败') } finally { testingModelId.value = null }
}
async function toggleModel(model) {
  try {
    const updated = await apiSetAgentModelEnabled(model.id, !model.enabled)
    models.value = models.value.map(item => item.id === updated.id ? updated : item)
    if (!updated.enabled && selectedModelConfigId.value === String(updated.id)) selectedModelConfigId.value = ''
    toast.success(updated.enabled ? '模型已启用' : '模型已停用')
  } catch (error) { toast.error(error.message) }
}
function splitPreferences(value) { return value.split(',').map(item => item.trim()).filter(Boolean) }
async function savePreferences() {
  try {
    preferences.value = await apiSaveAgentPreferences({ ...preferences.value, preferredGenres: splitPreferences(preferenceGenres.value), avoidedThemes: splitPreferences(avoidedThemes.value) })
    if (!preferences.value.retainConversations) { sessions.value = []; activeSession.value = null; messages.value = [] }
    toast.success('阅读偏好已保存'); showActionNotice('success', '设置已保存'); showActionNotice('success', '设置已保存')
  } catch (error) { toast.error(error.message) }
}
async function erasePersonalData() {
  if (!window.confirm('确定清除 Agent 偏好设置和选中的个人记录吗？此操作无法撤销。')) return
  try {
    await apiEraseAgentPersonalData(eraseConversations.value)
    preferences.value = { preferredGenres: [], avoidedThemes: [], spoilerLevel: 'STRICT', personalizationEnabled: true, retainConversations: true }
    preferenceGenres.value = ''; avoidedThemes.value = ''
    if (eraseConversations.value) { sessions.value = []; activeSession.value = null; messages.value = [] }
    toast.success('Agent 数据已清除')
  } catch (error) { toast.error(error.message) }
}
async function confirmAddToShelf (item) {
  if (!item?.canonicalBookId || !window.confirm(`确定将《${item.title}》加入书架吗？`)) return
  try {
    const detail = await apiGetAgentReaderLink(item.canonicalBookId)
    if (!detail?.sourceId || !detail?.sourceBookUrl) throw new Error('这部作品暂时没有可用书源。')
    await apiAddToShelf({ canonicalBookId: item.canonicalBookId, sourceId: detail.sourceId, bookName: detail.title || item.title, author: detail.author, coverUrl: detail.coverUrl, bookUrl: detail.sourceBookUrl })
    await saveRecommendationFeedback(item, 'ADD_TO_SHELF')
    toast.success('已加入书架')
  } catch (error) { toast.error(error.message) }
}
async function openRecommendedBook (item) {
  try {
    const detail = await apiGetAgentReaderLink(item.canonicalBookId)
    if (!detail?.sourceId || !detail?.sourceBookUrl) throw new Error('这部作品暂时没有可用书源。')
    router.push({ name: 'Reader', query: { sourceId: detail.sourceId, bookUrl: detail.sourceBookUrl, bookName: detail.title || item.title, author: detail.author, coverUrl: detail.coverUrl, intro: detail.summary, canonicalBookId: item.canonicalBookId } })
  } catch (error) { toast.error(error.message) }
}
function citationItems(message) {
  return Array.isArray(message.citations) ? message.citations : []
}
function isInterviewResponse(content) { return typeof content === 'string' && content.includes('【原文事实】') && content.includes('【基于事实的推断】') && content.includes('【不足以判断】') }
async function openCitation(citation) {
  try {
    const detail = await apiGetAgentReaderLink(citation.canonicalBookId)
    if (!detail?.sourceId || !detail?.sourceBookUrl) { toast.error('该引用暂时没有可用书源。'); return }
    router.push({ name: 'Reader', query: { sourceId: detail.sourceId, bookUrl: detail.sourceBookUrl, bookName: detail.title, author: detail.author, coverUrl: detail.coverUrl, intro: detail.summary, canonicalBookId: citation.canonicalBookId, chapterIndex: citation.chapterIndex } })
  } catch (error) { toast.error(error.message) }
}
function timelineChapter (item) {
  const match = /^第(\d+)章/.exec(item || '')
  return match ? Math.max(0, Number(match[1]) - 1) : Math.max(0, Number(insightChapter.value) - 1)
}
async function openInsightChapter (chapterIndex) {
  try {
    const canonicalBookId = String(insightBookId.value)
    const detail = await apiGetAgentReaderLink(canonicalBookId)
    if (!detail?.sourceId || !detail?.sourceBookUrl) { toast.error('这条依据暂时没有可用书源。'); return }
    router.push({ name: 'Reader', query: { sourceId: detail.sourceId, bookUrl: detail.sourceBookUrl, bookName: detail.title, author: detail.author, coverUrl: detail.coverUrl, intro: detail.summary, canonicalBookId, chapterIndex: Math.max(0, Number(chapterIndex) || 0) } })
  } catch (error) { toast.error(error.message) }
}
function selectGraphEvidence (item, kind) {
  selectedGraphEvidence.value = { label: kind === 'NODE' ? `${item.name} / ${graphTypeLabel(item.type)}` : item.relation, chapter: item.firstChapter || 0, confidence: item.confidence, evidence: item.evidence }
}
function eventLinkCount(eventId) { return visibleReadingMapLinks.value.filter(link => link.source === eventId || link.target === eventId).length }
function mapNextLink (eventId) { return visibleReadingMapLinks.value.find(link => String(link.source) === String(eventId)) || null }
function mapConnectorLabel (sourceId, targetId) {
  const direct = visibleReadingMapLinks.value.find(link => String(link.source) === String(sourceId) && String(link.target) === String(targetId))
  return direct?.relation || '故事推进'
}
async function startCharacterInterview(node) {
  if (!insightBookId.value || insightChapter.value < 1) return
  if (!activeSession.value) await newSession()
  selectTab('chats')
  draft.value = `请以${node.name}的第一人称进行角色访谈。只使用我已经读过的章节，不要透露后续剧情，也不要编造事实。先说说此刻对你最重要的事情。`
  await send({ canonicalBookId: String(insightBookId.value), currentChapter: Number(insightChapter.value) - 1, interviewCharacter: node.name })
}
async function saveRecommendationFeedback(item, action) {
  try {
    await apiSaveRecommendationFeedback({ canonicalBookId: String(item.canonicalBookId), action })
    shelfRecommendations.value = await apiGetQuickRecommendations()
    toast.success(action === 'LIKE' ? '推荐偏好已记录' : '已暂不展示这条推荐')
  } catch (error) { toast.error(error.message) }
}
async function saveShelfGroup(item, groupCode) {
  try {
    await apiSaveAgentShelfGroup({ canonicalBookId: String(item.canonicalBookId), groupCode })
    shelfGroups.value = await apiGetAgentShelfGroups()
    toast.success(groupCode === 'AUTO' ? '已恢复自动分组' : '书架分组已更新')
  } catch (error) { toast.error(error.message) }
}
async function loadInsights() {
  if (!insightBookId.value || insightChapter.value < 1) return
  // Ask for explicit consent before any downstream request can include unread chapters.
  if (insightChapter.value > safeInsightChapter.value && !spoilersConfirmed.value) {
    pendingSpoilerChapter.value = insightChapter.value
    showSpoilerConfirm.value = true
    return
  }
  // READY only means that some chapters were indexed. Re-check the requested range every time.
  buildForm.value.startChapter = 1
  buildForm.value.endChapter = insightChapter.value
  await loadKnowledgeBuildPreparation()
  if (knowledgeBuild.value?.requiresBuild !== false) {
    showKnowledgeBuildDialog.value = true
    return
  }
  insightLoading.value = true
  insightError.value = ''
  try {
    const chapter = insightChapter.value - 1
    const results = await Promise.allSettled([
      apiGetAgentGraph(insightBookId.value, chapter, spoilersConfirmed.value),
      apiGetAgentClues(insightBookId.value, chapter, spoilersConfirmed.value),
      apiGetAgentTimeline(insightBookId.value, chapter, spoilersConfirmed.value),
      apiGetAgentReadingMap(insightBookId.value, chapter, spoilersConfirmed.value),
      apiGetSimilarBooks(insightBookId.value, chapter, spoilersConfirmed.value),
      apiGetPlotCapsule(insightBookId.value, chapter, spoilersConfirmed.value)
    ])
    const [graphResult, clueResult, timelineResult, readingMapResult, similarResult, capsuleResult] = results
    const unavailable = results.filter(result => result.status === 'rejected').length
    graph.value = graphResult.status === 'fulfilled' ? graphResult.value : { nodes: [], edges: [] }
    graphTypeFilter.value = 'ALL'
    selectedGraphEvidence.value = null
    clues.value = clueResult.status === 'fulfilled' ? clueResult.value : []
    timeline.value = timelineResult.status === 'fulfilled' ? timelineResult.value : []
    readingMap.value = readingMapResult.status === 'fulfilled' ? readingMapResult.value : { events: [], links: [] }
    readingMapBranch.value = 'ALL'
    similarBooks.value = similarResult.status === 'fulfilled' ? similarResult.value : []
    capsule.value = capsuleResult.status === 'fulfilled' ? capsuleResult.value : null
    const throughChapter = Number(capsule.value?.throughChapter)
    effectiveInsightChapter.value = Number.isInteger(throughChapter) && throughChapter >= 0 ? throughChapter + 1 : null
    insightLoaded.value = true
    if (unavailable === results.length) {
      insightError.value = 'Agent 洞察接口均未返回结果。请确认网关与 Agent 的共享密钥一致，并检查这本书是否已经完成 LightRAG 索引。'
    } else if (unavailable) {
      toast.error(`${unavailable} 项洞察暂不可用，已展示其余阅读安全结果。`)
    }
  } catch (error) {
    insightError.value = error.message
    toast.error(error.message)
  } finally {
    insightLoading.value = false
  }
}
function cancelSpoilerAnalysis () {
  showSpoilerConfirm.value = false
  spoilersConfirmed.value = false
  insightChapter.value = safeInsightChapter.value
  pendingSpoilerChapter.value = null
}
function confirmSpoilerAnalysis () {
  spoilersConfirmed.value = true
  showSpoilerConfirm.value = false
  pendingSpoilerChapter.value = null
  loadInsights()
}
onMounted(() => {
  load()
  loadBuildTasks()
  window.addEventListener('resize', requestRelationshipGlobeRender)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', requestRelationshipGlobeRender)
  if (globeRenderFrame) window.cancelAnimationFrame(globeRenderFrame)
  clearTimeout(taskPollTimer)
  clearTimeout(buildEstimateTimer)
})
</script>

<style scoped>
.agent-center { background: radial-gradient(circle at 10% 0%, var(--gold-3), transparent 28%), var(--paper-1); }
.agent-load-notice { margin: -12px 0 var(--space-5); padding: 9px 12px; border-left: 3px solid var(--gold-0); border-radius: var(--radius-sm); background: var(--paper-2); color: var(--ink-3); font-size: .82rem; }
.agent-status-strip { display:flex; flex-wrap:wrap; align-items:center; gap:8px; margin:-12px 0 var(--space-6); color:var(--ink-3); font-size:.72rem; }
.agent-status-strip span { display:inline-flex; align-items:center; gap:5px; padding:5px 9px; border:1px solid var(--paper-3); border-radius:99px; background:var(--paper-0); }
.agent-status-strip button { margin-left:auto; border:0; border-bottom:1px solid var(--gold-1); padding:4px 0; background:transparent; color:var(--gold-0); cursor:pointer; font:inherit; }
.status-dot { width:7px; height:7px; border-radius:50%; background:var(--ink-4); }.status-dot.online { background:#4f9a70; box-shadow:0 0 0 3px rgba(79,154,112,.14); }.status-dot.pending { background:var(--gold-0); }
.agent-page-loading { display:flex; align-items:center; justify-content:center; gap:10px; min-height:130px; color:var(--ink-3); }.loading-spinner { width:16px; height:16px; border:2px solid var(--paper-3); border-top-color:var(--gold-0); border-radius:50%; animation:agent-spin .8s linear infinite; }@keyframes agent-spin { to { transform:rotate(360deg); } }
.agent-hero { display: flex; justify-content: space-between; align-items: end; gap: var(--space-8); margin-bottom: var(--space-8); }
.agent-eyebrow { color: var(--gold-0); font-size: .7rem; font-weight: 700; letter-spacing: .14em; }
.agent-hero h1 { margin: var(--space-2) 0; font-size: clamp(2rem, 4vw, 3.5rem); }
.credit-card { min-width: 200px; padding: var(--space-5); border-radius: var(--radius-lg); color: var(--paper-0); background: var(--ink-0); }
.credit-card span,.credit-card small { display: block; opacity: .75; font-size: .75rem; }.credit-card strong { display:block; margin: 3px 0; font-family: var(--font-serif); font-size: 2.2rem; color: var(--gold-2); }
.agent-tabs { display: flex; gap: var(--space-2); border-bottom: 1px solid var(--paper-3); margin-bottom: var(--space-6); overflow-x: auto; }.agent-tabs button { border: 0; padding: var(--space-3) var(--space-4); background: transparent; color: var(--ink-3); cursor: pointer; white-space: nowrap; }.agent-tabs button.active { color: var(--ink-0); border-bottom: 2px solid var(--gold-0); }
.agent-workbench { display: grid; grid-template-columns: 240px 1fr; gap: var(--space-4); min-height: 560px; }.session-list { padding: var(--space-3); display: flex; flex-direction: column; gap: 6px; }.session-list button { border: 0; background: transparent; text-align: left; padding: 10px; border-radius: var(--radius-sm); color: var(--ink-2); cursor: pointer; }.session-list button.selected { background: var(--gold-3); color: var(--ink-0); }.session-list .new-session { background: var(--ink-0); color: var(--paper-0); text-align:center; }.session-search { width:100%; box-sizing:border-box; border:1px solid var(--paper-3); border-radius:var(--radius-sm); padding:8px; background:var(--paper-0); font:inherit; }.session-empty { padding: 10px; color: var(--ink-4); font-size: .78rem; line-height: 1.5; }.chat-header { display:flex; justify-content:space-between; align-items:center; gap:var(--space-3); padding:var(--space-4) var(--space-5); border-bottom:1px solid var(--paper-3); }.chat-header strong { display:block; margin-top:4px; color:var(--ink-0); }.chat-actions { display:flex; align-items:center; justify-content:flex-end; gap:6px; }.context-chip { padding:5px 8px; border-radius:99px; background:var(--gold-3); color:var(--ink-2); font-size:.68rem; }
.chat-pane { display:flex; flex-direction:column; padding:0; overflow:hidden; }.chat-history { flex:1; min-height:440px; padding:var(--space-5); overflow:auto; display:flex; flex-direction:column; gap:var(--space-3); }.chat-welcome { margin:auto; max-width:520px; padding:var(--space-6); text-align:center; color:var(--ink-2); }.welcome-mark { display:grid; place-items:center; width:48px; height:48px; margin:0 auto var(--space-3); border-radius:16px 16px 16px 4px; color:var(--paper-0); background:var(--ink-0); font-family:var(--font-serif); font-size:1.5rem; }.chat-welcome h2 { margin:0 0 8px; color:var(--ink-0); }.chat-welcome p { margin:0 auto var(--space-4); max-width:420px; line-height:1.6; font-size:.86rem; }.starter-prompts { display:flex; flex-wrap:wrap; justify-content:center; gap:8px; }.starter-prompts button { border:1px solid var(--paper-3); border-radius:99px; padding:7px 10px; background:var(--paper-0); color:var(--ink-2); cursor:pointer; font:inherit; font-size:.75rem; }.starter-prompts button:hover { border-color:var(--gold-1); color:var(--gold-0); }.center-message { max-width:78%; padding:12px; border-radius:var(--radius-md); white-space:pre-wrap; }.center-message small { display:block; margin-top:8px; color:var(--ink-4); font-size:.72rem; white-space:normal; }.citation-list { margin-top:8px; padding-top:6px; border-top:1px solid var(--paper-3); }.center-message.user { align-self:flex-end; background:var(--ink-0); color:var(--paper-0); }.center-message.assistant { background:var(--paper-2); }.center-input { position:relative; display:flex; flex-wrap:wrap; gap:var(--space-3); padding:var(--space-4); border-top:1px solid var(--paper-3); }.context-bar { flex:0 0 100%; padding:7px 9px; border-radius:var(--radius-sm); background:var(--gold-3); color:var(--ink-2); font-size:.72rem; }.context-bar button { float:right; border:0; background:transparent; color:var(--gold-0); cursor:pointer; font:inherit; }.chat-tools { display:flex; flex:0 0 100%; flex-wrap:wrap; align-items:center; gap:6px; }.chat-tool,.plugin-action { border:1px solid var(--paper-3); border-radius:99px; padding:5px 9px; background:var(--paper-0); color:var(--ink-3); cursor:pointer; font:inherit; font-size:.7rem; }.chat-tool.active { border-color:var(--gold-1); background:var(--gold-3); color:var(--gold-0); }.chat-reference { color:var(--sage-0); font-size:.72rem; }.chat-plugin-panel { display:flex; flex:0 0 100%; flex-wrap:wrap; align-items:center; gap:7px; padding:9px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-1); }.chat-plugin-panel label { display:flex; align-items:center; gap:6px; color:var(--ink-3); font-size:.72rem; }.chat-plugin-panel select { max-width:250px; padding:6px 8px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); color:var(--ink-1); font:inherit; font-size:.72rem; }.plugin-action { padding:4px 8px; }.center-input textarea { flex:1; min-width:180px; padding:10px; border:1px solid var(--paper-3); border-radius:var(--radius-md); font:inherit; }.chat-model-select { display:flex; flex:0 0 160px; flex-direction:column; gap:3px; color:var(--ink-3); font-size:.68rem; }.chat-model-select select { min-width:0; padding:8px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); color:var(--ink-1); font:inherit; font-size:.75rem; }.empty-state { display:grid; place-items:center; min-height:500px; color:var(--ink-4); }
.citation-link { display:block; width:100%; border:0; padding:4px 0; background:transparent; color:var(--sage-0); text-align:left; font:inherit; font-size:.72rem; cursor:pointer; }.citation-link:hover { text-decoration:underline; }.interview-contract { margin-top:8px; color:var(--sage-0); font-size:.68rem; }
.model-layout { display:grid; grid-template-columns:1fr 1fr; gap:var(--space-4); }.model-intro { grid-row:span 2; }.model-intro ul { margin:var(--space-4) 0 0 var(--space-5); color:var(--ink-2); }.model-form { display:flex; flex-direction:column; gap:var(--space-3); }.model-form label { display:flex; flex-direction:column; gap:6px; font-size:.85rem; color:var(--ink-2); }.model-form input,.model-form select { padding:10px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); font:inherit; }.saved-model { display:flex; justify-content:space-between; align-items:center; gap:var(--space-3); padding:var(--space-3) 0; border-bottom:1px solid var(--paper-3); }.saved-model small { display:block; color:var(--ink-4); margin-top:3px; }.model-actions { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:6px; }
.insights-panel { display:grid; gap:var(--space-4); }.insight-query { display:grid; grid-template-columns:1fr 180px 180px auto; gap:var(--space-3); align-items:end; }.insight-query-copy { min-width:0; }.insight-query h2 { margin:var(--space-2) 0; }.insight-query label { display:flex; flex-direction:column; gap:6px; font-size:.82rem; }.insight-query input,.insight-query select { padding:10px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); font:inherit; background:var(--paper-0); }.field-hint { color:var(--ink-4)!important; font-size:.68rem; }.insight-steps { display:flex; flex-wrap:wrap; gap:8px; margin-top:var(--space-3); }.insight-steps span { display:flex; align-items:center; gap:5px; padding:5px 8px; border-radius:99px; background:var(--paper-1); color:var(--ink-3); font-size:.68rem; }.insight-steps b { color:var(--gold-0); }.insight-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:var(--space-4); }.insight-card span { color:var(--gold-0); font-family:var(--font-serif); font-size:1.25rem; }.insight-card h2 { margin:var(--space-2) 0; }.insight-card small { color:var(--sage-0); }.insight-card ul { margin:var(--space-3) 0; padding-left:var(--space-5); color:var(--ink-2); font-size:.84rem; }.insight-card li { margin-bottom:8px; }.clue-status { margin-left:4px; color:var(--gold-0); font-size:.72rem; font-style:normal; }.relationship-map { width:100%; height:auto; margin:var(--space-3) 0 0; border-radius:var(--radius-md); background:linear-gradient(135deg, var(--paper-1), var(--gold-3)); }.relationship-map line { stroke:var(--sage-0); stroke-width:1.5; opacity:.6; }.relationship-map line.tentative { stroke-dasharray:5 4; opacity:.35; }.relationship-map circle.character { fill:var(--ink-0); }.relationship-map circle.other { fill:var(--sage-0); }.relationship-map text { fill:var(--paper-0); font-size:10px; text-anchor:middle; font-family:var(--font-serif); }.character-interview,.recommendation-actions button { margin-left:8px; border:1px solid var(--gold-1); border-radius:99px; padding:2px 7px; background:transparent; color:var(--gold-0); font:inherit; font-size:.7rem; cursor:pointer; }.recommendation-actions { display:inline-flex; margin-top:5px; }.recommendation-actions button { margin-left:0; margin-right:5px; }
.insight-error { padding:var(--space-6); border-left:3px solid #b94a3a; }.insight-error strong { color:var(--ink-0); }.insight-error p { margin:8px 0 14px; color:var(--ink-3); }
.insight-boundary-note { margin:0; padding:10px 13px; border-left:3px solid var(--sage-0); border-radius:var(--radius-sm); background:rgba(184,214,125,.15); color:var(--ink-2); font-size:.8rem; line-height:1.6; }.insight-boundary-note.limited { border-left-color:var(--agent-coral); background:rgba(212,97,69,.10); }
.graph-tools { display:flex; flex-wrap:wrap; gap:5px; margin:8px 0; }.graph-tools button,.graph-card li button { border:1px solid var(--paper-3); border-radius:99px; padding:3px 7px; background:var(--paper-0); color:var(--ink-2); font:inherit; font-size:.68rem; cursor:pointer; }.graph-tools button.active { background:var(--ink-0); color:var(--paper-0); border-color:var(--ink-0); }.relationship-map line,.relationship-map g { cursor:pointer; }.graph-evidence { margin-top:10px; padding:9px; border-left:3px solid var(--gold-0); background:var(--paper-1); font-size:.78rem; }.graph-evidence b,.graph-evidence span { display:block; }.graph-evidence span { color:var(--ink-4); margin-top:3px; }.graph-evidence p { margin:6px 0 0; line-height:1.5; }
.reading-map-events { margin:var(--space-3) 0; padding-left:var(--space-5); }.reading-map-events li { padding:0 0 10px 6px; border-left:1px solid var(--paper-3); }.reading-map-events strong { color:var(--ink-1); font-size:.8rem; }.reading-map-events p { margin:5px 0; font-size:.76rem; line-height:1.5; }.reading-map-events small { color:var(--ink-4); font-size:.68rem; }.evidence-jump { margin:5px 0 0 6px; border:0; border-bottom:1px solid var(--gold-1); padding:1px 0; background:transparent; color:var(--ink-2); font:inherit; font-size:.68rem; cursor:pointer; }.relationship-map line { cursor:pointer; stroke-width:4px; stroke-opacity:.25; }.relationship-map line:hover { stroke-opacity:.8; }
.shelf-groups-card li { display:grid; grid-template-columns:1fr auto; gap:5px 10px; align-items:center; }.shelf-groups-card li small { grid-column:1 / -1; }.shelf-groups-card select { max-width:150px; padding:5px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); color:var(--ink-2); font:inherit; font-size:.7rem; }
.privacy-layout { display:grid; grid-template-columns:1fr 1fr; gap:var(--space-4); align-items:start; }.preference-form { display:flex; flex-direction:column; gap:var(--space-3); }.preference-form label { display:flex; flex-direction:column; gap:6px; font-size:.85rem; color:var(--ink-2); }.preference-form input,.preference-form select { padding:10px; border:1px solid var(--paper-3); border-radius:var(--radius-sm); background:var(--paper-0); font:inherit; }.switch-line { flex-direction:row!important; align-items:center; }.privacy-danger p { color:var(--ink-2); line-height:1.65; }.privacy-danger .btn { margin-top:var(--space-4); }
@media(max-width:700px){.agent-hero,.agent-workbench,.model-layout,.insight-query,.privacy-layout{display:flex;flex-direction:column}.credit-card{width:100%}.agent-workbench{min-height:unset}.session-list{max-height:180px}.insight-grid{grid-template-columns:1fr}.center-input{flex-direction:column}.chat-model-select{flex:auto}.insight-query>*{width:100%}}

/* Reading control room: a denser, editorial layout for an AI reading companion. */
.agent-center {
  --agent-ink: #102c32;
  --agent-ink-soft: #274950;
  --agent-coral: #d46145;
  --agent-sand: #f4efe5;
  --agent-lime: #b8d67d;
  position: relative;
  min-height: calc(100vh - 72px);
  overflow: hidden;
  background:
    radial-gradient(circle at 92% 2%, rgba(184,214,125,.34), transparent 19rem),
    radial-gradient(circle at 4% 23%, rgba(212,97,69,.13), transparent 26rem),
    repeating-linear-gradient(90deg, rgba(16,44,50,.025) 0 1px, transparent 1px 8px),
    var(--agent-sand);
}
.agent-center::before {
  content: 'READER INTELLIGENCE';
  position: absolute;
  top: 220px;
  right: -92px;
  z-index: 0;
  color: rgba(16,44,50,.035);
  font-family: var(--font-serif);
  font-size: clamp(4rem, 11vw, 10rem);
  font-weight: 700;
  letter-spacing: -.08em;
  white-space: nowrap;
  transform: rotate(90deg);
}
.agent-center .container { position: relative; z-index: 1; max-width: 1480px; padding-top: clamp(28px, 4vw, 62px); padding-bottom: 72px; }
.agent-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(235px, 310px);
  align-items: stretch;
  gap: 20px;
  margin: 0 0 18px;
  padding: clamp(26px, 4vw, 48px);
  border: 1px solid rgba(16,44,50,.12);
  border-radius: 28px;
  color: #f7f2e9;
  background: var(--agent-ink);
  box-shadow: 0 25px 55px rgba(16,44,50,.16);
}
.agent-hero::after { content: ''; position: absolute; inset: 12px; border: 1px solid rgba(247,242,233,.14); border-radius: 20px; pointer-events: none; }
.agent-hero > div:first-child { position: relative; z-index: 1; max-width: 780px; }
.agent-eyebrow { display: inline-flex; align-items: center; gap: 8px; color: var(--agent-lime); font-size: .66rem; font-weight: 800; letter-spacing: .19em; }
.agent-eyebrow::before { content: ''; display: inline-block; width: 20px; height: 1px; background: currentColor; }
.agent-hero h1 { margin: 12px 0 10px; color: #fffaf0; font-family: var(--font-serif); font-size: clamp(2.8rem, 6vw, 5.6rem); font-weight: 600; letter-spacing: -.07em; line-height: .92; }
.agent-hero p { max-width: 530px; margin: 0; color: rgba(247,242,233,.72); font-size: .93rem; line-height: 1.8; }
.credit-card { position: relative; z-index: 1; display: flex; flex-direction: column; justify-content: flex-end; min-width: 0; padding: 24px; border: 0; border-radius: 18px; color: var(--agent-ink); background: var(--agent-lime); }
.credit-card::before { content: '可用积分'; margin-bottom: auto; font-size: .62rem; font-weight: 800; letter-spacing: .17em; opacity: .66; }
.credit-card span { color: var(--agent-ink); font-size: .74rem; font-weight: 700; opacity: .75; }.credit-card strong { margin: 7px 0 4px; color: var(--agent-ink); font-family: var(--font-serif); font-size: 4rem; letter-spacing: -.08em; line-height: .9; }.credit-card small { color: var(--agent-ink); font-size: .68rem; line-height: 1.5; opacity: .68; }
.agent-load-notice { margin: 0 0 12px; padding: 12px 15px; border: 1px solid rgba(212,97,69,.27); border-left: 4px solid var(--agent-coral); border-radius: 12px; background: rgba(255,249,240,.78); color: var(--agent-ink); }
.agent-status-strip { margin: 0 0 19px; gap: 7px; }.agent-status-strip span { border-color: rgba(16,44,50,.12); padding: 6px 10px; background: rgba(255,253,247,.62); color: var(--agent-ink-soft); }.agent-status-strip button { color: var(--agent-coral); }.status-dot.online { background: var(--agent-lime); box-shadow: 0 0 0 3px rgba(184,214,125,.3); }
.agent-page-loading { min-height: 260px; border: 1px solid rgba(16,44,50,.1); border-radius: 24px; background: rgba(255,253,247,.75); color: var(--agent-ink); }.loading-spinner { border-color: rgba(16,44,50,.14); border-top-color: var(--agent-coral); }
.agent-tabs { display: inline-flex; max-width: 100%; margin: 0 0 20px; padding: 5px; border: 1px solid rgba(16,44,50,.12); border-radius: 14px; background: rgba(255,253,247,.64); backdrop-filter: blur(10px); }.agent-tabs button { position: relative; padding: 9px 14px; border: 0; border-radius: 10px; color: var(--agent-ink-soft); font-size: .8rem; font-weight: 700; }.agent-tabs button.active { border: 0; color: #fffaf0; background: var(--agent-ink); box-shadow: 0 5px 12px rgba(16,44,50,.16); }
.agent-workbench { grid-template-columns: 250px minmax(0, 1fr) 260px; gap: 16px; min-height: 670px; }.agent-workbench .card { border: 1px solid rgba(16,44,50,.11); border-radius: 20px; box-shadow: 0 12px 28px rgba(16,44,50,.055); }
.session-list { min-height: 0; padding: 14px; border: 0!important; color: rgba(247,242,233,.8); background: var(--agent-ink)!important; }.session-list-head { display: flex; align-items: center; justify-content: space-between; padding: 4px 3px 15px; color: var(--agent-lime); font-size: .66rem; font-weight: 800; letter-spacing: .16em; }.session-list .new-session { padding: 6px 9px; border: 1px solid rgba(184,214,125,.55); border-radius: 8px; background: transparent; color: var(--agent-lime); font-size: .66rem; font-weight: 800; letter-spacing: .04em; }.session-search-wrap { display: grid; gap: 5px; margin-bottom: 11px; color: rgba(247,242,233,.52); font-size: .62rem; letter-spacing: .1em; }.session-search { border: 1px solid rgba(247,242,233,.16); border-radius: 9px; padding: 9px 10px; color: #fffaf0; background: rgba(247,242,233,.07); }.session-search::placeholder { color: rgba(247,242,233,.36); }.session-scroll { display: flex; flex: 1; min-height: 0; flex-direction: column; gap: 3px; overflow: auto; }.session-list button:not(.new-session) { display: flex; align-items: center; gap: 9px; padding: 10px 8px; border-radius: 9px; color: rgba(247,242,233,.68); font-size: .78rem; line-height: 1.35; }.session-list button.selected { color: var(--agent-ink); background: var(--agent-lime); }.session-bullet { width: 6px; height: 6px; flex: 0 0 6px; border-radius: 50%; background: currentColor; opacity: .7; }.session-empty { color: rgba(247,242,233,.52); }.session-list-foot { display: flex; justify-content: space-between; padding: 13px 3px 1px; border-top: 1px solid rgba(247,242,233,.12); color: rgba(247,242,233,.42); font-size: .58rem; letter-spacing: .05em; }
.chat-pane { min-width: 0; border: 0!important; border-radius: 20px!important; background: rgba(255,253,247,.92)!important; }.chat-header { padding: 21px 24px; border-bottom: 1px solid rgba(16,44,50,.1); }.chat-title-block strong { margin: 5px 0 2px; font-family: var(--font-serif); font-size: 1.42rem; letter-spacing: -.03em; }.chat-title-block small { color: var(--ink-4); font-size: .72rem; }.chat-actions { gap: 7px; }.context-chip { border: 1px solid rgba(184,214,125,.8); padding: 6px 9px; background: rgba(184,214,125,.24); color: var(--agent-ink); font-size: .64rem; font-weight: 700; }.icon-action { border: 1px solid rgba(16,44,50,.13); border-radius: 8px; padding: 6px 8px; background: transparent; color: var(--agent-ink-soft); cursor: pointer; font: inherit; font-size: .68rem; }.icon-action:hover { border-color: var(--agent-ink); color: var(--agent-ink); }.icon-action.danger:hover { border-color: var(--agent-coral); color: var(--agent-coral); }
.chat-history { min-height: 420px; padding: 34px clamp(20px, 4vw, 48px); gap: 18px; background: linear-gradient(180deg, rgba(244,239,229,.44), transparent 38%); }.chat-welcome { display: grid; grid-template-columns: 75px minmax(0, 1fr); align-items: start; max-width: 680px; margin: auto; padding: 24px 0; text-align: left; }.welcome-mark { width: 58px; height: 58px; margin: 0; border-radius: 17px 17px 17px 4px; background: var(--agent-coral); box-shadow: 6px 6px 0 rgba(16,44,50,.1); font-size: 1.85rem; }.chat-welcome h2 { margin: 8px 0 10px; font-family: var(--font-serif); font-size: clamp(1.8rem, 4vw, 2.8rem); letter-spacing: -.06em; }.chat-welcome p { max-width: 500px; margin: 0 0 18px; font-size: .88rem; line-height: 1.8; }.starter-prompts { justify-content: flex-start; }.starter-prompts button { display: inline-flex; align-items: center; gap: 9px; border-color: rgba(16,44,50,.12); padding: 8px 10px; color: var(--agent-ink-soft); background: rgba(255,253,247,.72); }.starter-prompts button b { color: var(--agent-coral); font-size: 1rem; line-height: 1; }.starter-prompts button:hover { border-color: var(--agent-coral); color: var(--agent-ink); transform: translateY(-1px); }
.center-message { max-width: min(79%, 680px); padding: 13px 15px; border-radius: 15px 15px 15px 3px; line-height: 1.75; box-shadow: 0 5px 14px rgba(16,44,50,.04); }.center-message.assistant { border: 1px solid rgba(16,44,50,.09); background: #fffdf7; color: var(--agent-ink); }.center-message.user { border-radius: 15px 15px 3px 15px; background: var(--agent-ink); color: #fffaf0; }.citation-list { border-top-color: rgba(16,44,50,.1); }.citation-link { color: #54796d; font-size: .7rem; }.center-message.user .citation-link { color: var(--agent-lime); }
.center-input { gap: 10px; padding: 14px 18px 18px; border-top: 1px solid rgba(16,44,50,.11); background: #fffdf7; }.context-bar { display: flex; align-items: center; gap: 6px; padding: 8px 10px; border: 0; border-radius: 9px; background: rgba(184,214,125,.25); color: var(--agent-ink); }.context-orbit { width: 8px; height: 8px; border-radius: 50%; background: var(--agent-coral); box-shadow: 0 0 0 3px rgba(212,97,69,.13); }.context-bar b { font-weight: 800; }.context-bar button { margin-left: auto; float: none; color: var(--agent-coral); }.chat-tools { gap: 6px; }.chat-tool,.plugin-action { border-color: rgba(16,44,50,.12); padding: 6px 9px; color: var(--agent-ink-soft); background: #fffdf7; font-size: .67rem; }.chat-tool.primary-tool { color: #fffaf0; background: var(--agent-ink); }.chat-tool.primary-tool.active { border-color: var(--agent-ink); background: var(--agent-coral); }.chat-tool.protected { border-color: rgba(184,214,125,.75); color: #47612f; background: rgba(184,214,125,.21); cursor: default; }.chat-reference { margin-left: 2px; color: var(--agent-coral); font-size: .72rem; font-weight: 700; }.chat-plugin-panel { gap: 7px; border-color: rgba(16,44,50,.11); border-radius: 11px; padding: 10px; background: var(--agent-sand); }.chat-plugin-panel label { color: var(--agent-ink); font-weight: 700; }.chat-plugin-panel select { border-color: rgba(16,44,50,.14); background: #fffdf7; }.plugin-divider { width: 1px; height: 23px; background: rgba(16,44,50,.12); }.plugin-action:hover { border-color: var(--agent-coral); color: var(--agent-coral); }.center-input textarea { min-height: 62px; border-color: rgba(16,44,50,.14); border-radius: 12px; padding: 12px; color: var(--agent-ink); background: #fffefa; }.center-input textarea:focus { outline: 2px solid rgba(212,97,69,.18); border-color: var(--agent-coral); }.chat-model-select { flex-basis: 158px; justify-content: end; color: var(--agent-ink-soft); font-size: .62rem; font-weight: 700; letter-spacing: .05em; }.chat-model-select select { border-color: rgba(16,44,50,.14); background: #fffefa; color: var(--agent-ink); }.send-button { display: inline-flex; align-items: center; justify-content: center; gap: 9px; min-width: 112px; border: 0; border-radius: 12px; padding: 0 14px; color: #fffaf0; background: var(--agent-coral); box-shadow: 0 7px 14px rgba(212,97,69,.19); cursor: pointer; font: inherit; font-size: .76rem; font-weight: 800; }.send-button b { font-size: 1.1rem; }.send-button:disabled { opacity: .48; cursor: not-allowed; box-shadow: none; }
.workspace-notes { display: flex; flex-direction: column; gap: 16px; }.workspace-notes .card { padding: 19px; background: rgba(255,253,247,.78); }.note-index { color: var(--agent-coral); font-size: .58rem; font-weight: 800; letter-spacing: .13em; }.workspace-notes h3 { margin: 8px 0 13px; color: var(--agent-ink); font-family: var(--font-serif); font-size: 1.2rem; letter-spacing: -.03em; }.context-card strong { color: var(--agent-ink); font-size: .9rem; }.context-card p { margin: 5px 0 13px; color: var(--ink-4); font-size: .74rem; line-height: 1.6; }.reading-meter { height: 4px; overflow: hidden; border-radius: 99px; background: rgba(16,44,50,.12); }.reading-meter i { display: block; height: 100%; border-radius: inherit; background: var(--agent-coral); }.context-card small { display: block; margin-top: 8px; color: #54796d; font-size: .68rem; }.text-action { border: 0; padding: 0; background: transparent; color: var(--agent-coral); cursor: pointer; font: inherit; font-size: .74rem; font-weight: 800; }.shortcut-card { padding: 0!important; overflow: hidden; }.shortcut-card > .note-index,.shortcut-card > h3 { display: block; margin-left: 19px; margin-right: 19px; }.shortcut-card > .note-index { margin-top: 19px; }.shortcut-card button { display: grid; gap: 3px; width: 100%; border: 0; border-top: 1px solid rgba(16,44,50,.1); padding: 13px 19px; background: transparent; color: var(--agent-ink); text-align: left; cursor: pointer; font: inherit; }.shortcut-card button:hover { background: rgba(184,214,125,.18); }.shortcut-card button b { font-size: .76rem; }.shortcut-card button span { color: var(--ink-4); font-size: .67rem; }.privacy-note { margin-top: auto; padding: 0 5px; color: var(--agent-ink-soft); font-size: .65rem; line-height: 1.55; }.privacy-note .status-dot { display: inline-block; margin-right: 5px; vertical-align: middle; }
.model-layout,.privacy-layout { gap: 16px; }.model-layout .card,.privacy-layout .card,.insight-query,.insight-card { border: 1px solid rgba(16,44,50,.11); border-radius: 20px; box-shadow: 0 12px 28px rgba(16,44,50,.055); }.model-intro { position: relative; overflow: hidden; padding: 30px; color: #fffaf0; background: var(--agent-ink)!important; }.model-intro::after { content: 'BYOK'; position: absolute; right: -8px; bottom: -35px; color: rgba(184,214,125,.13); font-family: var(--font-serif); font-size: 8rem; font-weight: 700; letter-spacing: -.08em; }.model-intro > * { position: relative; z-index: 1; }.model-intro h2 { color: #fffaf0; font-family: var(--font-serif); font-size: 2.1rem; letter-spacing: -.06em; }.model-intro p,.model-intro ul { color: rgba(247,242,233,.72)!important; line-height: 1.75; }.model-form,.saved-model { background: rgba(255,253,247,.86); }.model-form { padding: 26px; }.model-form h2,.saved-models h2,.privacy-layout h2 { color: var(--agent-ink); font-family: var(--font-serif); letter-spacing: -.04em; }.model-form label,.preference-form label { color: var(--agent-ink-soft); font-weight: 700; }.model-form input,.model-form select,.preference-form input,.preference-form select { border-color: rgba(16,44,50,.14); background: #fffefa; }.saved-models { padding: 24px; background: rgba(255,253,247,.86); }.saved-model { border-bottom-color: rgba(16,44,50,.1); }.saved-model strong { color: var(--agent-ink); }.model-actions .btn { border-color: rgba(16,44,50,.16); color: var(--agent-ink-soft); }
.insights-panel { gap: 16px; }.insight-query { grid-template-columns: minmax(0, 1fr) 210px 190px auto; padding: 26px; background: var(--agent-ink); color: #fffaf0; }.insight-query-copy h2 { color: #fffaf0; font-family: var(--font-serif); font-size: 2.35rem; letter-spacing: -.06em; }.insight-query-copy p { color: rgba(247,242,233,.72); line-height: 1.65; }.insight-query label { color: rgba(247,242,233,.82); font-weight: 700; }.insight-query input,.insight-query select { border-color: rgba(247,242,233,.18); color: #fffaf0; background: rgba(247,242,233,.1); }.insight-query option { color: var(--agent-ink); }.field-hint { color: rgba(247,242,233,.58)!important; }.insight-steps span { background: rgba(247,242,233,.1); color: rgba(247,242,233,.82); }.insight-steps b { color: var(--agent-lime); }.insight-query .btn { align-self: end; min-height: 42px; border: 0; border-radius: 10px; background: var(--agent-lime); color: var(--agent-ink); font-weight: 800; }.insight-grid { grid-template-columns: repeat(3, minmax(0,1fr)); gap: 16px; }.insight-card { position: relative; min-height: 210px; padding: 22px; overflow: hidden; background: rgba(255,253,247,.86); }.insight-card::before { content: ''; position: absolute; right: -30px; top: -30px; width: 100px; height: 100px; border-radius: 50%; background: rgba(184,214,125,.18); }.insight-card > * { position: relative; z-index: 1; }.insight-card > span { color: var(--agent-coral); font-family: var(--font-serif); font-size: 1.9rem; letter-spacing: -.07em; }.insight-card h2 { color: var(--agent-ink); font-family: var(--font-serif); font-size: 1.38rem; letter-spacing: -.045em; }.insight-card p,.insight-card li { color: var(--agent-ink-soft); line-height: 1.6; }.insight-card small { color: #54796d; }.graph-card,.reading-map-card { grid-column: span 2; }.relationship-map { border: 1px solid rgba(16,44,50,.1); background: linear-gradient(135deg, #f8f3e7, #dceac4); }.graph-tools button,.graph-card li button { border-color: rgba(16,44,50,.14); background: #fffdf7; }.graph-tools button.active { border-color: var(--agent-ink); background: var(--agent-ink); }.insight-error { border-left-color: var(--agent-coral); background: rgba(255,253,247,.86); }
.insights-panel { gap: 22px; }.insight-query { position: relative; grid-template-columns: minmax(0, 1.2fr) 236px 176px; gap: 18px; overflow: hidden; padding: 32px; border-radius: 26px!important; background: linear-gradient(123deg, #102c32 0%, #173e42 62%, #274950 100%); box-shadow: 0 24px 44px rgba(16,44,50,.15)!important; }.insight-query::after { content: 'READ'; position: absolute; right: -20px; top: -37px; color: rgba(184,214,125,.08); font-family: var(--font-serif); font-size: 10rem; font-weight: 700; letter-spacing: -.1em; }.insight-query-copy,.insight-query label,.insight-query .btn { position: relative; z-index: 1; }.insight-query-copy h2 { max-width: 360px; font-size: clamp(2.5rem,5vw,4.3rem); line-height: .92; }.insight-query-copy p { max-width: 610px; font-size: .88rem; }.insight-query .btn { grid-column: 2 / 4; min-height: 46px; border-radius: 12px; box-shadow: 0 9px 16px rgba(0,0,0,.14); }.insight-grid { grid-template-columns: repeat(12,minmax(0,1fr)); gap: 18px; align-items: stretch; }.insight-card { min-height: 230px; padding: 25px; border: 0!important; border-radius: 22px!important; background: rgba(255,253,247,.84); box-shadow: 0 14px 31px rgba(16,44,50,.07)!important; }.insight-card::before { width: 150px; height: 150px; right: -55px; top: -62px; background: rgba(184,214,125,.2); }.insight-card::after { content: ''; position: absolute; left: 0; top: 26px; bottom: 26px; width: 3px; border-radius: 0 3px 3px 0; background: var(--agent-coral); }.insight-card > span { display: block; margin-bottom: 11px; color: var(--agent-coral); font-size: 2.5rem; line-height: .8; }.insight-card h2 { margin: 0 0 14px; font-size: clamp(1.45rem,2vw,2rem); line-height: 1; }.insight-card ul { max-height: 210px; padding-right: 7px; overflow: auto; }.insight-card li { padding-bottom: 9px; border-bottom: 1px solid rgba(16,44,50,.08); }.insight-card li:last-child { border-bottom: 0; }.insight-card:nth-child(1) { grid-column: span 4; background: #f7efe0; }.insight-card:nth-child(2) { grid-column: span 8; min-height: 400px; background: linear-gradient(135deg,#fffdf7,#e6f0d1); }.insight-card:nth-child(3) { grid-column: span 4; min-height: 290px; background: #f5e3d8; }.insight-card:nth-child(4) { grid-column: span 8; min-height: 290px; }.insight-card:nth-child(5) { grid-column: span 4; }.insight-card:nth-child(6) { grid-column: span 4; background: #e4efe8; }.insight-card:nth-child(7) { grid-column: span 4; }.insight-card:nth-child(8) { grid-column: span 12; min-height: 156px; display: grid; grid-template-columns: 185px minmax(0,1fr); column-gap: 22px; }.insight-card:nth-child(8)::after { top: 19px; bottom: 19px; }.insight-card:nth-child(8) > span { grid-row: 1 / span 2; margin: 0; align-self: center; font-size: 4rem; }.insight-card:nth-child(8) h2 { align-self: end; margin: 0; }.insight-card:nth-child(8) p,.insight-card:nth-child(8) ul { grid-column: 2; margin: 5px 0; }.graph-card,.reading-map-card { grid-column: auto; }.relationship-map,.cytoscape-graph { height: 250px!important; margin-top: 13px!important; border: 0!important; border-radius: 15px!important; background: rgba(255,253,247,.7)!important; }.graph-tools { margin: 0 0 9px; }.reading-map-events { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; max-height: 205px; padding: 0; overflow: auto; list-style: none; }.reading-map-events li { margin: 0; padding: 10px 12px!important; border: 1px solid rgba(16,44,50,.1)!important; border-radius: 11px; background: rgba(255,253,247,.65); }.insight-boundary-note { margin: -8px 0 0; border-radius: 13px; box-shadow: 0 8px 20px rgba(16,44,50,.04); }.spoiler-dialog-backdrop { position: fixed; inset: 0; z-index: 5000; display: grid; place-items: center; padding: 20px; background: rgba(9,27,31,.72); backdrop-filter: blur(7px); }.spoiler-dialog { position: relative; width: min(100%, 510px); padding: 34px; border: 1px solid rgba(255,253,247,.25); border-radius: 24px; color: #fffaf0; background: #173e42; box-shadow: 0 30px 85px rgba(0,0,0,.35); }.spoiler-dialog-mark { display: grid; width: 39px; height: 39px; place-items: center; border-radius: 50%; color: #173e42; background: var(--agent-lime); font-family: var(--font-serif); font-size: 1.75rem; }.spoiler-dialog-kicker { margin: 22px 0 6px; color: var(--agent-lime); font-size: .67rem; font-weight: 800; letter-spacing: .16em; }.spoiler-dialog h2 { margin: 0; color: #fffaf0; font-family: var(--font-serif); font-size: clamp(2rem,5vw,3rem); line-height: .96; letter-spacing: -.06em; }.spoiler-dialog > p:not(.spoiler-dialog-kicker) { color: rgba(255,250,240,.76); line-height: 1.8; }.spoiler-dialog-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 9px; margin-top: 26px; }.spoiler-dialog-actions .btn { border-radius: 10px; padding: 10px 14px; }.spoiler-dialog-actions .btn-primary { border: 0; color: var(--agent-ink); background: var(--agent-lime); }
.privacy-layout { grid-template-columns: minmax(0, 1.25fr) minmax(280px, .75fr); }.preference-form { padding: 28px; background: rgba(255,253,247,.86); }.privacy-danger { padding: 28px; border: 1px solid rgba(212,97,69,.26)!important; background: linear-gradient(145deg, #fffaf0, #f7dfd5)!important; }.privacy-danger p { color: var(--agent-ink-soft); }.privacy-danger .btn { border-color: var(--agent-coral); color: var(--agent-coral); }
@media (max-width: 1160px) { .agent-workbench { grid-template-columns: 230px minmax(0, 1fr); }.workspace-notes { display: grid; grid-column: 1 / -1; grid-template-columns: 1fr 1fr auto; align-items: stretch; }.privacy-note { margin: 0; align-self: end; }.insight-grid { grid-template-columns: repeat(2,minmax(0,1fr)); } }
@media (min-width: 761px) and (max-width: 1160px) { .insight-query { grid-template-columns: minmax(0,1fr) 210px; }.insight-query .btn { grid-column: 1 / -1; }.insight-grid { grid-template-columns: repeat(6,minmax(0,1fr)); }.insight-card:nth-child(1),.insight-card:nth-child(3),.insight-card:nth-child(5),.insight-card:nth-child(6),.insight-card:nth-child(7) { grid-column: span 3; }.insight-card:nth-child(2),.insight-card:nth-child(4) { grid-column: span 6; }.insight-card:nth-child(8) { grid-column: span 6; } }
@media (max-width: 760px) { .agent-center .container { padding-top: 18px; padding-bottom: 36px; }.agent-hero { display: flex; flex-direction: column; padding: 25px; border-radius: 22px; }.agent-hero h1 { font-size: clamp(2.7rem, 15vw, 4.3rem); }.credit-card { min-height: 145px; }.agent-tabs { display: flex; width: 100%; overflow-x: auto; }.agent-tabs button { flex: 0 0 auto; }.agent-workbench { display: flex!important; gap: 12px; }.session-list { max-height: 220px; }.session-scroll { min-height: 86px; }.chat-pane { min-height: 590px; }.workspace-notes { display: grid; grid-template-columns: 1fr; }.chat-header { align-items: flex-start; flex-direction: column; }.chat-actions { width: 100%; justify-content: flex-start; }.chat-welcome { grid-template-columns: 1fr; gap: 18px; }.chat-history { min-height: 370px; padding: 23px 18px; }.center-message { max-width: 92%; }.center-input { padding: 12px; }.chat-plugin-panel label { width: 100%; flex-direction: column; align-items: stretch; }.chat-plugin-panel select { max-width: none; }.plugin-divider { display: none; }.chat-model-select { flex: 1 0 145px; }.send-button { min-height: 44px; }.insight-query { display: flex!important; padding: 22px; }.insight-grid { display: grid!important; grid-template-columns: 1fr; }.graph-card,.reading-map-card { grid-column: auto; }.model-layout,.privacy-layout { display: grid!important; grid-template-columns: 1fr; }.model-intro { min-height: auto; }.agent-status-strip button { margin-left: 0; } }
@media (max-width: 760px) { .insight-query { gap: 15px; padding: 24px; }.insight-query-copy h2 { font-size: 3rem; }.insight-query .btn { width: 100%; }.insight-card,.insight-card:nth-child(n) { grid-column: 1; min-height: 0; padding: 22px; }.insight-card:nth-child(2) { min-height: 370px; }.insight-card:nth-child(8) { display: block; }.insight-card:nth-child(8) > span { margin-bottom: 13px; font-size: 2.5rem; }.insight-card:nth-child(8) p,.insight-card:nth-child(8) ul { margin: 8px 0; }.reading-map-events { grid-template-columns: 1fr; }.spoiler-dialog { padding: 28px 23px; }.spoiler-dialog-actions { justify-content: stretch; }.spoiler-dialog-actions .btn { flex: 1 1 100%; } }
/* The command rail keeps the Agent center spatially stable, like an IDE rather than a dashboard. */
.task-center-card { min-width: 215px; padding: 16px; border: 1px solid rgba(16,44,50,.12); border-radius: 15px; color: var(--agent-ink); background: rgba(255,253,247,.76); }
.task-center-panel { display:grid; gap:14px; }.task-center-panel header { max-width:720px; padding:12px 0; }.task-center-panel h2 { margin:7px 0; color:var(--agent-ink); font-family:var(--font-serif); font-size:clamp(2rem,4vw,3.2rem); letter-spacing:-.06em; }.task-center-panel header p,.task-empty { color:var(--agent-ink-soft); line-height:1.65; }.task-empty { padding:30px; border:1px dashed rgba(16,44,50,.18); border-radius:16px; background:rgba(255,253,247,.55); }.task-row { display:flex; align-items:center; justify-content:space-between; gap:26px; padding:20px 22px; border:1px solid rgba(16,44,50,.1)!important; border-radius:16px!important; background:rgba(255,253,247,.8); }.task-row strong,.task-row small { display:block; }.task-row strong { margin:8px 0 3px; color:var(--agent-ink); }.task-row small { color:var(--agent-ink-soft); font-size:.72rem; }.task-status { display:inline-block; border-radius:99px; padding:4px 7px; color:#47612f; background:rgba(184,214,125,.28); font-size:.65rem; font-weight:800; }.task-status.failed { color:#a34535; background:rgba(212,97,69,.16); }.task-status.queued { color:#5d6744; background:rgba(184,214,125,.18); }.task-row-progress { min-width:240px; }.task-row-progress b { display:block; margin-bottom:7px; color:var(--agent-ink); font-size:.76rem; }.task-row-progress > div { height:6px; overflow:hidden; border-radius:99px; background:rgba(16,44,50,.1); }.task-row-progress i { display:block; height:100%; border-radius:inherit; background:var(--agent-coral); transition:width .45s ease; }.task-row-progress small { margin-top:7px; }
.task-center-card span,.task-center-card strong,.task-center-card small { display:block; }.task-center-card span { color:var(--agent-coral); font-size:.65rem; font-weight:800; letter-spacing:.08em; }.task-center-card strong { margin:5px 0 3px; font-size:.88rem; }.task-center-card small { overflow:hidden; color:var(--agent-ink-soft); font-size:.67rem; white-space:nowrap; text-overflow:ellipsis; }.task-progress { height:4px; margin-top:11px; overflow:hidden; border-radius:99px; background:rgba(16,44,50,.12); }.task-progress i { display:block; height:100%; border-radius:inherit; background:var(--agent-coral); transition:width .45s ease; }
.insight-commandbar { display:flex; align-items:center; justify-content:space-between; gap:12px; padding:10px 12px; border:1px solid rgba(16,44,50,.11); border-radius:14px; background:rgba(255,253,247,.72); }.insight-mode-switcher { display:flex; min-width:0; gap:4px; overflow-x:auto; }.insight-mode-switcher button { flex:0 0 auto; border:0; border-radius:8px; padding:8px 10px; color:var(--agent-ink-soft); background:transparent; cursor:pointer; font:inherit; font-size:.72rem; font-weight:700; }.insight-mode-switcher button:hover { background:rgba(16,44,50,.06); }.insight-mode-switcher button.active { color:#fffaf0; background:var(--agent-ink); }.build-index-button { flex:0 0 auto; border:0; border-radius:9px; padding:9px 11px; color:var(--agent-ink); background:var(--agent-lime); cursor:pointer; font:inherit; font-size:.72rem; font-weight:800; }
.insight-grid { grid-template-columns:minmax(0,1fr)!important; }.insight-card,.insight-card:nth-child(n) { grid-column:auto!important; min-height:300px; }.insight-card.graph-card { min-height:calc(100vh - 340px); }.graph-card .relationship-map,.graph-card .cytoscape-graph { height:min(62vh,680px)!important; }.reading-map-card { min-height:calc(100vh - 340px); }.reading-map-events { max-height:calc(100vh - 510px); }
.knowledge-build-backdrop { position:fixed; inset:0; z-index:5100; display:grid; place-items:center; padding:20px; background:rgba(9,27,31,.72); backdrop-filter:blur(8px); }.knowledge-build-dialog { width:min(100%,580px); padding:32px; border:1px solid rgba(255,253,247,.2); border-radius:23px; color:#fffaf0; background:linear-gradient(145deg,#173e42,#102c32); box-shadow:0 30px 85px rgba(0,0,0,.35); }.knowledge-build-dialog h2 { margin:7px 0 14px; color:#fffaf0; font-family:var(--font-serif); font-size:clamp(2rem,5vw,3.1rem); line-height:.96; letter-spacing:-.06em; }.dialog-kicker { color:var(--agent-lime); font-size:.66rem; font-weight:800; letter-spacing:.14em; }.knowledge-build-dialog > p { color:rgba(255,250,240,.74); line-height:1.75; }.build-cost-note { display:grid; gap:5px; margin:18px 0; padding:13px; border-left:3px solid var(--agent-lime); border-radius:8px; background:rgba(184,214,125,.14); }.build-cost-note b { color:var(--agent-lime); }.build-cost-note span,.dialog-help { color:rgba(255,250,240,.7); font-size:.72rem; line-height:1.6; }.knowledge-build-dialog label { display:flex; flex-direction:column; gap:6px; margin-top:13px; color:rgba(255,250,240,.85); font-size:.78rem; font-weight:700; }.knowledge-build-dialog select { border:1px solid rgba(255,253,247,.2); border-radius:9px; padding:10px; color:#fffaf0; background:rgba(255,253,247,.1); font:inherit; }.knowledge-build-dialog option { color:var(--agent-ink); }.knowledge-build-dialog .build-share { flex-direction:row; align-items:center; line-height:1.45; }.knowledge-build-dialog .build-share input { accent-color:var(--agent-lime); }
@media (min-width: 1080px) { .agent-center .container { display:grid; grid-template-columns:210px minmax(0,1fr); column-gap:26px; align-items:start; }.agent-tabs { grid-column:1; grid-row:1 / span 10; position:sticky; top:76px; display:flex; flex-direction:column; align-items:stretch; gap:3px; margin:0; padding:12px 8px; border:1px solid rgba(16,44,50,.11); border-radius:16px; background:rgba(255,253,247,.72); }.agent-tabs button { border-radius:9px; padding:10px 12px; text-align:left; }.agent-tabs button.active { border:0; color:#fffaf0; background:var(--agent-ink); }.agent-hero,.agent-status-strip,.agent-page-loading,.agent-workbench,.model-layout,.insights-panel,.privacy-layout,.agent-load-notice { grid-column:2; }.agent-hero { margin-bottom:22px; }.agent-status-strip { margin-top:0; }.agent-workbench { grid-template-columns:220px minmax(0,1fr); }.workspace-notes { display:none; } }
@media (max-width:760px) { .task-center-card { width:100%; }.insight-commandbar { align-items:stretch; flex-direction:column; }.build-index-button { width:100%; }.knowledge-build-dialog { padding:27px 22px; }.insight-card.graph-card { min-height:520px; }.graph-card .relationship-map,.graph-card .cytoscape-graph { height:420px!important; } }

/* The insight workspace uses a stable navigation rail and one focused reading surface. */
.insights-panel { display:grid; gap:18px; }
.insight-page-head { display:flex; align-items:flex-end; justify-content:space-between; gap:24px; padding:8px 3px 2px; }
.insight-page-head h2 { margin:5px 0 7px; color:var(--agent-ink); font-family:var(--font-serif); font-size:clamp(2.35rem,4.6vw,4.25rem); line-height:.9; letter-spacing:-.075em; }
.insight-page-head p { max-width:580px; margin:0; color:var(--agent-ink-soft); line-height:1.65; }
.insight-book-stamp { display:grid; flex:0 0 auto; gap:5px; min-width:214px; padding:13px 15px; border:1px solid rgba(16,44,50,.13); border-radius:14px; color:var(--agent-ink); background:rgba(255,253,247,.72); }
.insight-book-stamp b { overflow:hidden; font-family:var(--font-serif); font-size:1.12rem; text-overflow:ellipsis; white-space:nowrap; }
.insight-book-stamp span { color:var(--agent-ink-soft); font-size:.72rem; }
.insight-console { display:grid; grid-template-columns:minmax(210px,1.35fr) minmax(150px,.55fr) minmax(178px,.8fr) auto; align-items:end; gap:11px; padding:13px; border:1px solid rgba(16,44,50,.14); border-radius:17px; background:linear-gradient(115deg,rgba(255,253,247,.93),rgba(244,239,222,.8)); box-shadow:0 12px 32px rgba(65,47,25,.06); }
.insight-field { display:grid; gap:5px; color:var(--agent-ink); font-size:.72rem; font-weight:800; letter-spacing:.04em; }
.insight-field select,.insight-field input { min-width:0; box-sizing:border-box; border:1px solid rgba(16,44,50,.18); border-radius:9px; padding:10px 11px; color:var(--agent-ink); background:#fffdf7; font:inherit; font-size:.85rem; outline:none; }
.insight-field select:focus,.insight-field input:focus { border-color:var(--agent-coral); box-shadow:0 0 0 3px rgba(212,97,69,.12); }
.insight-field small { min-height:14px; color:var(--agent-ink-soft); font-size:.66rem; font-weight:500; letter-spacing:0; }
.insight-safety-state { display:grid; gap:4px; min-height:38px; margin:0 0 1px; padding:8px 10px; border-left:3px solid var(--agent-lime); color:var(--agent-ink-soft); background:rgba(184,214,125,.16); font-size:.68rem; line-height:1.35; }
.insight-safety-state b { color:#486333; font-size:.72rem; }.insight-safety-state.warning { border-color:var(--agent-coral); background:rgba(212,97,69,.1); }.insight-safety-state.warning b { color:#a14434; }
.insight-run { min-height:42px; white-space:nowrap; }
.insight-boundary-note { margin:0; padding:8px 12px; border-radius:9px; color:var(--agent-ink-soft); background:rgba(16,44,50,.045); font-size:.72rem; }.insight-boundary-note.limited { color:#924232; background:rgba(212,97,69,.1); }
.insight-workspace { display:grid; grid-template-columns:190px minmax(0,1fr); align-items:stretch; min-height:560px; border:1px solid rgba(16,44,50,.14); border-radius:20px; overflow:hidden; background:rgba(255,253,247,.72); box-shadow:0 18px 48px rgba(65,47,25,.07); }
.insight-nav { display:flex; flex-direction:column; gap:3px; padding:16px 10px; color:rgba(255,250,240,.72); background:linear-gradient(165deg,#173e42,#102c32 74%); }
.insight-nav > span { margin:2px 8px 11px; color:var(--agent-lime); font-size:.63rem; font-weight:800; letter-spacing:.13em; }
.insight-nav button { display:grid; gap:2px; width:100%; border:0; border-left:2px solid transparent; border-radius:8px; padding:10px 10px 10px 12px; color:rgba(255,250,240,.69); background:transparent; text-align:left; cursor:pointer; font:inherit; transition:background .18s ease,color .18s ease; }
.insight-nav button:hover { color:#fffaf0; background:rgba(255,253,247,.08); }.insight-nav button.active { border-left-color:var(--agent-lime); color:#fffaf0; background:rgba(255,253,247,.13); }.insight-nav button b { font-size:.78rem; }.insight-nav button small { font-size:.64rem; opacity:.7; }
.insight-stage { display:flex; flex-direction:column; min-width:0; padding:23px; background:radial-gradient(circle at 96% 4%,rgba(184,214,125,.19),transparent 24%),linear-gradient(145deg,#fffdf7,#f7f0e1); }
.insight-stage-head { display:flex; align-items:flex-end; justify-content:space-between; gap:15px; padding:0 2px 18px; border-bottom:1px solid rgba(16,44,50,.11); }.insight-stage-head h3 { margin:4px 0 0; color:var(--agent-ink); font-family:var(--font-serif); font-size:clamp(1.85rem,3vw,2.8rem); line-height:.96; letter-spacing:-.065em; }.insight-stage-head .build-index-button { color:#173e42; background:var(--agent-lime); }
.insight-stage .insight-grid { display:block!important; min-height:0; padding-top:18px; }.insight-stage .insight-card,.insight-stage .insight-card:nth-child(n) { min-height:430px; padding:0; border:0!important; border-radius:0!important; background:transparent!important; box-shadow:none!important; }.insight-stage .insight-card > span:first-child { display:inline-block; margin-bottom:11px; color:var(--agent-coral); font-size:.68rem; font-weight:900; letter-spacing:.14em; }.insight-stage .insight-card h2 { margin:0 0 10px; color:var(--agent-ink); font-family:var(--font-serif); font-size:clamp(1.7rem,2.6vw,2.45rem); letter-spacing:-.055em; }.insight-stage .insight-card > p { max-width:720px; color:var(--agent-ink-soft); line-height:1.7; }.insight-stage .insight-card ul { display:grid; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); gap:10px; padding:0; list-style:none; }.insight-stage .insight-card li { margin:0; padding:14px; border:1px solid rgba(16,44,50,.1); border-radius:11px; background:rgba(255,253,247,.78); line-height:1.6; }.insight-stage .insight-card > small { display:block; margin-top:14px; color:var(--agent-ink-soft); line-height:1.5; }
.insight-stage .graph-card,.insight-stage .reading-map-card { min-height:calc(100vh - 330px)!important; }.insight-stage .graph-card .relationship-map,.insight-stage .graph-card .cytoscape-graph { height:min(62vh,680px)!important; margin-top:12px!important; border-radius:14px!important; background:rgba(255,253,247,.86)!important; }.insight-stage .reading-map-events { max-height:calc(100vh - 460px); }.insight-stage .reading-map-events li { display:block; }.insight-stage .graph-evidence { margin-top:12px; }
.insight-empty-stage { display:grid; place-content:center; min-height:390px; max-width:440px; }.insight-empty-stage span { color:var(--agent-coral); font-size:.68rem; font-weight:900; letter-spacing:.14em; }.insight-empty-stage h4 { margin:9px 0; color:var(--agent-ink); font-family:var(--font-serif); font-size:clamp(2rem,4vw,3.3rem); line-height:.95; letter-spacing:-.065em; }.insight-empty-stage p { margin:0; color:var(--agent-ink-soft); line-height:1.7; }
@media (max-width:900px) { .insight-console { grid-template-columns:1fr 1fr; }.insight-run { width:100%; }.insight-workspace { grid-template-columns:1fr; }.insight-nav { display:grid; grid-template-columns:repeat(3,1fr); padding:10px; }.insight-nav > span { display:none; }.insight-nav button { min-height:56px; }.insight-stage { padding:20px; } }
@media (max-width:600px) { .insight-page-head { align-items:flex-start; flex-direction:column; }.insight-book-stamp { width:100%; box-sizing:border-box; }.insight-console { grid-template-columns:1fr; }.insight-nav { grid-template-columns:repeat(2,1fr); }.insight-stage-head { align-items:flex-start; flex-direction:column; }.insight-stage-head .build-index-button { width:100%; }.insight-stage .insight-card ul { grid-template-columns:1fr; }.insight-stage .insight-card.graph-card,.insight-stage .insight-card.reading-map-card { min-height:470px!important; }.insight-stage .graph-card .relationship-map,.insight-stage .graph-card .cytoscape-graph { height:390px!important; } }

/* Overview contains orientation and account signals; workspaces stay deliberately distraction-free. */
.agent-dashboard { display:grid; gap:20px; }
.dashboard-head { display:flex; align-items:flex-end; justify-content:space-between; gap:28px; padding:12px 3px 4px; }
.dashboard-head > div { max-width:740px; }
.dashboard-head h1 { max-width:700px; margin:10px 0 9px; color:var(--agent-ink); font-family:var(--font-serif); font-size:clamp(2.15rem,3.4vw,3.6rem); font-weight:600; line-height:.98; letter-spacing:-.065em; }
.dashboard-head p { max-width:580px; margin:0; color:var(--agent-ink-soft); line-height:1.7; }
.dashboard-primary-action { display:inline-flex; flex:0 0 auto; align-items:center; gap:15px; border:0; border-radius:12px; padding:13px 16px; color:#fffaf0; background:var(--agent-ink); box-shadow:0 12px 23px rgba(16,44,50,.16); cursor:pointer; font:inherit; font-size:.82rem; font-weight:800; }
.dashboard-primary-action b { color:var(--agent-lime); font-size:1.15rem; line-height:1; }
.dashboard-metrics { display:grid; grid-template-columns:1.08fr minmax(220px,.9fr) minmax(220px,.95fr); gap:14px; }
.dashboard-metrics > article { display:flex; flex-direction:column; min-height:205px; padding:20px; border:1px solid rgba(16,44,50,.12); border-radius:18px; box-shadow:0 12px 30px rgba(16,44,50,.055); }
.dashboard-credit { color:#fffaf0; background:linear-gradient(145deg,#173e42,#0d272c); }
.dashboard-credit > span,.dashboard-label { color:var(--agent-lime); font-size:.65rem; font-weight:800; letter-spacing:.14em; }
.dashboard-credit strong { margin:8px 0 4px; color:#fffaf0; font-family:var(--font-serif); font-size:clamp(3rem,5vw,4.4rem); line-height:.85; letter-spacing:-.08em; }
.dashboard-credit small { max-width:300px; margin-top:auto; color:rgba(255,250,240,.66); font-size:.7rem; line-height:1.55; }
.dashboard-credit button,.dashboard-task button { align-self:flex-start; margin-top:12px; border:0; border-bottom:1px solid currentColor; padding:1px 0; color:var(--agent-lime); background:transparent; cursor:pointer; font:inherit; font-size:.7rem; font-weight:700; }
.dashboard-status,.dashboard-task { color:var(--agent-ink); background:rgba(255,253,247,.78); }
.dashboard-status strong,.dashboard-task strong { display:flex; align-items:center; gap:8px; margin:13px 0 7px; font-family:var(--font-serif); font-size:1.25rem; letter-spacing:-.035em; }
.dashboard-status strong i { width:8px; height:8px; }
.dashboard-status p,.dashboard-task p { margin:0; color:var(--agent-ink-soft); font-size:.76rem; line-height:1.55; }
.status-tags { display:flex; flex-wrap:wrap; gap:6px; margin-top:auto; padding-top:14px; }
.status-tags span { padding:5px 7px; border-radius:7px; color:var(--agent-ink-soft); background:rgba(16,44,50,.06); font-size:.63rem; font-weight:700; }
.dashboard-task { background:linear-gradient(145deg,#f2ebdc,#e1edd0); }
.dashboard-task strong { display:block; }
.dashboard-task .task-progress { margin-top:auto; }
.dashboard-task .task-progress + button { margin-top:12px; color:var(--agent-coral); }
.dashboard-shortcuts { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:12px; }
.dashboard-shortcuts button { position:relative; display:grid; grid-template-columns:auto 1fr auto; gap:5px 12px; align-items:center; min-height:130px; border:1px solid rgba(16,44,50,.11); border-radius:15px; padding:17px; color:var(--agent-ink); background:rgba(255,253,247,.64); text-align:left; cursor:pointer; font:inherit; transition:transform .18s ease,box-shadow .18s ease,border-color .18s ease; }
.dashboard-shortcuts button:hover { border-color:rgba(212,97,69,.55); box-shadow:0 12px 25px rgba(16,44,50,.09); transform:translateY(-2px); }
.dashboard-shortcuts span { grid-row:1 / span 2; align-self:start; color:var(--agent-coral); font-family:var(--font-serif); font-size:1.65rem; letter-spacing:-.08em; }
.dashboard-shortcuts b { align-self:end; font-family:var(--font-serif); font-size:1.2rem; letter-spacing:-.04em; }
.dashboard-shortcuts small { grid-column:2; color:var(--agent-ink-soft); font-size:.7rem; line-height:1.55; }
.dashboard-shortcuts em { grid-column:3; grid-row:1 / span 2; color:var(--agent-coral); font-size:1.2rem; font-style:normal; }

.is-chat-workspace .agent-workbench { min-height:calc(100vh - 142px); }
.is-chat-workspace .chat-pane,.is-chat-workspace .session-list { min-height:calc(100vh - 142px); }
.is-chat-workspace .chat-history { min-height:0; }

@media (min-width:1080px) { .agent-dashboard,.task-center-panel { grid-column:2; }.is-chat-workspace .agent-workbench { grid-template-columns:250px minmax(0,1fr); height:calc(100vh - 142px); }.is-chat-workspace .chat-pane,.is-chat-workspace .session-list { height:100%; min-height:0; }.is-chat-workspace .chat-history { flex:1 1 auto; }.is-chat-workspace .center-input { flex:0 0 auto; } }
@media (max-width:900px) { .dashboard-metrics { grid-template-columns:repeat(2,minmax(0,1fr)); }.dashboard-credit { grid-column:span 2; }.dashboard-shortcuts { grid-template-columns:1fr; }.dashboard-shortcuts button { min-height:96px; } }
@media (max-width:600px) { .dashboard-head { align-items:flex-start; flex-direction:column; }.dashboard-head h1 { font-size:clamp(2.3rem,12vw,3.7rem); }.dashboard-primary-action { width:100%; justify-content:space-between; }.dashboard-metrics { grid-template-columns:1fr; }.dashboard-credit { grid-column:auto; }.dashboard-metrics > article { min-height:170px; }.is-chat-workspace .agent-workbench,.is-chat-workspace .chat-pane { min-height:calc(100vh - 112px); height:auto; }.is-chat-workspace .session-list { min-height:0; }.dashboard-shortcuts button { grid-template-columns:auto 1fr auto; } }

/* A fixed application canvas keeps navigation stable while each workspace owns its own overflow. */
.agent-center.page { height:calc(100dvh - 64px); min-height:0; padding:0; overflow:hidden; }
.agent-center.page .container { height:100%; box-sizing:border-box; max-width:none; padding:18px 24px; }
.agent-dashboard,.model-layout,.task-center-panel,.privacy-layout { min-height:0; overflow:auto; overscroll-behavior:contain; }
.agent-dashboard { padding-right:5px; }
.agent-workbench { min-height:0!important; }
.agent-workbench,.chat-pane,.session-list { height:100%; }
.chat-history { min-height:0!important; overscroll-behavior:contain; }
.model-layout,.task-center-panel,.privacy-layout { padding-right:8px; }

.message-edit-button { display:block; margin:7px 0 -3px auto; border:0; padding:2px 0; color:inherit; background:transparent; cursor:pointer; font:inherit; font-size:.67rem; opacity:.62; text-decoration:underline; text-underline-offset:3px; }
.center-message:hover .message-edit-button { opacity:1; }
.message-editor { box-sizing:border-box; width:100%; resize:vertical; border:1px solid rgba(255,250,240,.48); border-radius:10px; padding:9px; color:var(--agent-ink); background:#fffdf7; font:inherit; line-height:1.55; outline:none; }
.message-editor:focus { border-color:var(--agent-lime); box-shadow:0 0 0 3px rgba(184,214,125,.18); }
.message-edit-actions { display:flex; justify-content:flex-end; gap:7px; margin-top:8px; }
.message-edit-actions button { border:1px solid rgba(255,250,240,.45); border-radius:7px; padding:5px 8px; color:#fffaf0; background:transparent; cursor:pointer; font:inherit; font-size:.66rem; }
.message-edit-actions button:last-child { border-color:var(--agent-lime); color:var(--agent-ink); background:var(--agent-lime); font-weight:800; }
.message-edit-actions button:disabled { cursor:not-allowed; opacity:.52; }

.chat-model-select { position:relative; flex:0 1 230px!important; min-height:48px; box-sizing:border-box; border:1px solid rgba(16,44,50,.14)!important; border-radius:11px!important; padding:0!important; background:linear-gradient(135deg,#fffdf7,#eef2df)!important; box-shadow:inset 0 1px 0 rgba(255,255,255,.75); }
.model-picker-trigger { display:grid; grid-template-columns:1fr auto; gap:2px 9px; width:100%; border:0; padding:7px 11px; color:var(--agent-ink); background:transparent; text-align:left; cursor:pointer; font:inherit; }
.model-picker-trigger span { color:var(--agent-ink-soft); font-size:.59rem; font-weight:800; letter-spacing:.08em; }.model-picker-trigger strong { overflow:hidden; font-size:.77rem; text-overflow:ellipsis; white-space:nowrap; }.model-picker-trigger b { grid-column:2; grid-row:1 / span 2; align-self:center; color:var(--agent-coral); font-size:1rem; line-height:1; transition:transform .16s ease; }.chat-model-select.open .model-picker-trigger b { transform:rotate(180deg); }
.chat-model-select:focus-within,.chat-model-select.open { border-color:var(--agent-coral)!important; box-shadow:0 0 0 3px rgba(212,97,69,.12); }
.chat-model-menu { position:absolute; z-index:30; right:0; bottom:calc(100% + 8px); display:grid; width:min(320px,calc(100vw - 48px)); gap:3px; padding:6px; border:1px solid rgba(16,44,50,.13); border-radius:12px; background:#fffdf7; box-shadow:0 18px 38px rgba(16,44,50,.18); }
.chat-model-menu button { display:grid; gap:2px; border:0; border-radius:8px; padding:9px 10px; color:var(--agent-ink); background:transparent; text-align:left; cursor:pointer; font:inherit; }.chat-model-menu button:hover { background:rgba(16,44,50,.055); }.chat-model-menu button.selected { color:#fffaf0; background:var(--agent-ink); }.chat-model-menu button span { font-size:.76rem; font-weight:800; }.chat-model-menu button small { color:inherit; font-size:.62rem; opacity:.68; }.chat-model-menu .model-picker-manage { display:block; border-top:1px solid rgba(16,44,50,.1); border-radius:0; padding-top:10px; color:var(--agent-coral); font-size:.68rem; font-weight:800; }

.insight-stage { min-height:0; overflow:hidden; }
.insight-stage-head { flex:0 0 auto; }
.insight-stage-actions { display:flex; align-items:center; justify-content:flex-end; gap:8px; }
.insight-subtabs { display:flex; gap:3px; padding:3px; border:1px solid rgba(16,44,50,.12); border-radius:9px; background:rgba(255,253,247,.8); }
.insight-subtabs button { border:0; border-radius:6px; padding:6px 8px; color:var(--agent-ink-soft); background:transparent; cursor:pointer; font:inherit; font-size:.67rem; font-weight:800; }
.insight-subtabs button.active { color:#fffaf0; background:var(--agent-ink); }
.insight-stage .insight-grid { flex:1 1 auto; overflow:auto; overscroll-behavior:contain; padding-right:7px; }
.insight-stage .insight-card,.insight-stage .insight-card:nth-child(n) { min-height:0!important; }
.insight-stage .graph-card,.insight-stage .reading-map-card { min-height:0!important; }
.insight-stage .graph-card .relationship-map,.insight-stage .graph-card .cytoscape-graph { height:min(50vh,520px)!important; }

@media (min-width:1080px) {
  .agent-center.page .container { grid-template-columns:216px minmax(0,1fr); grid-template-rows:minmax(0,1fr); column-gap:20px; align-items:stretch; }
  .agent-tabs { grid-column:1; grid-row:1; position:static; align-self:stretch; min-height:0; margin:0; border:1px solid rgba(16,44,50,.1); border-radius:13px; background:rgba(255,253,247,.55); }
  .agent-tabs::before { content:'阅见助手'; padding:8px 10px 12px; color:var(--agent-ink); font-family:var(--font-serif); font-size:1.06rem; letter-spacing:-.04em; }
  .agent-dashboard,.agent-workbench,.model-layout,.task-center-panel,.insights-panel,.privacy-layout,.agent-load-notice { grid-column:2; grid-row:1; }
  .agent-workbench { grid-template-columns:242px minmax(0,1fr)!important; height:100%!important; }
  .insights-panel { grid-template-rows:auto auto auto minmax(0,1fr); min-height:0; height:100%; overflow:hidden; }
  .insight-workspace { min-height:0; height:100%; }
  .model-layout,.task-center-panel,.privacy-layout { height:100%; }
}
@media (max-width:1079px) {
  .agent-center.page { height:auto; min-height:calc(100dvh - 64px); overflow:visible; }
  .agent-center.page .container { height:auto; }
}
@media (max-width:700px) { .insight-stage-actions { align-items:stretch; flex-direction:column-reverse; width:100%; }.insight-subtabs { width:100%; }.insight-subtabs button { flex:1; }.chat-model-select { flex:1 1 100%!important; }.message-editor { min-height:90px; } }

/* Desktop agent center: a fixed, left-anchored command rail and one focused surface. */
.conversation-title-row { display:flex; align-items:center; gap:7px; min-width:0; }
.conversation-title-row strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.conversation-title-edit { flex:0 0 auto; border:0; border-radius:6px; padding:4px 6px; color:var(--agent-ink-soft); background:transparent; cursor:pointer; font:inherit; font-size:.66rem; font-weight:700; }
.conversation-title-edit:hover { color:var(--agent-ink); background:rgba(16,44,50,.07); }
.conversation-title-input { min-width:0; width:min(320px,48vw); border:0; border-bottom:1px solid var(--agent-coral); padding:2px 0 4px; color:var(--agent-ink); background:transparent; font-family:var(--font-serif); font-size:1.05rem; font-weight:700; outline:0; }
.message-edit-button { display:inline-flex; align-items:center; gap:4px; margin:8px 0 -3px auto; font-size:.68rem; }
.message-edit-button span { font-size:.85rem; line-height:1; }

.chat-model-select { flex:0 0 220px!important; border-radius:12px!important; background:#fffdf7!important; }
.model-picker-trigger { display:flex; align-items:center; gap:9px; min-height:48px; padding:7px 10px; }
.model-picker-signal { width:8px; height:8px; border-radius:50%; background:var(--agent-lime); box-shadow:0 0 0 3px rgba(184,214,125,.22); }
.model-picker-copy { display:grid; flex:1; gap:2px; min-width:0; }
.model-picker-copy small { color:var(--agent-ink-soft); font-size:.57rem; font-weight:800; letter-spacing:.07em; }
.model-picker-copy strong { font-size:.77rem; }
.model-picker-trigger b { margin-left:auto; }
.chat-model-menu { gap:5px; padding:8px; border-radius:14px; }
.chat-model-menu > p { margin:2px 4px 6px; color:var(--agent-ink-soft); font-size:.65rem; font-weight:700; }
.chat-model-menu button:not(.model-picker-manage) { grid-template-columns:auto minmax(0,1fr) auto; align-items:center; gap:9px; padding:9px; }
.chat-model-menu button i { width:9px; height:9px; border:1px solid rgba(16,44,50,.35); border-radius:50%; }
.chat-model-menu button.selected i { border:3px solid var(--agent-lime); background:#fffdf7; }
.chat-model-menu button span { display:grid; gap:2px; }.chat-model-menu button span b { font-size:.76rem; }.chat-model-menu button span small { font-size:.62rem; }
.chat-model-menu button em { border-radius:5px; padding:3px 5px; color:var(--agent-ink-soft); background:rgba(16,44,50,.07); font-size:.57rem; font-style:normal; font-weight:800; }
.chat-model-menu button.selected em { color:var(--agent-ink); background:rgba(184,214,125,.8); }

.model-layout { display:flex; flex-direction:column; gap:18px; min-height:0; }
.model-page-head { display:flex; align-items:flex-end; justify-content:space-between; gap:24px; }
.model-page-head > div:first-child { max-width:690px; }.model-page-head h2 { margin:6px 0 7px; color:var(--agent-ink); font-family:var(--font-serif); font-size:clamp(2rem,3.2vw,3.45rem); line-height:.94; letter-spacing:-.065em; }.model-page-head p { margin:0; color:var(--agent-ink-soft); line-height:1.6; }
.model-credit-chip { display:grid; grid-template-columns:auto auto; column-gap:14px; align-items:center; min-width:160px; padding:10px 13px; border:1px solid rgba(16,44,50,.12); border-radius:12px; background:#fffdf7; }.model-credit-chip span,.model-credit-chip small { color:var(--agent-ink-soft); font-size:.64rem; font-weight:700; }.model-credit-chip b { color:var(--agent-ink); font-family:var(--font-serif); font-size:1.7rem; line-height:1; }.model-credit-chip small { grid-column:1 / -1; margin-top:3px; }
.model-settings-grid { display:grid; grid-template-columns:minmax(230px,.75fr) minmax(310px,1fr); gap:14px; min-height:0; }
.model-platform-card,.model-form,.saved-models { box-sizing:border-box; border:1px solid rgba(16,44,50,.12); border-radius:16px; }
.model-platform-card { display:flex; flex-direction:column; justify-content:space-between; min-height:278px; padding:22px; color:#fffaf0; background:linear-gradient(145deg,#173e42,#102c32); }.model-card-kicker { display:block; color:var(--agent-coral); font-size:.63rem; font-weight:900; letter-spacing:.13em; }.model-platform-card h3,.model-form h3,.saved-models h3 { margin:8px 0 8px; font-family:var(--font-serif); font-size:1.45rem; line-height:1; letter-spacing:-.045em; }.model-platform-card p { margin:0; color:rgba(255,250,240,.72); line-height:1.65; font-size:.8rem; }.model-platform-card ul { display:grid; gap:7px; margin:22px 0 0; padding:0; list-style:none; }.model-platform-card li { padding:8px 9px; border-radius:7px; color:rgba(255,250,240,.84); background:rgba(255,253,247,.09); font-size:.72rem; }.model-platform-card li::before { content:'✓'; margin-right:7px; color:var(--agent-lime); }
.model-form { display:grid; grid-template-columns:1fr 1fr; gap:11px; padding:21px; background:linear-gradient(140deg,#fffdf7,#f5efdf); }.model-form-head { display:flex; grid-column:1 / -1; align-items:flex-start; justify-content:space-between; }.model-form-head > span { margin-top:2px; border:1px solid rgba(16,44,50,.13); border-radius:99px; padding:5px 7px; color:var(--agent-ink-soft); font-size:.61rem; font-weight:800; }.model-form label:last-of-type { grid-column:1 / -1; }.model-form label { gap:5px; color:var(--agent-ink-soft); font-size:.68rem; font-weight:800; }.model-form input { width:100%; box-sizing:border-box; border:1px solid rgba(16,44,50,.16); border-radius:9px; padding:10px; color:var(--agent-ink); background:#fffefa; font:inherit; font-size:.8rem; outline:none; }.model-form input:focus { border-color:var(--agent-coral); box-shadow:0 0 0 3px rgba(212,97,69,.1); }.model-field-help { grid-column:1 / -1; margin:-5px 0 0; color:var(--agent-ink-soft); font-size:.68rem; line-height:1.45; }.model-field-help code { padding:1px 4px; border-radius:4px; color:var(--agent-ink); background:rgba(16,44,50,.08); font-family:ui-monospace,SFMono-Regular,Consolas,monospace; }.model-save-button { display:flex; grid-column:1 / -1; align-items:center; justify-content:space-between; border:0; border-radius:9px; padding:10px 12px; color:#fffaf0; background:var(--agent-ink); cursor:pointer; font:inherit; font-size:.75rem; font-weight:800; }.model-save-button b { color:var(--agent-lime); font-size:1rem; }
.saved-models { grid-column:1 / -1; min-height:0; padding:18px 21px; background:#fffdf7; }.saved-models-head { display:flex; align-items:flex-start; justify-content:space-between; gap:12px; }.saved-models-head h3 { margin-bottom:0; }.saved-models-head > small { color:var(--agent-ink-soft); font-size:.65rem; }.model-empty { margin:20px 0 5px; color:var(--agent-ink-soft); font-size:.78rem; }.saved-model { padding:13px 0; }.saved-model-name { position:relative; padding-left:16px; }.saved-model-name > i { position:absolute; left:0; top:6px; width:7px; height:7px; border-radius:50%; background:var(--agent-lime); }.saved-model-name > i.disabled { background:#b4aaa0; }.saved-model small { overflow:hidden; max-width:420px; text-overflow:ellipsis; white-space:nowrap; }.model-actions button { border:1px solid rgba(16,44,50,.16); border-radius:7px; padding:5px 7px; color:var(--agent-ink-soft); background:transparent; cursor:pointer; font:inherit; font-size:.66rem; }.model-actions button:hover { color:var(--agent-ink); border-color:var(--agent-ink); }.model-actions button.danger:hover { color:var(--agent-coral); border-color:var(--agent-coral); }
.credit-task-card { border:1px solid rgba(16,44,50,.12); border-radius:16px; padding:18px; background:linear-gradient(145deg,#edf4dc,#fffdf7); }.credit-task-head { display:flex; align-items:flex-start; justify-content:space-between; gap:10px; }.credit-task-head h3 { margin:7px 0 0; color:var(--agent-ink); font-family:var(--font-serif); font-size:1.32rem; line-height:1; letter-spacing:-.04em; }.credit-task-head > small { border:1px solid rgba(16,44,50,.1); border-radius:999px; padding:5px 7px; color:var(--agent-ink-soft); background:#fffdf7; font-size:.61rem; font-weight:800; white-space:nowrap; }.credit-task-list { display:grid; gap:7px; margin-top:15px; }.credit-task-list > div { display:grid; grid-template-columns:1fr auto; gap:2px 9px; padding:8px 9px; border-radius:8px; color:var(--agent-ink); background:rgba(255,253,247,.76); font-size:.72rem; }.credit-task-list b { color:#54796d; font-family:var(--font-serif); font-size:.95rem; }.credit-task-list small { grid-column:1 / -1; color:var(--agent-ink-soft); font-size:.64rem; }.credit-task-list > div.done { opacity:.62; }.credit-task-card > p { margin:16px 0 0; color:var(--agent-ink-soft); font-size:.72rem; line-height:1.65; }

@media (min-width:1080px) {
  .agent-center.page .container { grid-template-columns:232px minmax(0,1fr); column-gap:0; padding:0; }
  .agent-tabs { padding:22px 12px; border:0; border-radius:0; background:linear-gradient(180deg,#102c32,#173e42); box-shadow:inset -1px 0 rgba(255,253,247,.12); }
  .agent-tabs::before { padding:7px 10px 20px; color:#fffaf0; }
  .agent-tabs button { border:0!important; border-radius:8px; padding:10px 12px; color:rgba(255,250,240,.64); font-size:.78rem; font-weight:700; }.agent-tabs button:hover { color:#fffaf0; background:rgba(255,253,247,.08); }.agent-tabs button.active { color:#102c32; background:var(--agent-lime); }
  .agent-dashboard,.model-layout,.task-center-panel,.privacy-layout { padding:26px 32px; overflow:hidden; }
  .agent-workbench,.insights-panel { box-sizing:border-box; padding:22px 28px; }
  .agent-workbench { grid-template-columns:250px minmax(0,1fr)!important; }
  .agent-dashboard { align-content:start; }.dashboard-head { padding-top:2px; }.dashboard-metrics > article { min-height:184px; }.dashboard-shortcuts button { min-height:112px; }
}
@media (max-width:900px) { .model-page-head { align-items:flex-start; flex-direction:column; }.model-settings-grid { grid-template-columns:1fr; }.saved-models { grid-column:auto; }.model-platform-card { min-height:0; }.model-credit-chip { width:100%; box-sizing:border-box; }.model-form { grid-template-columns:1fr; }.model-form label:last-of-type { grid-column:auto; }.model-save-button { grid-column:auto; } }
@media (max-width:700px) { .conversation-title-input { width:55vw; }.model-page-head h2 { font-size:2.25rem; }.chat-model-select { flex:1 1 100%!important; }.saved-model { align-items:flex-start; flex-direction:column; }.model-actions { justify-content:flex-start; } }

/* Compact controls prevent native number spinners from competing with the reading workspace. */
.chapter-stepper { display:grid; grid-template-columns:34px auto minmax(48px,1fr) auto 34px; align-items:center; gap:5px; min-height:42px; box-sizing:border-box; border:1px solid rgba(16,44,50,.18); border-radius:10px; padding:3px; color:var(--agent-ink-soft); background:#fffdf7; }
.chapter-stepper button { display:grid; width:30px; height:30px; place-items:center; border:0; border-radius:7px; color:var(--agent-ink); background:rgba(16,44,50,.06); cursor:pointer; font:inherit; font-size:1rem; }.chapter-stepper button:hover { color:#fffaf0; background:var(--agent-ink); }.chapter-stepper input { width:100%; min-width:0; border:0!important; padding:0!important; color:var(--agent-ink); background:transparent!important; text-align:center; font-weight:800; box-shadow:none!important; }
.chapter-stepper:focus-within { border-color:var(--agent-coral); box-shadow:0 0 0 3px rgba(212,97,69,.12); }
.build-range { display:grid; grid-template-columns:1fr auto 1fr; align-items:end; gap:10px; margin-top:14px; }.build-range > span { padding-bottom:10px; color:rgba(255,250,240,.55); font-size:.75rem; }.knowledge-build-dialog .build-range label { margin:0; }.knowledge-build-dialog .build-range input { width:100%; box-sizing:border-box; border:1px solid rgba(255,253,247,.2); border-radius:9px; padding:10px; color:#fffaf0; background:rgba(255,253,247,.1); font:inherit; outline:0; }.knowledge-build-dialog .build-range input:focus { border-color:var(--agent-lime); box-shadow:0 0 0 3px rgba(184,214,125,.12); }
.task-center-panel { grid-template-rows:auto minmax(0,1fr); gap:18px; }.task-center-head { display:flex; align-items:flex-end; justify-content:space-between; gap:20px; max-width:none!important; padding:0!important; }.task-center-head h2 { margin-bottom:6px; }.task-center-head p { max-width:610px; margin:0; }.task-refresh { display:inline-flex; align-items:center; gap:6px; flex:0 0 auto; border:1px solid rgba(16,44,50,.13); border-radius:8px; padding:7px 9px; color:var(--agent-ink-soft); background:#fffdf7; cursor:pointer; font:inherit; font-size:.68rem; font-weight:800; }.task-refresh:hover { border-color:var(--agent-ink); color:var(--agent-ink); }.task-refresh span { font-size:1rem; line-height:1; }
.task-list { display:grid; align-content:start; gap:10px; min-height:0; overflow:auto; padding-right:5px; }.task-row { display:grid; grid-template-columns:minmax(230px,.8fr) minmax(320px,1.2fr) auto; gap:26px; padding:18px 20px; }.task-row-main { min-width:0; }.task-row-main strong { overflow:hidden; max-width:100%; text-overflow:ellipsis; white-space:nowrap; }.task-row-main small { margin-top:6px; }.task-row-progress { min-width:0; }.task-progress-label { display:flex; justify-content:space-between; align-items:center; gap:10px; margin-bottom:7px; }.task-progress-label b,.task-progress-label small { margin:0; }.task-row-progress > small { margin-top:7px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.task-delete { align-self:center; border:1px solid rgba(163,69,53,.28); border-radius:8px; padding:7px 9px; color:#a34535; background:#fffaf7; cursor:pointer; font:inherit; font-size:.68rem; font-weight:700; }.task-delete:hover { border-color:#a34535; background:rgba(212,97,69,.1); }.task-empty { min-height:180px; display:grid; place-items:center; margin:0; text-align:center; }
@media (max-width:760px) { .task-center-head { align-items:flex-start; }.task-row { grid-template-columns:1fr; gap:13px; }.task-delete { justify-self:start; }.task-list { overflow:visible; }.build-range { grid-template-columns:1fr; gap:7px; }.build-range > span { display:none; } }
</style>

<style scoped>
.task-row-progress .task-progress-track { position:relative; box-sizing:border-box; height:12px; margin-top:7px; overflow:hidden; border:1px solid rgba(16,44,50,.2); border-radius:999px; background:repeating-linear-gradient(90deg,rgba(16,44,50,.09) 0 12px,rgba(16,44,50,.04) 12px 24px); }
.task-row-progress .task-progress-track i { display:block; height:100%; border-radius:inherit; background:linear-gradient(90deg,#d46145,#ee8d5a); box-shadow:inset 0 0 0 1px rgba(255,255,255,.25); transition:width .45s ease; }
.task-row-progress .task-progress-track.completed i { background:linear-gradient(90deg,#5e9166,#9bc46c); }
.task-row-progress .task-progress-track.failed { border-color:rgba(163,69,53,.35); background:repeating-linear-gradient(135deg,rgba(163,69,53,.12) 0 8px,rgba(163,69,53,.04) 8px 16px); }
.agent-action-notice { margin: 0 0 14px; padding: 10px 14px; border-radius: 10px; font-size: .8rem; font-weight: 700; } .agent-action-notice.is-success { color: #235c35; border: 1px solid #8fca9c; background: #e7f6e9; } .agent-action-notice.is-error { color: #8b2f24; border: 1px solid #e5a197; background: #fff0ed; } .spoiler-dialog-actions .btn-ghost { border: 2px solid #fffaf0 !important; color: #fffaf0 !important; background: #2b5b60 !important; box-shadow: 0 2px 0 rgba(0,0,0,.2); } .spoiler-dialog-actions .btn-ghost:hover { background: #3b7378 !important; }
.agent-action-notice { margin: 0 0 14px; padding: 10px 14px; border-radius: 10px; font-size: .8rem; font-weight: 700; } .agent-action-notice.is-success { color: #235c35; border: 1px solid #8fca9c; background: #e7f6e9; } .agent-action-notice.is-error { color: #8b2f24; border: 1px solid #e5a197; background: #fff0ed; } .spoiler-dialog-actions .btn-ghost { border: 2px solid #fffaf0 !important; color: #fffaf0 !important; background: #2b5b60 !important; box-shadow: 0 2px 0 rgba(0,0,0,.2); } .spoiler-dialog-actions .btn-ghost:hover { background: #3b7378 !important; }

/* The graph is deliberately the primary reading surface: an interactive, depth-sorted relation globe. */
.insight-stage .graph-card { display:flex; flex-direction:column; min-height:0!important; height:100%; overflow:hidden; }
.graph-card-title { display:flex; align-items:flex-start; gap:14px; flex:0 0 auto; }
.graph-card-title > span { min-width:34px; margin:4px 0 0!important; color:var(--agent-coral)!important; font-family:var(--font-serif); font-size:2.4rem!important; line-height:.78; }
.graph-card-title h2 { margin:0 0 8px!important; }
.graph-card-title p { max-width:680px; margin:0!important; color:var(--agent-ink-soft)!important; font-size:.78rem; line-height:1.55!important; }
.globe-toolbar { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:10px; flex:0 0 auto; margin:17px 0 10px; }
.globe-toolbar .graph-tools { margin:0; }
.globe-toolbar-status { display:flex; align-items:center; gap:5px; color:rgba(255,250,240,.66); font-size:.68rem; }
.globe-toolbar-status b { color:var(--agent-lime); font-family:var(--font-serif); font-size:1rem; }
.globe-toolbar-status button { margin-left:7px; border:1px solid rgba(255,250,240,.24); border-radius:99px; padding:5px 8px; color:#fffaf0; background:rgba(255,253,247,.08); cursor:pointer; font:inherit; font-size:.64rem; font-weight:800; }
.globe-toolbar-status button:hover { border-color:var(--agent-lime); color:var(--agent-lime); }
.relationship-globe-shell { position:relative; flex:1 1 370px; min-height:360px; overflow:hidden; border:1px solid rgba(184,214,125,.34); border-radius:20px; background:radial-gradient(circle at 50% 45%,#173f4d 0,#102f3b 46%,#09232f 100%); box-shadow:inset 0 0 70px rgba(0,0,0,.26),0 16px 34px rgba(16,44,50,.14); }
.relationship-globe-shell::before { content:''; position:absolute; inset:0; pointer-events:none; opacity:.45; background-image:radial-gradient(rgba(230,255,238,.6) 1px,transparent 1.4px),radial-gradient(rgba(230,255,238,.35) 1px,transparent 1.4px); background-position:18px 26px,86px 62px; background-size:132px 132px,196px 196px; }
.relationship-globe { display:block; position:relative; z-index:1; width:100%; height:100%; touch-action:none; cursor:grab; outline:0; }
.relationship-globe:active { cursor:grabbing; }
.relationship-globe:focus-visible { box-shadow:inset 0 0 0 3px var(--agent-lime); }
.globe-hud { position:absolute; z-index:2; display:flex; align-items:center; gap:8px; pointer-events:none; color:rgba(255,250,240,.56); font-size:.58rem; font-weight:800; letter-spacing:.1em; }
.globe-hud-top { top:14px; left:16px; }.globe-hud-top i { width:22px; height:1px; background:var(--agent-lime); opacity:.7; }
.globe-hud-bottom { right:15px; bottom:13px; gap:11px; letter-spacing:0; }.globe-hud-bottom span { display:inline-flex; align-items:center; gap:5px; }
.legend-dot { width:7px; height:7px; border-radius:50%; background:#ffc65a; box-shadow:0 0 0 3px rgba(255,198,90,.15); }.legend-dot.location { background:#7edbd0; box-shadow:0 0 0 3px rgba(126,219,208,.15); }.legend-dot.event { background:#f58e70; box-shadow:0 0 0 3px rgba(245,142,112,.15); }
.graph-inspector { display:grid; grid-template-columns:minmax(0,1.5fr) minmax(280px,.85fr); gap:11px; flex:0 0 auto; margin-top:11px; }
.graph-evidence,.graph-evidence.graph-evidence-empty { min-height:105px; margin:0!important; border:1px solid rgba(16,44,50,.12); border-left:3px solid var(--agent-coral); border-radius:11px; padding:12px 14px; background:rgba(255,253,247,.82); }
.graph-evidence > span { display:block; margin:0 0 5px; color:var(--agent-coral); font-size:.61rem; font-weight:900; letter-spacing:.1em; }.graph-evidence b { color:var(--agent-ink); font-family:var(--font-serif); font-size:1.03rem; }.graph-evidence small { display:block; margin-top:4px; color:var(--agent-ink-soft); font-size:.65rem; }.graph-evidence p { margin:7px 0 0!important; color:var(--agent-ink-soft)!important; font-size:.69rem; line-height:1.5!important; }.graph-evidence .evidence-jump { margin-left:0; }
.graph-node-list { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:6px; min-width:0; }.graph-node-list button { display:grid; grid-template-columns:auto minmax(0,1fr); align-items:center; gap:2px 6px; min-width:0; border:1px solid rgba(16,44,50,.11); border-radius:10px; padding:8px; color:var(--agent-ink); background:rgba(255,253,247,.78); text-align:left; cursor:pointer; font:inherit; }.graph-node-list button:hover { border-color:var(--agent-coral); background:#fffdf7; }.graph-node-list i { grid-row:1 / span 2; width:7px; height:7px; border-radius:50%; background:#d6e7d1; }.graph-node-list i.CHARACTER { background:#ffc65a; }.graph-node-list i.LOCATION { background:#7edbd0; }.graph-node-list i.ORGANIZATION { background:#9cb5ff; }.graph-node-list i.EVENT { background:#f58e70; }.graph-node-list i.CLUE { background:#d9a5ff; }.graph-node-list span { overflow:hidden; font-size:.67rem; font-weight:800; text-overflow:ellipsis; white-space:nowrap; }.graph-node-list small { overflow:hidden; color:var(--agent-ink-soft); font-size:.57rem; text-overflow:ellipsis; white-space:nowrap; }.graph-footnote { flex:0 0 auto; margin-top:9px; }
/* Full-screen graph inspection keeps dense works usable without shrinking their relation topology. */
.relationship-canvas-dialog { position:fixed; inset:0; z-index:5500; display:grid; grid-template-rows:auto minmax(0,1fr) auto; color:#fffaf0; background:#081f2a; }
.relationship-canvas-head { display:flex; align-items:flex-start; justify-content:space-between; gap:24px; padding:24px 34px 18px; border-bottom:1px solid rgba(255,253,247,.12); background:linear-gradient(100deg,#0d2f3b,#123d49); }.relationship-canvas-head > div:first-child { max-width:760px; }.relationship-canvas-head > div:first-child > span { color:var(--agent-lime); font-size:.62rem; font-weight:900; letter-spacing:.14em; }.relationship-canvas-head h2 { margin:7px 0; font-family:var(--font-serif); font-size:clamp(1.8rem,3.2vw,3.1rem); line-height:.94; letter-spacing:-.06em; }.relationship-canvas-head p { margin:0; color:rgba(255,250,240,.68); font-size:.76rem; line-height:1.6; }.relationship-canvas-head > div:last-child { display:flex; align-items:center; flex-wrap:wrap; justify-content:flex-end; gap:7px; max-width:410px; }.relationship-canvas-head > div:last-child > span { border:1px solid rgba(255,253,247,.16); border-radius:99px; padding:6px 8px; color:rgba(255,250,240,.7); font-size:.63rem; }.relationship-canvas-head button { border:1px solid rgba(255,253,247,.22); border-radius:9px; padding:8px 10px; color:#fffaf0; background:rgba(255,253,247,.06); cursor:pointer; font:inherit; font-size:.68rem; font-weight:800; }.relationship-canvas-head button:hover { border-color:var(--agent-lime); color:var(--agent-lime); }.relationship-canvas-head .relationship-canvas-close { color:#102c32; border-color:var(--agent-lime); background:var(--agent-lime); }
.relationship-canvas-stage { position:relative; min-height:0; overflow:hidden; background:radial-gradient(circle at 50% 43%,#194958 0,#0c2d3a 45%,#061b26 100%); }.relationship-canvas-stage .relationship-globe { width:100%; height:100%; }.canvas-corner-note { position:absolute; left:28px; bottom:23px; color:rgba(255,250,240,.46); font-size:.63rem; font-weight:800; letter-spacing:.08em; pointer-events:none; }
.relationship-canvas-foot { display:flex; align-items:center; justify-content:space-between; gap:18px; min-height:54px; padding:12px 34px; border-top:1px solid rgba(255,253,247,.1); color:rgba(255,250,240,.62); background:#0b2630; }.relationship-canvas-foot .globe-hud-bottom { position:static; }.relationship-canvas-foot > p { margin:0; font-size:.72rem; }.canvas-selection { display:flex; align-items:center; gap:9px; min-width:0; }.canvas-selection span { color:var(--agent-lime); font-size:.62rem; font-weight:800; }.canvas-selection b { overflow:hidden; max-width:280px; color:#fffaf0; font-size:.77rem; text-overflow:ellipsis; white-space:nowrap; }.canvas-selection small { color:rgba(255,250,240,.6); font-size:.64rem; }.canvas-selection button { border:0; border-bottom:1px solid var(--agent-lime); padding:2px 0; color:var(--agent-lime); background:transparent; cursor:pointer; font:inherit; font-size:.66rem; font-weight:800; }
.evidence-source-backdrop { position:fixed; inset:0; z-index:5600; display:grid; place-items:center; padding:20px; background:rgba(7,25,33,.76); backdrop-filter:blur(8px); }.evidence-source-dialog { width:min(100%,640px); border:1px solid rgba(255,253,247,.21); border-radius:20px; padding:27px; color:#fffaf0; background:linear-gradient(145deg,#173e42,#102c32); box-shadow:0 32px 92px rgba(0,0,0,.35); }.evidence-source-dialog > span { color:var(--agent-lime); font-size:.64rem; font-weight:900; letter-spacing:.13em; }.evidence-source-dialog h2 { margin:9px 0 7px; font-family:var(--font-serif); font-size:clamp(1.7rem,3vw,2.5rem); line-height:1; letter-spacing:-.05em; }.evidence-source-dialog > small { color:rgba(255,250,240,.65); }.evidence-source-dialog blockquote { margin:19px 0 13px; border-left:3px solid var(--agent-coral); padding:12px 15px; color:#fffaf0; background:rgba(255,253,247,.09); font-family:var(--font-serif); font-size:1rem; line-height:1.8; }.evidence-source-dialog > p { color:rgba(255,250,240,.69); font-size:.74rem; line-height:1.65; }.evidence-source-dialog > div { display:flex; justify-content:flex-end; gap:9px; margin-top:20px; }.evidence-source-dialog .btn { border-radius:9px; }.evidence-source-dialog .btn-primary { border:0; color:#102c32; background:var(--agent-lime); }
.map-card-title { display:flex; align-items:flex-start; gap:14px; }.map-card-title > span { min-width:34px; margin:4px 0 0!important; color:var(--agent-coral)!important; font-family:var(--font-serif); font-size:2.4rem!important; line-height:.78; }.map-card-title h2 { margin:0 0 8px!important; }.map-card-title p { max-width:700px; margin:0!important; color:var(--agent-ink-soft)!important; font-size:.78rem; line-height:1.55!important; }.map-toolbar { display:flex; align-items:center; justify-content:space-between; gap:12px; margin:17px 0 10px; }.map-toolbar .graph-tools { margin:0; }.map-toolbar small { color:var(--agent-ink-soft); }.map-toolbar small b { color:var(--agent-coral); font-family:var(--font-serif); font-size:1rem; }
.reading-map-card .reading-map-events.story-thread { display:grid; grid-template-columns:1fr; gap:0; max-height:none; margin:0; padding:4px 0 0; overflow:auto; list-style:none; }.story-thread li { position:relative; display:grid!important; grid-template-columns:46px minmax(0,1fr); gap:0 12px; margin:0!important; padding:0!important; border:0!important; border-radius:0!important; background:transparent!important; }.event-marker { position:relative; z-index:1; display:grid; place-items:start center; padding-top:14px; }.event-marker::after { content:''; position:absolute; top:42px; bottom:-13px; width:1px; background:rgba(16,44,50,.15); }.story-thread li:last-child .event-marker::after { display:none; }.event-marker b { display:grid; width:29px; height:29px; place-items:center; border:1px solid rgba(212,97,69,.42); border-radius:50%; color:var(--agent-coral); background:#fffdf7; font-size:.62rem; }.event-card { margin-bottom:12px; border:1px solid rgba(16,44,50,.11); border-radius:12px; padding:13px 14px; background:rgba(255,253,247,.78); }.event-card > div { display:flex; align-items:center; gap:8px; }.event-card em { border-radius:99px; padding:3px 6px; color:#54796d; background:rgba(184,214,125,.22); font-size:.58rem; font-style:normal; font-weight:800; }.event-card strong { min-width:0; color:var(--agent-ink); font-family:var(--font-serif); font-size:.94rem; }.event-card p { margin:8px 0!important; font-size:.72rem!important; line-height:1.55!important; }.event-card footer { display:flex; align-items:center; gap:9px; }.event-card footer small { color:var(--agent-ink-soft); }.event-card footer > span { border-left:2px solid var(--agent-lime); padding-left:6px; color:#54796d; font-size:.62rem; font-weight:800; }.event-card .evidence-jump { margin:0 0 0 auto; }.event-connector { grid-column:2; position:relative; z-index:1; min-height:17px; padding:0 0 3px; color:#54796d; font-size:.6rem; font-weight:800; }.event-connector span { display:inline-block; padding:2px 6px; border-radius:5px; background:rgba(184,214,125,.18); }.map-footnote { margin-top:8px; }
@media (min-width:1080px) { .insight-stage .graph-card { min-height:0!important; }.relationship-globe-shell { min-height:0; }.insight-stage .graph-card .relationship-globe { height:100%!important; margin:0!important; border:0!important; border-radius:0!important; background:transparent!important; } }
@media (max-width:900px) { .relationship-globe-shell { flex-basis:430px; }.graph-inspector { grid-template-columns:1fr; }.graph-node-list { grid-template-columns:repeat(5,minmax(100px,1fr)); overflow-x:auto; padding-bottom:3px; }.graph-node-list button { min-width:100px; } }
@media (max-width:600px) { .graph-card-title > span { font-size:1.9rem!important; }.graph-card-title p { font-size:.7rem; }.globe-toolbar-status { width:100%; justify-content:space-between; }.relationship-globe-shell { flex-basis:390px; min-height:390px; border-radius:15px; }.globe-hud-top { top:11px; left:12px; }.globe-hud-top span:last-child { display:none; }.globe-hud-bottom { right:11px; bottom:10px; gap:7px; font-size:.53rem; }.graph-inspector { margin-top:9px; }.graph-evidence,.graph-evidence.graph-evidence-empty { min-height:0; }.graph-node-list { display:none; } }
</style>

<style scoped>
/* Keep every overlay legible against the deliberately dark relation canvas. */
.globe-toolbar-status { color:rgba(255,253,247,.92); text-shadow:0 1px 2px rgba(0,0,0,.55); }
.globe-hud { border:1px solid rgba(255,253,247,.18); border-radius:7px; padding:5px 7px; color:rgba(255,253,247,.95); background:rgba(4,24,33,.7); box-shadow:0 2px 9px rgba(0,0,0,.18); text-shadow:0 1px 2px rgba(0,0,0,.75); }
.relationship-canvas-head { border-bottom-color:rgba(255,253,247,.18); background:linear-gradient(100deg,#0b2935,#154653); }
.relationship-canvas-head > div:first-child > span { color:#dbf19c; }
.relationship-canvas-head p { color:rgba(255,253,247,.88); }
.relationship-canvas-head > div:last-child > span { border-color:rgba(255,253,247,.28); color:rgba(255,253,247,.92); background:rgba(4,24,33,.34); }
.relationship-canvas-head button { border-color:rgba(255,253,247,.34); color:#fffdf2; background:rgba(255,253,247,.1); }
.canvas-corner-note { border-radius:6px; padding:5px 7px; color:rgba(255,253,247,.92); background:rgba(4,24,33,.74); }
.relationship-canvas-foot { border-top-color:rgba(255,253,247,.16); color:rgba(255,253,247,.9); background:#0a2530; }
.canvas-selection span,.canvas-selection button { color:#dbf19c; }
.canvas-selection b { color:#fffdf2; }
.canvas-selection small { color:rgba(255,253,247,.84); }
</style>
