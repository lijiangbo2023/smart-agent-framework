<template>
  <div class="app-layout">
    <aside class="sidebar">
      <div class="sb-user">
        <div class="sb-av">{{ nickname.charAt(0).toUpperCase() }}</div>
        <div class="sb-info"><div class="sb-name">{{ nickname }}</div><div class="sb-id">@{{ username }}</div></div>
      </div>
      <div class="sb-btn-wrap"><button class="sb-btn" @click="newConversation"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg><span>新对话</span></button></div>
      <div class="sb-list-wrap">
        <div class="sb-list-label">历史对话</div>
        <div class="sb-list">
          <template v-for="g in groupedSessions" :key="g.label"><div class="sb-g-title">{{ g.label }}</div><div v-for="s in g.items" :key="s.sessionId" class="sb-item" :class="{on:s.sessionId===currentSessionId}" @click="selectSession(s)">
    <div class="sb-item-t">{{ getTitle(s) }}</div><div class="sb-item-ts">{{ formatSessionTime(s.time) }}</div>
    <div class="sb-menu" @click.stop>
      <button class="sb-menu-btn" @click.stop="toggleMenu(s)"><svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg></button>
      <div class="sb-menu-drop" v-if="s._menuOpen">
        <div class="sb-menu-item" @click.stop="pinSession(s)"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="17" x2="12" y2="22"/><path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"/></svg>{{ s._pinned ? '取消置顶' : '置顶' }}</div>
        <div class="sb-menu-item" @click.stop="renameSession(s)"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>重命名</div>
        <div class="sb-menu-item danger" @click.stop="confirmDelete(s)"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v4"/></svg>删除</div>
      </div>
    </div>
  </div></template>
          <div v-if="sessions.length===0" class="sb-empty">暂无记录</div>
        </div>
      </div>
      <div class="sb-foot">
        <div class="sb-logout" @click="handleLogout"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg><span>退出登录</span></div>
        <div class="sb-foot-info"><span class="sfi-brand">Smart Agent</span> <span class="sfi-ver">v1.0</span></div>
        <div class="sb-foot-author">Jiangbo Li · lijiangbo2023@163.com</div>
      </div>
    </aside>
    <main class="chat-main"><ChatPanel :messages="currentMessages" :loading="loading" :streaming-text="streamingText" :answer-text="answerText" :is-answering="isAnswering" :route-steps="routeSteps" :current-session-title="currentSessionTitle" :nickname="nickname" @send="handleSend" @stop="handleStop" @feedback="handleFeedback" @clear="newConversation" /></main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'; import { useRouter } from 'vue-router'; import { ElMessage, ElMessageBox } from 'element-plus'
