<template>
  <div class="cp">
    <div class="hdr"><div class="hdr-l"><span class="hdr-dot"></span><span class="hdr-t" v-if="currentSessionTitle">{{ currentSessionTitle }}</span><span class="hdr-t dim" v-else>新对话</span></div></div>

    <div class="msgs" ref="mc">
      <div v-if="messages.length===0&&!loading" class="welcome">
        <div class="w-logo">
    <svg width="32" height="32" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="13" fill="#0f172a"/><path d="M16 32V18l6 6 6-6v14" stroke="#38bdf8" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="M32 32V18l-6 6-6-6v14" stroke="#60a5fa" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/><circle cx="22" cy="14" r="2" fill="#38bdf8"/><circle cx="26" cy="14" r="2" fill="#60a5fa"/></svg>
  </div>
        <h2>你好，<span class="grad">{{ nickname }}</span></h2>
        <p>AI 多智能体协作平台 · 回答、编码、分析</p>
        <div class="sugs">
          <div class="sug" v-for="(s,i) in sugs" :key="i" @click="fill(s.text)"><span class="sug-emoji">{{ s.emoji }}</span><div><div class="sug-l">{{ s.label }}</div><div class="sug-d">{{ s.text }}</div></div></div>
        </div>
      </div>

      <div v-for="msg in messages" :key="msg.id" class="mg">
        <div class="mr user"><div class="bbl user-bbl">{{ msg.userInput }}</div><div class="av user-av">{{ (nickname||'U').charAt(0).toUpperCase() }}</div></div>
        <div class="mr agent" v-if="msg.agentOutput&&msg.status!==1">
          <div class="av agent-av">
    <svg width="22" height="22" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="13" fill="#0f172a"/><path d="M16 32V18l6 6 6-6v14" stroke="#38bdf8" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="M32 32V18l-6 6-6-6v14" stroke="#60a5fa" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/><circle cx="22" cy="14" r="2" fill="#38bdf8"/><circle cx="26" cy="14" r="2" fill="#60a5fa"/></svg>
  </div>
          <div class="mb">
            <div class="route-done" v-if="msg._routeSteps && msg._routeSteps.length">
              <div class="route-toggle" @click="msg._showRoute=!msg._showRoute">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M12 2v4"/><path d="M12 18v4"/><path d="M2 12h4"/><path d="M18 12h4"/><path d="M9.5 2.8l2.5 4.3"/><path d="M12 16.9l2.5 4.3"/><path d="M2.8 9.5l4.3 2.5"/><path d="M16.9 12l4.3 2.5"/></svg>
                <span>执行路线 ({{ msg._routeSteps.length }} 步)</span>
                <svg class="chev" :class="{open:msg._showRoute}" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
              </div>
              <div class="route-steps" v-show="msg._showRoute">
                <div class="rs-item" v-for="rs in msg._routeSteps" :key="rs.id" :class="'rs-'+rs.type">
                  <span class="rs-icon">{{ routeIcon(rs.type) }}</span><span class="rs-body"><span class="rs-agent">{{ rs.agentName }}</span><span class="rs-text">{{ routeText(rs) }}</span></span>
                </div>
              </div>
            </div>
            <div class="tb" v-if="msg._thinking&&msg._thinking.length>0">
              <div class="tb-toggle" @click="msg._showThinking=!msg._showThinking">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>
                <span>思考过程</span><span class="tb-sec" v-if="msg._thinkSec">{{ msg._thinkSec }}秒</span>
                <span class="tb-tools" v-if="msg._tools&&msg._tools.length">{{ msg._tools.join('·') }}</span>
                <svg class="chev" :class="{open:msg._showThinking}" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
              </div>
              <div class="tb-body" v-show="msg._showThinking"><div class="tb-text">{{ msg._thinking }}</div></div>
            </div>
            <div class="bbl agent-bbl" v-html="md(msg.agentOutput)"></div>
            <div class="mf"><span class="mt">{{ ft(msg.time) }}</span><div class="fb" v-if="msg.id&&msg.status===2"><button class="fbb" :class="{on:msg.feedbackType===1}" @click="$emit('feedback',{messageId:msg.id,action:'like',currentStatus:msg.feedbackType===1?'like':msg.feedbackType===2?'dislike':'none'})"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"/></svg></button><button class="fbb" :class="{on:msg.feedbackType===2}" @click="$emit('feedback',{messageId:msg.id,action:'dislike',currentStatus:msg.feedbackType===1?'like':msg.feedbackType===2?'dislike':'none'})"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zM17 2h3a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-3"/></svg></button></div></div>
          </div>
        </div>
        <div class="mr agent" v-else-if="msg.status===1">
          <div class="av agent-av" :class="{pulse:!isAnswering}">
    <svg width="22" height="22" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="13" fill="#0f172a"/><path d="M16 32V18l6 6 6-6v14" stroke="#38bdf8" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/><path d="M32 32V18l-6 6-6-6v14" stroke="#60a5fa" stroke-width="2.2" fill="none" stroke-linecap="round" stroke-linejoin="round"/><circle cx="22" cy="14" r="2" fill="#38bdf8"/><circle cx="26" cy="14" r="2" fill="#60a5fa"/></svg>
  </div>
          <div class="mb">
            <!-- Route timeline live -->
            <div class="route-live" v-if="routeSteps && routeSteps.length">
              <div class="rs-item" v-for="rs in routeSteps" :key="rs.id" :class="'rs-'+rs.type">
                <span class="rs-icon">{{ routeIcon(rs.type) }}</span><span class="rs-body"><span class="rs-agent">{{ rs.agentName }}</span><span class="rs-text">{{ routeText(rs) }}</span></span>
              </div>
            </div>
            <div class="live-tb" v-if="streamingText">
              <div class="live-tb-header" @click="showLiveThinking=!showLiveThinking">
                <span class="live-dot"></span><span class="live-tb-label">深度思考中...</span>
                <svg class="live-chev" :class="{open:showLiveThinking}" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
              </div>
              <div class="live-tb-body" v-show="showLiveThinking">
                <div class="live-tb-text">{{ streamingText }}</div>
              </div>
            </div>
            <div class="live" v-if="!streamingText && !answerText"><span class="ld"></span><span class="ld"></span><span class="ld"></span><span class="live-t">深度思考中...</span></div>
            <div class="bbl agent-bbl stream-bbl" v-if="answerText"><div v-html="md(answerText)"></div><span class="cursor"></span></div>
          </div>
        </div>
      </div>
    </div>

    <div class="input-area">
      <div class="ib" :class="{on:focus}">
        <textarea v-model="text" :placeholder="loading?'回复中...':'输入消息，Enter 发送'" :disabled="loading" @keydown.enter.exact.prevent="send" @focus="focus=true" @blur="focus=false" @input="resize" ref="ta" rows="1"></textarea>
        <button class="stop-btn" v-if="loading" @click="$emit('stop')" title="中断回复"><svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="4" y="4" width="16" height="16" rx="2"/></svg></button>
        <button class="snd" v-else :class="{go:text.trim()}" @click="send" :disabled="!text.trim()"><svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg></button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'; import { marked } from 'marked'; import DOMPurify from 'dompurify'
