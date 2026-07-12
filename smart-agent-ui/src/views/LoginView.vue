<template>
  <div class="auth-page">
    <!-- Background decorations -->
    <div class="bg-shape s1"></div><div class="bg-shape s2"></div><div class="bg-shape s3"></div>

    <!-- Split layout -->
    <div class="auth-container">
      <!-- Left brand panel -->
      <div class="brand-panel">
        <div class="brand-inner">
          <div class="brand-logo">
            <svg width="48" height="48" viewBox="0 0 48 48" fill="none" class="logo-svg">
              <rect width="48" height="48" rx="13" fill="#1e293b"/>
              <path d="M16 32V18l6 6 6-6v14" stroke="#38bdf8" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M32 32V18l-6 6-6-6v14" stroke="#818cf8" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"/>
              <circle cx="22" cy="14" r="2" fill="#38bdf8"/><circle cx="26" cy="14" r="2" fill="#818cf8"/>
            </svg>
          </div>
          <h1 class="brand-name">Smart Agent</h1>
          <p class="brand-tagline">AI 多智能体协作平台</p>
          <p class="brand-desc">基于 Supervisor/SubAgent 模式，集成 RAG 知识检索、钉钉机器人、流式对话，为企业提供开箱即用的 AI Agent 解决方案。</p>

          <div class="feature-list">
            <div class="feat">
              <div class="feat-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg></div>
              <div class="feat-text"><strong>多 Agent 协作</strong><span>Supervisor 自动调度子 Agent</span></div>
            </div>
            <div class="feat">
              <div class="feat-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg></div>
              <div class="feat-text"><strong>流式对话</strong><span>实时 SSE 推送，思考过程可见</span></div>
            </div>
            <div class="feat">
              <div class="feat-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M2 12h20"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg></div>
              <div class="feat-text"><strong>RAG 知识库</strong><span>Milvus 向量检索，精准回答</span></div>
            </div>
            <div class="feat">
              <div class="feat-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2" ry="2"/><path d="M8 21h8"/><path d="M12 17v4"/></svg></div>
              <div class="feat-text"><strong>钉钉机器人</strong><span>Stream 模式 + AI 流式卡片</span></div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right form panel -->
      <div class="form-panel">
        <div class="form-card" :class="{flip:flipping}">
          <!-- Login -->
          <template v-if="mode==='login'">
            <div class="form-head"><h2>欢迎回来</h2><p>登录你的账号</p></div>
            <div class="field"><label>用户名</label><input v-model="loginForm.username" placeholder="输入用户名" autocomplete="username"/></div>
            <div class="field"><label>密码</label><input v-model="loginForm.password" type="password" placeholder="输入密码" autocomplete="current-password"/></div>
            <div v-if="errorMsg" class="err">{{ errorMsg }}</div>
            <button class="btn" :disabled="loading" @click="handleLogin"><span v-if="!loading">登 录</span><span v-else class="spinner"></span></button>
            <p class="sw">还没有账号？<a @click="switchMode('register')">立即注册 →</a></p>
          </template>
          <!-- Register -->
          <template v-if="mode==='register'">
            <div class="form-head"><h2>创建账号</h2><p>注册后体验全部功能</p></div>
            <div class="field"><label>用户名</label><input v-model="registerForm.username" placeholder="字母数字组合" autocomplete="username"/></div>
            <div class="field"><label>昵称</label><input v-model="registerForm.nickname" placeholder="给自己起个名字"/></div>
            <div class="field"><label>密码</label><input v-model="registerForm.password" type="password" placeholder="至少 6 个字符" autocomplete="new-password"/></div>
            <div class="field"><label>确认密码</label><input v-model="registerForm.confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password"/></div>
            <div v-if="errorMsg" class="err">{{ errorMsg }}</div>
            <button class="btn" :disabled="loading" @click="handleRegister"><span v-if="!loading">注 册</span><span v-else class="spinner"></span></button>
            <p class="sw">已有账号？<a @click="switchMode('login')">立即登录 →</a></p>
          </template>
        </div>
        <div class="form-footer"><div class="ff-info"><span class="ffi-brand">Smart Agent</span> <span class="ffi-ver">v1.0</span></div><div class="ff-author">Jiangbo Li · lijiangbo2023@163.com</div></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'; import { useRouter } from 'vue-router'; import axios from 'axios'
const router = useRouter(); const mode = ref('login'); const flipping = ref(false); const loading = ref(false); const errorMsg = ref('')
const loginForm = ref({ username: '', password: '' }); const registerForm = ref({ username: '', nickname: '', password: '', confirmPassword: '' })
function switchMode(m) { flipping.value = true; setTimeout(() => { mode.value = m; errorMsg.value = ''; flipping.value = false }, 250) }
async function handleLogin() { errorMsg.value = ''; if (!loginForm.value.username || !loginForm.value.password) { errorMsg.value = '请填写用户名和密码'; return }; loading.value = true; try { const r = await axios.post('/api/auth/login', loginForm.value); if (r.data.success) { localStorage.setItem('token', r.data.data.token); localStorage.setItem('user', JSON.stringify(r.data.data)); router.push('/chat') } else errorMsg.value = r.data.msg || '登录失败' } catch { errorMsg.value = '网络错误' } finally { loading.value = false } }
async function handleRegister() { errorMsg.value = ''; if (!registerForm.value.username || !registerForm.value.password) { errorMsg.value = '请填写用户名和密码'; return }; if (registerForm.value.password.length < 6) { errorMsg.value = '密码至少需要 6 个字符'; return }; if (registerForm.value.password !== registerForm.value.confirmPassword) { errorMsg.value = '两次密码不一致'; return }; loading.value = true; try { const r = await axios.post('/api/auth/register', { username: registerForm.value.username, password: registerForm.value.password, nickname: registerForm.value.nickname || registerForm.value.username }); if (r.data.success) { localStorage.setItem('token', r.data.data.token); localStorage.setItem('user', JSON.stringify(r.data.data)); router.push('/chat') } else errorMsg.value = r.data.msg || '注册失败' } catch { errorMsg.value = '网络错误' } finally { loading.value = false } }
</script>