import { getHistory, getConversation, chatStream, sendFeedback, deleteConversation, setAuthUserId } from '../api/agent.js'; import ChatPanel from '../components/ChatPanel.vue'
const router = useRouter(); const stored = JSON.parse(localStorage.getItem('user')||'{}')
const userId = ref(stored.userId||stored.username||'user001'); const nickname = ref(stored.nickname||stored.username||'User'); const username = ref(stored.username||'user001'); setAuthUserId(userId.value)
const sessions = ref([]); const currentSessionId = ref(''); const currentMessages = ref([]); const loading = ref(false); const streamingText = ref(''); const answerText = ref(''); const isAnswering = ref(false); const routeSteps = ref([]); const abortController = ref(null)
let thinkingFull = ''; let thinkingTools = []; let thinkingStarted = 0; let routeSeq = 0; let currentReasoningAgent = ''
const customTitles = ref(JSON.parse(localStorage.getItem('sessionTitles')||'{}'))
function saveTitles() { localStorage.setItem('sessionTitles',JSON.stringify(customTitles.value)) }
function getTitle(s) { return customTitles.value[s.sessionId] || s.preview || '新对话' }
const currentSessionTitle = computed(() => { const s = sessions.value.find(s=>s.sessionId===currentSessionId.value); return s?getTitle(s):'' })
const groupedSessions = computed(() => { const pinned = sessions.value.filter(s=>s._pinned); const unpinned = sessions.value.filter(s=>!s._pinned); const groups = []; if(pinned.length) groups.push({label:'置顶',items:pinned}); const g = new Map(); unpinned.forEach(s=>{ const l=getTimeLabel(s.time); if(!g.has(l)) g.set(l,[]); g.get(l).push(s) }); ['今天','昨天','近7天','近30天','更早'].filter(l=>g.has(l)).forEach(l=>groups.push({label:l,items:g.get(l)})); return groups })
function getTimeLabel(t) { if(!t) return'更早'; const d=Math.floor((Date.now()-new Date(t.replace(' ','T')+'+08:00'))/86400000); if(d===0) return'今天'; if(d===1) return'昨天'; if(d<7) return'近7天'; if(d<30) return'近30天'; return'更早' }
function formatSessionTime(t) { if(!t) return''; const d=new Date(t.replace(' ','T')+'+08:00'); const m=Math.floor((Date.now()-d)/60000); if(m<1) return'刚刚'; if(m<60) return m+'分钟前'; const h=Math.floor(m/60); if(h<24) return h+'小时前'; return Math.floor(h/24)+'天前' }
function gSid() { return crypto.randomUUID().replace(/-/g,'') }
async function loadHistory() { try { const r=await getHistory(userId.value); const d=(r.data.data||{}).records||[]; const m=new Map(); d.forEach(d=>{ if(!d.sessionId) return; const ex=m.get(d.sessionId); if(!ex) { m.set(d.sessionId,{sessionId:d.sessionId,preview:(d.userInput||'新对话').substring(0,30),time:d.gmtCreate,_earliestTime:d.gmtCreate}) } else if(d.gmtCreate<ex._earliestTime) { ex._earliestTime=d.gmtCreate; ex.preview=(d.userInput||'新对话').substring(0,30) } }); sessions.value=Array.from(m.values()) } catch(e){console.error(e)} }
async function selectSession(s) { currentSessionId.value=s.sessionId; streamingText.value=''; try { const r=await getConversation(userId.value,s.sessionId); currentMessages.value=(r.data.data||[]).map(m=>({id:m.id,role:'pair',userInput:m.userInput,agentOutput:m.agentOutput,status:m.status,feedbackType:m.feedbackType,time:m.gmtCreate,_expanded:false,_showThinking:false,_thinking:'',_tools:[]})) } catch(e){console.error(e)} }
function newConversation() { currentSessionId.value=gSid(); currentMessages.value=[]; streamingText.value=''; answerText.value=''; isAnswering.value=false; routeSteps.value=[]; thinkingFull=''; thinkingTools=[]; thinkingStarted=0; routeSeq=0; currentReasoningAgent='' }
async function handleSend(msg) {
  if(!msg.trim()) return; if(!currentSessionId.value) currentSessionId.value=gSid()
  currentMessages.value.push({id:Date.now(),role:'pair',userInput:msg,agentOutput:null,status:1,time:new Date(),_thinking:'',_tools:[],_showThinking:false,_thinkSec:0})
  loading.value=true; streamingText.value=''; answerText.value=''; isAnswering.value=false; routeSteps.value=[]; thinkingFull=''; thinkingTools=[]; thinkingStarted=0; routeSeq=0; currentReasoningAgent=''
  const ctrl=new AbortController(); abortController.value=ctrl; const seen=new Set()
  try {
    const resp=await chatStream(userId.value,currentSessionId.value,msg,ctrl.signal); if(!resp.ok) throw new Error(`请求失败 (HTTP ${resp.status})`)
    const reader=resp.body.getReader(); const dec=new TextDecoder(); let buf=''
    while(true) { const {done,value}=await reader.read(); if(done) break; buf+=dec.decode(value,{stream:true}); const lines=buf.split('\n'); buf=lines.pop()||''; for(const l of lines) { if(!l.startsWith('data:')) continue; try { pe(JSON.parse(l.substring(5).trim()),seen) } catch {} } }
    if(buf.trim()) { for(const l of buf.split('\n')) { if(!l.startsWith('data:')) continue; try { pe(JSON.parse(l.substring(5).trim()),seen) } catch {} } }
    const last=currentMessages.value[currentMessages.value.length-1]
    if(last) { last.agentOutput=answerText.value||streamingText.value; last.status=2; last._thinking=thinkingFull; last._tools=[...thinkingTools]; last._showThinking=thinkingFull.length>0; last._thinkSec=thinkingStarted>0?Math.round((Date.now()-thinkingStarted)/1000):0; last._routeSteps=[...routeSteps.value] }
    loadHistory()
  } catch(e) { if(e.name!=='AbortError') { ElMessage.error(e.message||'请求失败') }; const last=currentMessages.value[currentMessages.value.length-1]; if(last&&last.status===1) { last.agentOutput=answerText.value||streamingText.value||'已中断'; last.status=-1; last._thinking=thinkingFull; last._tools=[...thinkingTools]; last._routeSteps=[...routeSteps.value] } }
  finally { loading.value=false; abortController.value=null }
}
function pe(data,seen) {
    // Handle nested JSON tool results (sub-agent events) — only for legacy compatibility
    if (data.type==='TOOL_RESULT' && data.content && data.content.startsWith('{')) {
        try { const nested=JSON.parse(data.content); if (nested.type && nested.message) { pn(nested,data.agentName,seen); return } } catch {}
    }
    if (!data.content || !data.content.trim()) return

    switch (data.type) {
        case 'REASONING':
            if (thinkingStarted===0) thinkingStarted=Date.now()
            if (currentReasoningAgent!=='Supervisor') { thinkingFull+=thinkingFull?'\n\n':''; currentReasoningAgent='Supervisor' }
            thinkingFull += data.content
            streamingText.value = thinkingFull
            break
        case 'TOOL_USE':
            if (!thinkingTools.includes(data.content)) thinkingTools.push(data.content)
            routeSteps.value.push({id:++routeSeq, type:'tool_use', agentName:data.agentName||'Agent', toolName:data.content, time:Date.now()})
            break
        case 'SUB_REASONING': {
            if (thinkingStarted===0) thinkingStarted=Date.now()
            const an=data.agentName||'SubAgent'
            if (currentReasoningAgent!==an) { thinkingFull+=thinkingFull?'\n\n':''; currentReasoningAgent=an }
            thinkingFull += data.content
            streamingText.value = thinkingFull
            break }
        case 'TOOL_RESULT':
            if (!isAnswering.value) { isAnswering.value=true; answerText.value='' }
            if (!data.content.startsWith('{')) { answerText.value = data.content }
            routeSteps.value.push({id:++routeSeq, type:'tool_result', agentName:data.agentName||'Tool', content:data.content.substring(0,300), time:Date.now()})
            break
        case 'SUB_RESULT':
            if (!isAnswering.value) { isAnswering.value=true; answerText.value='' }
            if (!data.content.startsWith('{')) { answerText.value = data.content }
            routeSteps.value.push({id:++routeSeq, type:'sub_result', agentName:data.agentName||'SubAgent', content:data.content.substring(0,300), time:Date.now()})
            break
        case 'AGENT_RESULT':
            if (!isAnswering.value) { isAnswering.value=true; answerText.value='' }
            if (!data.content.startsWith('{')) { answerText.value = data.content }
            break
        default:
            if (data.type==='TOOL_RESULT' && data.isLast) { if (!isAnswering.value) { isAnswering.value=true; answerText.value='' }; answerText.value=data.content }
    }
}
function pn(nested,an,seen) {
    const blocks=nested.message?.content||[]
    for (const b of blocks) {
        if (b.type==='tool_use' && b.name && b.name!=='__fragment__') {
            if (!thinkingTools.includes(b.name)) thinkingTools.push(b.name)
            routeSteps.value.push({id:++routeSeq, type:'tool_use', agentName:nested.agentName||an||'Agent', toolName:b.name, time:Date.now()})
        }
    }
    const tb=blocks.filter(b=>b.type==='text'&&b.text)
    const t=tb.map(b=>b.text).join('')
    if (!t) return
    if (nested.type==='REASONING') {
        if (thinkingStarted===0) thinkingStarted=Date.now()
        const nan=nested.agentName||an||'SubAgent'
        if (currentReasoningAgent!==nan) { thinkingFull+=thinkingFull?'\n\n':''; currentReasoningAgent=nan }
        thinkingFull += t
        streamingText.value = thinkingFull
        return
    }
    if (nested.type==='AGENT_RESULT') {
        if (!isAnswering.value) { isAnswering.value=true; answerText.value='' }
        answerText.value = t
    }
}
function handleStop() { if(abortController.value) { abortController.value.abort(); abortController.value=null } }
async function handleFeedback({messageId,action,currentStatus}) { try { const r=await sendFeedback(messageId,action,currentStatus); const m=currentMessages.value.find(m=>m.id===messageId); if(m) { const s=r.data.data; m.feedbackType=s==='like'?1:s==='dislike'?2:0; ElMessage.success(s==='like'?'已点赞':s==='dislike'?'已点踩':'已取消') } } catch { ElMessage.error('反馈失败') } }
function toggleMenu(s) { sessions.value.forEach(x => { if (x !== s) x._menuOpen = false }); s._menuOpen = !s._menuOpen }
function pinSession(s) { s._pinned = !s._pinned; s._menuOpen = false; ElMessage.success(s._pinned ? '已置顶' : '已取消置顶') }
async function renameSession(s) {
    s._menuOpen = false
    try {
        const { value } = await ElMessageBox.prompt('请输入新标题', '重命名', {
            confirmButtonText:'确定', cancelButtonText:'取消',
            inputValue: getTitle(s), inputPlaceholder:'输入会话标题'
        })
        if (value && value.trim()) {
            customTitles.value[s.sessionId] = value.trim()
            saveTitles()
            ElMessage.success('标题已更新')
        }
    } catch { /* cancelled */ }
}
async function confirmDelete(s) {
  s._menuOpen = false
  try { await ElMessageBox.confirm('确定删除该对话？删除后不可恢复。', '删除确认', { confirmButtonText:'删除', cancelButtonText:'取消', type:'warning', confirmButtonClass:'el-button--danger' }); await deleteConversation(userId.value, s.sessionId); sessions.value = sessions.value.filter(x => x.sessionId !== s.sessionId); if (currentSessionId.value === s.sessionId) newConversation(); ElMessage.success('已删除') } catch { /* cancelled */ }
}
function handleLogout() { localStorage.clear(); router.push('/login') }
onMounted(()=>{ loadHistory(); newConversation(); document.addEventListener('click', ()=>{ sessions.value.forEach(s=>s._menuOpen=false) }) })
</script>

