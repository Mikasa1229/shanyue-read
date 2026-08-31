<template>
  <Teleport to="body">
    <div v-if="open" class="welcome-backdrop" role="presentation" @click.self="close">
      <section class="welcome-dialog" role="dialog" aria-modal="true" aria-labelledby="welcome-title">
        <button class="welcome-close" type="button" aria-label="关闭公告" @click="close">×</button>
        <div class="welcome-mark" aria-hidden="true">阅</div>
        <p class="welcome-kicker">善阅坊 · 新读者公告</p>
        <h2 id="welcome-title">欢迎入场，体验官！</h2>
        <p class="welcome-lead">你刚刚创建的不是普通账号，是一张通往小说宇宙的临时门票。</p>

        <div class="welcome-credit">
          <strong>+100</strong>
          <span><b>平台试用积分</b><small>已放入你的账户，可用于体验平台模型对话</small></span>
        </div>

        <div class="welcome-copy">
          <p><b>这是 Demo 版本：</b>功能还在持续进化，数据可能会重启，服务偶尔会思考人生；请不要上传真实密码、敏感资料或生产数据。</p>
          <p>如果页面加载得有点慢，不一定是你的网速，可能是模型正在和网络进行一场严肃的哲学讨论。</p>
          <p>想把方向盘交给自己？可以在 Agent 的模型设置里配置兼容接口的自定义模型；自配模型使用你自己的额度，不消耗这 100 个平台试用积分。</p>
          <p>遇到问题、发现 Bug，或者有更好的点子，欢迎邮件联系：<a href="mailto:1500725047@qq.com">1500725047@qq.com</a>。邮件随缘查看，一定<s>（尽量）</s>听取建议🐦。</p>
          <p class="welcome-footnote">作品内容来自公开书源，版权归原作者及相关平台，本 Demo 仅用于技术展示与功能体验，无任何商业用途。</p>
        </div>

        <button class="welcome-action" type="button" @click="close">知道了，去逛逛 <span>→</span></button>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { useWelcomeAnnouncement } from '@/composables/useWelcomeAnnouncement'

const { open, close } = useWelcomeAnnouncement()
</script>

<style scoped>
.welcome-backdrop { position:fixed; inset:0; z-index:2000; display:grid; place-items:center; padding:20px; background:rgba(15,35,36,.62); backdrop-filter:blur(9px); animation:welcome-fade .22s ease-out; }
.welcome-dialog { position:relative; width:min(100%,520px); overflow:hidden; padding:34px 34px 30px; border:1px solid rgba(255,253,248,.3); border-radius:24px; color:#fffdf8; background:linear-gradient(145deg,#183f42 0%,#102b31 100%); box-shadow:0 28px 90px rgba(4,18,20,.42); animation:welcome-rise .3s cubic-bezier(.2,.8,.2,1); }
.welcome-dialog::before { content:''; position:absolute; width:220px; height:220px; top:-125px; right:-90px; border:1px solid rgba(211,236,165,.22); border-radius:50%; box-shadow:0 0 0 22px rgba(211,236,165,.04),0 0 0 44px rgba(211,236,165,.035); pointer-events:none; }
.welcome-close { position:absolute; top:14px; right:17px; z-index:1; width:32px; height:32px; border:1px solid rgba(255,253,248,.2); border-radius:50%; color:rgba(255,253,248,.78); background:rgba(255,253,248,.08); cursor:pointer; font-size:1.45rem; line-height:1; }
.welcome-close:hover { color:#fffdf8; background:rgba(255,253,248,.18); }
.welcome-mark { display:grid; place-items:center; width:46px; height:46px; margin-bottom:19px; border:1px solid rgba(211,236,165,.6); border-radius:14px 14px 14px 3px; color:#183638; background:#d3eca5; font-family:var(--font-serif); font-size:1.7rem; font-weight:700; transform:rotate(-5deg); }
.welcome-kicker { margin:0 0 9px; color:#d3eca5; font-size:.68rem; font-weight:800; letter-spacing:.16em; }
.welcome-dialog h2 { margin:0; color:#fffdf8; font-family:var(--font-serif); font-size:clamp(2.1rem,7vw,3.15rem); letter-spacing:-.06em; line-height:1.08; }
.welcome-lead { max-width:420px; margin:13px 0 21px; color:rgba(255,253,248,.78); font-size:.93rem; line-height:1.7; }
.welcome-credit { display:flex; align-items:center; gap:15px; margin:0 0 21px; padding:14px 16px; border:1px solid rgba(211,236,165,.35); border-radius:15px; background:rgba(211,236,165,.11); }
.welcome-credit strong { color:#d3eca5; font-family:var(--font-serif); font-size:2.45rem; line-height:1; letter-spacing:-.08em; }
.welcome-credit span { display:grid; gap:4px; }
.welcome-credit b { color:#fffdf8; font-size:.88rem; }
.welcome-credit small { color:rgba(255,253,248,.68); font-size:.72rem; }
.welcome-copy { display:grid; gap:10px; color:#dce8e2; font-size:.82rem; line-height:1.78; }
.welcome-copy p { margin:0; color:#dce8e2 !important; }
.welcome-copy b { color:#fffdf8 !important; }
.welcome-copy a { color:#d3eca5; text-decoration:underline; text-underline-offset:3px; }
.welcome-copy a:hover { color:#e0f2bb; }
.welcome-footnote { padding-top:3px; color:#a9c0b7 !important; font-size:.7rem; }
.welcome-action { display:flex; align-items:center; justify-content:space-between; width:100%; margin-top:25px; padding:12px 15px; border:0; border-radius:11px; color:#173638; background:#d3eca5; cursor:pointer; font:inherit; font-size:.84rem; font-weight:800; }
.welcome-action:hover { background:#e0f2bb; }
.welcome-action span { font-size:1.15rem; }
@keyframes welcome-fade { from { opacity:0; } to { opacity:1; } }
@keyframes welcome-rise { from { opacity:0; transform:translateY(12px) scale(.98); } to { opacity:1; transform:none; } }
@media (max-width:560px) { .welcome-dialog { padding:28px 22px 23px; border-radius:20px; } .welcome-dialog h2 { font-size:2.4rem; } }
</style>