<style scoped>
.auth-page { min-height:100vh; display:flex; align-items:center; justify-content:center; background:linear-gradient(135deg, #eff6ff 0%, #f8fafc 50%, #f0f9ff 100%); position:relative; overflow:hidden; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC',sans-serif; }
.bg-shape { position:absolute; border-radius:50%; pointer-events:none; opacity:.35; }
.s1 { width:500px; height:500px; background:radial-gradient(circle, rgba(59,130,246,.06), transparent 70%); top:-100px; right:-150px; }
.s2 { width:350px; height:350px; background:radial-gradient(circle, rgba(56,189,248,.05), transparent 70%); bottom:-80px; left:-100px; }
.s3 { width:250px; height:250px; background:radial-gradient(circle, rgba(99,102,241,.04), transparent 70%); top:50%; left:40%; }

.auth-container { position:relative; z-index:1; display:flex; width:960px; min-height:580px; border-radius:20px; overflow:hidden; box-shadow:0 20px 60px rgba(0,0,0,.08), 0 0 0 1px rgba(0,0,0,.04); }

/* Left brand */
.brand-panel { width:460px; background:#fff; display:flex; align-items:center; padding:48px 44px; position:relative; }
.brand-panel::after { content:''; position:absolute; right:0; top:15%; bottom:15%; width:1px; background:linear-gradient(transparent,#e5e7eb,transparent); }
.brand-inner { width:100%; }
.brand-logo { margin-bottom:24px; }
.logo-icon { display:flex; align-items:center; justify-content:center; }
.logo-svg { filter:drop-shadow(0 4px 12px rgba(99,102,241,.25)); }
.brand-name { font-size:26px; font-weight:800; color:#1a1a2e; margin:0 0 6px; letter-spacing:-.5px; }
.brand-tagline { font-size:14px; font-weight:500; color:#3b82f6; margin:0 0 16px; }
.brand-desc { font-size:13px; color:#6b7280; line-height:1.7; margin:0 0 32px; }

.feature-list { display:flex; flex-direction:column; gap:16px; }
.feat { display:flex; gap:14px; }
.feat-icon { width:40px; height:40px; border-radius:10px; background:rgba(99,102,241,.06); display:flex; align-items:center; justify-content:center; color:#3b82f6; flex-shrink:0; }
.feat-text { display:flex; flex-direction:column; gap:2px; }
.feat-text strong { font-size:13px; color:#1f2937; }
.feat-text span { font-size:12px; color:#9ca3af; }

/* Right form */
.form-panel { flex:1; background:#fafbfc; display:flex; flex-direction:column; align-items:center; justify-content:center; padding:40px; }
.form-card { width:100%; max-width:340px; transition:transform .25s,opacity .25s; }
.form-card.flip { transform:scale(.94); opacity:0; }
.form-head { margin-bottom:28px; }
.form-head h2 { font-size:22px; font-weight:700; color:#1a1a2e; margin:0 0 4px; }
.form-head p { font-size:13px; color:#9ca3af; margin:0; }

.field { margin-bottom:18px; }
.field label { display:block; font-size:12px; font-weight:600; color:#4b5563; margin-bottom:6px; }
.field input { width:100%; padding:11px 14px; border:1.5px solid #e5e7eb; border-radius:10px; font-size:14px; color:#1a1a2e; background:#fff; outline:none; transition:border-color .2s,box-shadow .2s; font-family:inherit; }
.field input::placeholder { color:#d1d5db; }
.field input:focus { border-color:#818cf8; box-shadow:0 0 0 3px rgba(129,140,248,.1); }

.err { background:rgba(239,68,68,.06); border:1px solid rgba(239,68,68,.12); color:#ef4444; font-size:13px; padding:10px 14px; border-radius:8px; margin-bottom:16px; }

.btn { width:100%; padding:12px; border:none; border-radius:10px; background:linear-gradient(135deg,#3b82f6,#2563eb); color:#fff; font-size:15px; font-weight:600; cursor:pointer; transition:all .2s; margin-top:6px; letter-spacing:2px; }
.btn:hover:not(:disabled) { transform:translateY(-1px); box-shadow:0 6px 20px rgba(99,102,241,.35); }
.btn:disabled { opacity:.5; cursor:not-allowed; }
.spinner { display:inline-block; width:18px; height:18px; border:2px solid rgba(255,255,255,.3); border-top-color:#fff; border-radius:50%; animation:spin .6s linear infinite; }
@keyframes spin { to{transform:rotate(360deg)} }

.sw { text-align:center; font-size:13px; color:#9ca3af; margin:20px 0 0; }
.sw a { color:#3b82f6; cursor:pointer; font-weight:500; }
.sw a:hover { color:#818cf8; }

.form-footer { margin-top:28px; display:flex; flex-direction:column; align-items:center; gap:3px; }
.ff-info { font-size:11px; }
.ffi-brand { color:#b0b7c3; font-weight:400; }
.ffi-ver { color:#b0b7c3; font-weight:400; }
.ff-author { font-size:10px; color:#b0b7c3; }

@media (max-width:980px) { .auth-container { flex-direction:column; width:420px; } .brand-panel { width:100%; padding:32px 28px; } .brand-panel::after { display:none; } .feature-list { gap:10px; } }
</style>