<style scoped>
.app-layout { display:flex; height:100vh; overflow:hidden; background:#f8f9fc; }
.sidebar { width:260px; background:#fff; border-right:1px solid #eef0f2; display:flex; flex-direction:column; flex-shrink:0; }
.sb-user { display:flex; align-items:center; gap:10px; padding:20px 14px; }
.sb-av { width:40px; height:40px; border-radius:12px; background:linear-gradient(135deg,#93c5fd,#60a5fa); color:#fff; display:flex; align-items:center; justify-content:center; font-size:16px; font-weight:700; flex-shrink:0; box-shadow:0 2px 8px rgba(96,165,250,.25); }
.sb-info { min-width:0; }
.sb-name { font-size:13px; font-weight:600; color:#1a1a2e; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.sb-id { font-size:11px; color:#9ca3af; margin-top:1px; }
.sb-btn-wrap { padding:12px; }
.sb-btn { width:100%; display:flex; align-items:center; justify-content:center; gap:6px; padding:10px 0; border:1px solid #e5e7eb; border-radius:10px; background:#fff; color:#3b82f6; font-size:13px; font-weight:500; cursor:pointer; transition:all .2s; }
.sb-btn:hover { background:#eff6ff; border-color:#c7d2fe; }
.sb-list-wrap { flex:1; overflow:hidden; display:flex; flex-direction:column; }
.sb-list-label { font-size:10px; font-weight:700; color:#9ca3af; text-transform:uppercase; letter-spacing:.08em; padding:8px 16px 6px; }
.sb-list { flex:1; overflow-y:auto; padding:0 8px; }
.sb-g-title { font-size:10px; font-weight:600; color:#d1d5db; padding:8px 6px 4px; }
.sb-item { padding:9px 10px; border-radius:8px; cursor:pointer; margin-bottom:1px; transition:all .15s; position:relative; display:flex; align-items:center; gap:8px; }
.sb-item > :first-child { flex:1; min-width:0; }
.sb-item:hover { background:#eff6ff; }
.sb-item.on { background:rgba(99,102,241,.06); border-left:3px solid #38bdf8; }
.sb-item-t { font-size:12px; color:#4b5563; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.sb-item-ts { font-size:10px; color:#d1d5db; margin-top:2px; }
.sb-menu { position:relative; flex-shrink:0; }
.sb-menu-btn { opacity:0; width:22px; height:22px; border:none; border-radius:4px; background:transparent; color:#d1d5db; cursor:pointer; display:flex; align-items:center; justify-content:center; transition:all .15s; }
.sb-item:hover .sb-menu-btn { opacity:1; }
.sb-menu-btn:hover { background:#f3f4f6; color:#6b7280; }
.sb-menu-drop { position:absolute; right:0; top:100%; margin-top:4px; background:#fff; border:1px solid #e5e7eb; border-radius:8px; box-shadow:0 4px 16px rgba(0,0,0,.08); z-index:100; min-width:120px; padding:4px; }
.sb-menu-item { display:flex; align-items:center; gap:8px; padding:8px 12px; border-radius:6px; font-size:12px; color:#4b5563; cursor:pointer; transition:background .1s; }
.sb-menu-item:hover { background:#f9fafb; }
.sb-menu-item.danger { color:#ef4444; }
.sb-menu-item.danger:hover { background:#fef2f2; }
.sb-empty { text-align:center; padding:40px 0; color:#d1d5db; font-size:12px; }
.sb-foot { padding:4px 16px 14px; }
.sb-logout { display:flex; align-items:center; gap:6px; padding:8px 10px; border-radius:8px; cursor:pointer; color:#9ca3af; font-size:12px; transition:all .15s; margin-bottom:12px; }
.sb-logout:hover { background:#fef2f2; color:#ef4444; }
.sb-foot-info { text-align:center; font-size:11px; margin-bottom:3px; }
.sfi-brand { color:#b0b7c3; font-weight:400; }
.sfi-ver { color:#b0b7c3; font-weight:400; }
.sb-foot-author { font-size:10px; color:#b0b7c3; text-align:center; }
.chat-main { flex:1; display:flex; flex-direction:column; min-width:0; }
</style>