const p = defineProps({ messages:Array, loading:Boolean, streamingText:String, answerText:String, isAnswering:Boolean, routeSteps:Array, currentSessionTitle:String, nickname:{type:String,default:'User'} })
const emit = defineEmits(['send','stop','feedback','clear']); const text = ref(''); const mc = ref(null); const ta = ref(null); const focus = ref(false); const showLiveThinking = ref(true)
const sugs = [{emoji:'💻',label:'代码生成',text:'用 Python 写一个快速排序算法'},{emoji:'📚',label:'知识问答',text:'解释 RAG 检索增强生成的原理'},{emoji:'🔍',label:'知识检索',text:'Supervisor 多智能体模式是什么'},{emoji:'📊',label:'数据分析',text:'帮我分析销售数据趋势'}]
function fill(t) { text.value = t; ta.value?.focus(); resize() }
function send() { if(!text.value.trim()||p.loading) return; emit('send',text.value); text.value=''; nextTick(resize) }
function resize() { const e=ta.value; if(!e) return; e.style.height='auto'; e.style.height=Math.min(e.scrollHeight,120)+'px' }
function isLong(t) { return t&&t.length>800 }
function md(t) { try { return DOMPurify.sanitize(marked(t||'',{breaks:true})) } catch { return DOMPurify.sanitize(t||'') } }
function ft(t) { if(!t) return''; const d=new Date(t.replace?t.replace(' ','T')+'+08:00':t); if(isNaN(d.getTime())) return t; const m=Math.floor((Date.now()-d)/60000); if(m<1) return'刚刚'; if(m<60) return m+'分钟前'; const h=Math.floor(m/60); if(h<24) return h+'小时前'; return d.toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',hour12:false}) }
function routeIcon(type) { const icons={tool_use:'🔧',tool_result:'📋',sub_result:'✅',REASONING:'🧠',SUB_REASONING:'🤔',AGENT_RESULT:'📝'}; return icons[type]||'•' }
function routeText(rs) { return rs.toolName?'调用: '+rs.toolName:rs.content||'' }
watch(()=>p.messages.length,()=>nextTick(()=>{ if(mc.value) mc.value.scrollTop=mc.value.scrollHeight }))
watch(()=>p.streamingText,(v)=>{ nextTick(()=>{ if(mc.value) mc.value.scrollTop=mc.value.scrollHeight }); if(v&&v.length>0) showLiveThinking.value=true })
watch(()=>p.answerText,(v)=>{ nextTick(()=>{ if(mc.value) mc.value.scrollTop=mc.value.scrollHeight }); if(v&&v.length>0) showLiveThinking.value=false })
</script>

<style scoped>
.cp { display:flex; flex-direction:column; height:100%; background:#fafbfc; }
.hdr { display:flex; align-items:center; justify-content:space-between; padding:0 24px; height:52px; background:#fff; border-bottom:1px solid #eef0f2; flex-shrink:0; }
.hdr-l { display:flex; align-items:center; gap:10px; min-width:0; }
.hdr-dot { width:7px; height:7px; border-radius:50%; background:#22c55e; }
.hdr-t { font-size:14px; font-weight:600; color:#111827; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.hdr-t.dim { color:#9ca3af; font-weight:400; }
.hdr-btn { display:flex; align-items:center; padding:6px 10px; border:1px solid #e5e7eb; border-radius:8px; background:#fff; color:#9ca3af; cursor:pointer; transition:all .15s; }
.hdr-btn:hover { background:#f9fafb; color:#6b7280; }

.msgs { flex:1; overflow-y:auto; padding:24px 0; }
.welcome { display:flex; flex-direction:column; align-items:center; justify-content:center; height:100%; padding:40px 20px; }
.w-logo { width:64px; height:64px; display:flex; align-items:center; justify-content:center; margin-bottom:20px; filter:drop-shadow(0 4px 12px rgba(99,102,241,.25)); }
.welcome h2 { font-size:22px; font-weight:700; color:#111827; margin:0 0 8px; }
.grad { background:linear-gradient(135deg,#3b82f6,#2563eb); -webkit-background-clip:text; -webkit-text-fill-color:transparent; }
.welcome p { color:#9ca3af; font-size:13px; margin:0 0 32px; }
.sugs { display:grid; grid-template-columns:1fr 1fr; gap:10px; width:100%; max-width:520px; }
.sug { display:flex; align-items:center; gap:10px; padding:14px 16px; background:#fff; border:1px solid #f1f1f1; border-radius:12px; cursor:pointer; transition:all .2s; }
.sug:hover { border-color:#c7d2fe; box-shadow:0 4px 16px rgba(99,102,241,.06); transform:translateY(-2px); }
.sug-emoji { font-size:22px; }
.sug-l { font-size:12px; font-weight:600; color:#3b82f6; }
.sug-d { font-size:11px; color:#9ca3af; margin-top:2px; }

.mg { margin-bottom:28px; animation:fadeUp .3s ease; }
@keyframes fadeUp { from{opacity:0;transform:translateY(10px)} to{opacity:1;transform:translateY(0)} }
.mr { display:flex; align-items:flex-start; padding:0 24px; margin-bottom:6px; max-width:900px; margin-left:auto; margin-right:auto; width:100%; }
.mr.user { justify-content:flex-end; }
.mr.agent { justify-content:flex-start; }

.av { width:36px; height:36px; border-radius:12px; display:flex; align-items:center; justify-content:center; font-weight:700; color:#fff; flex-shrink:0; overflow:hidden; }
.user-av { background:linear-gradient(135deg,#93c5fd,#60a5fa); color:#fff; margin-left:12px; font-size:14px; box-shadow:0 2px 8px rgba(96,165,250,.25); }
.agent-av { margin-right:12px; }
.agent-av.pulse { animation:avPulse 2s ease-in-out infinite; }
@keyframes avPulse { 0%,100%{box-shadow:0 0 0 0 rgba(99,102,241,.3)} 50%{box-shadow:0 0 0 8px rgba(99,102,241,0)} }

.mb { flex:1; min-width:0; max-width:740px; }
.bbl { padding:12px 16px; font-size:14px; line-height:1.7; word-break:break-word; }
.user-bbl { background:linear-gradient(135deg,#bfdbfe,#dbeafe); color:#1e3a5f; border-radius:18px 18px 2px 18px; display:inline-block; max-width:80%; font-size:14px; box-shadow:0 1px 3px rgba(59,130,246,.08); }
.agent-bbl { background:#fff; color:#1f2937; border-radius:0 14px 14px 14px; border:none; box-shadow:0 2px 8px rgba(0,0,0,.05); }
.stream-bbl { position:relative; }
.cursor { display:inline-block; width:2px; height:16px; background:#3b82f6; margin-left:2px; vertical-align:text-bottom; animation:blink .8s infinite; }
@keyframes blink { 0%,100%{opacity:1} 50%{opacity:0} }

.agent-bbl:deep(pre) { background:#0f172a; color:#e2e8f0; border-radius:8px; overflow:hidden; margin:10px 0; }
.agent-bbl:deep(pre code) { display:block; padding:12px 16px; overflow-x:auto; font-size:13px; line-height:1.6; font-family:'SF Mono',Menlo,Consolas,monospace; }
.agent-bbl:deep(code) { background:#eff6ff; color:#2563eb; padding:2px 6px; border-radius:4px; font-size:12px; }
.agent-bbl:deep(p) { margin:4px 0; }
.agent-bbl:deep(ul),.agent-bbl:deep(ol) { padding-left:20px; margin:4px 0; }
.agent-bbl:deep(li) { margin:2px 0; }
.agent-bbl:deep(table) { border-collapse:collapse; width:100%; margin:8px 0; font-size:13px; }
.agent-bbl:deep(th),.agent-bbl:deep(td) { border:1px solid #e5e7eb; padding:8px 12px; text-align:left; }
.agent-bbl:deep(th) { background:#f9fafb; font-weight:600; }
.agent-bbl:deep(blockquote) { border-left:3px solid #c7d2fe; padding:8px 12px; margin:8px 0; background:rgba(99,102,241,.03); color:#6b7280; border-radius:0 6px 6px 0; }
.agent-bbl:deep(a) { color:#3b82f6; }

.tb { margin-bottom:8px; background:#eff6ff; border:1px solid #bfdbfe; border-radius:10px; overflow:hidden; }
.tb-toggle { display:flex; align-items:center; gap:8px; padding:8px 14px; cursor:pointer; font-size:12px; color:#3b82f6; user-select:none; transition:background .15s; }
.tb-toggle:hover { background:#dbeafe; }
.tb-sec { color:#60a5fa; font-size:11px; }
.tb-tools { color:#60a5fa; font-size:11px; margin-left:auto; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:180px; }
.chev { transition:transform .2s; flex-shrink:0; }
.chev.open { transform:rotate(180deg); }
.tb-body { padding:8px 14px 12px; max-height:280px; overflow-y:auto; }
.tb-text { font-size:12px; color:#3b82f6; white-space:pre-wrap; word-break:break-word; line-height:1.5; opacity:.8; }

.live { display:flex; align-items:center; gap:6px; padding:12px 16px; }
.ld { width:6px; height:6px; border-radius:50%; background:#3b82f6; animation:bounce 1.4s infinite; }
.ld:nth-child(2) { animation-delay:.2s; } .ld:nth-child(3) { animation-delay:.4s; }
@keyframes bounce { 0%,80%,100%{transform:scale(.6);opacity:.3} 40%{transform:scale(1);opacity:1} }
.live-t { font-size:13px; color:#9ca3af; margin-left:4px; }

.live-tb { background:#eff6ff; border:1px solid #bfdbfe; border-radius:10px; overflow:hidden; margin-bottom:12px; animation:fadeUp .25s ease; }
.live-tb-header { display:flex; align-items:center; gap:8px; padding:8px 14px; cursor:pointer; user-select:none; transition:background .15s; }
.live-tb-header:hover { background:#dbeafe; }
.live-dot { width:7px; height:7px; border-radius:50%; background:#3b82f6; animation:pulse-dot 1.2s ease-in-out infinite; }
@keyframes pulse-dot { 0%,100%{opacity:.4;transform:scale(.8)} 50%{opacity:1;transform:scale(1.2)} }
.live-tb-label { font-size:12px; font-weight:600; color:#3b82f6; }
.live-chev { flex-shrink:0; color:#60a5fa; transition:transform .2s; }
.live-chev.open { transform:rotate(180deg); }
.live-tb-body { padding:6px 14px 10px; max-height:260px; overflow-y:auto; }
.live-tb-text { font-size:12px; color:#3b82f6; white-space:pre-wrap; word-break:break-word; line-height:1.5; opacity:.85; }

/* Route timeline — live streaming */
.route-live { display:flex; flex-direction:column; gap:1px; margin-bottom:10px; background:#f8fafc; border:1px solid #e8ecf1; border-radius:10px; padding:6px 10px; animation:fadeUp .25s ease; }
.route-live .rs-item { display:flex; align-items:flex-start; gap:8px; padding:4px 6px; border-radius:6px; font-size:12px; transition:background .15s; }
.route-live .rs-item:first-child { background:#eff6ff; }
.rs-icon { flex-shrink:0; width:20px; text-align:center; font-size:12px; }
.rs-body { min-width:0; display:flex; align-items:baseline; gap:6px; flex-wrap:wrap; }
.rs-agent { font-weight:600; font-size:11px; padding:1px 6px; border-radius:4px; background:#e5e7eb; color:#6b7280; white-space:nowrap; flex-shrink:0; }
.rs-tool_use .rs-agent { background:#dbeafe; color:#3b82f6; }
.rs-sub_result .rs-agent, .rs-tool_result .rs-agent { background:#dcfce7; color:#16a34a; }
.rs-text { color:#4b5563; font-size:11px; word-break:break-word; line-height:1.4; }

/* Route timeline — completed */
.route-done { margin-bottom:8px; background:#f8fafc; border:1px solid #e8ecf1; border-radius:10px; overflow:hidden; }
.route-toggle { display:flex; align-items:center; gap:8px; padding:8px 14px; cursor:pointer; font-size:12px; color:#6b7280; user-select:none; transition:background .15s; }
.route-toggle:hover { background:#f1f5f9; }
.route-steps { padding:4px 10px 10px; display:flex; flex-direction:column; gap:1px; }
.route-steps .rs-item { display:flex; align-items:flex-start; gap:8px; padding:4px 6px; border-radius:6px; font-size:12px; }

.mf { display:flex; align-items:center; justify-content:space-between; margin-top:8px; }
.mt { font-size:11px; color:#d1d5db; }
.fb { display:flex; gap:4px; }
.fbb { padding:2px 6px; border:none; border-radius:4px; background:transparent; cursor:pointer; font-size:12px; opacity:.3; transition:opacity .15s; }
.fbb:hover { opacity:.7; }
.fbb.on { opacity:1; }

.input-area { padding:10px 24px 14px; }
.ib { display:flex; align-items:center; gap:8px; background:#fff; border:2px solid #e5e7eb; border-radius:14px; padding:4px 6px 4px 14px; transition:border-color .2s,box-shadow .2s; max-width:900px; margin:0 auto; }
.ib.on { border-color:#a5b4fc; box-shadow:0 0 0 4px rgba(99,102,241,.06); }
textarea { flex:1; border:none; outline:none; resize:none; font-size:14px; line-height:1.5; font-family:inherit; color:#111827; background:transparent; min-height:22px; max-height:120px; padding:2px 0; align-self:center; }
textarea::placeholder { color:#d1d5db; }
textarea:disabled { opacity:.4; }
.snd { width:36px; height:36px; border:none; border-radius:50%; background:#e5e7eb; color:#9ca3af; display:flex; align-items:center; justify-content:center; cursor:pointer; transition:all .2s; flex-shrink:0; }
.snd.go { background:linear-gradient(135deg,#3b82f6,#2563eb); color:#fff; box-shadow:0 2px 12px rgba(99,102,241,.3); }
.snd.go:hover { transform:scale(1.08); }
.snd:disabled { opacity:.4; cursor:not-allowed; }
.stop-btn { width:32px; height:32px; border:none; border-radius:50%; background:#fef2f2; color:#ef4444; display:flex; align-items:center; justify-content:center; cursor:pointer; transition:all .2s; flex-shrink:0; }
.stop-btn:hover { background:#fde8e8; transform:scale(1.08); }
</style>
