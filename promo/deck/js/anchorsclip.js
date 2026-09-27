// The localisation slide as a standalone clip: eyebrow + title on top, the anchors module below.
// opts: { seconds: 5 } compresses the anchors timeline to fit, with a fade-out in the last 0.5 s.
import { mount as mountAnchors } from './anchors.js';

export function mount(el, opts = {}) {
  const secs = opts.seconds ?? 5;
  el.innerHTML = `
    <div id="ac-root" style="position:absolute;inset:0;display:flex;flex-direction:column;padding:4.5% 5% 3%;box-sizing:border-box;font-family:Sora,sans-serif;color:#fff8f4;opacity:0">
      <div style="font-weight:600;font-size:1.6vw;letter-spacing:.18em;text-transform:uppercase;color:#c67c4e;text-shadow:0 2px 12px rgba(0,0,0,.6)">Localisation</div>
      <div style="font-weight:700;font-size:4.2vw;letter-spacing:-.02em;line-height:1.08;margin-top:.4vw;text-shadow:0 3px 18px rgba(0,0,0,.6)">GPS to the door. Signs, compass and barometer to the room.</div>
      <div id="ac-stage" style="flex:1;position:relative;margin-top:0.5vw"></div>
    </div>`;
  const root = el.querySelector('#ac-root');
  const inner = mountAnchors(el.querySelector('#ac-stage'), { loop: false });
  let tl;
  return {
    play() {
      inner.play();
      // Find the anchors timeline and squeeze it into the clip length.
      const kids = gsap.globalTimeline.getChildren(false, false, true);
      const at = kids.filter(t => t.duration() > 1).sort((a, b) => b.duration() - a.duration())[0];
      if (at) at.timeScale(at.duration() / (secs - 0.6));
      tl?.kill();
      tl = gsap.timeline()
        .fromTo(root, { opacity: 0 }, { opacity: 1, duration: 0.4 }, 0)
        .to(root, { opacity: 0, duration: 0.5 }, secs - 0.5);
    },
    pause() { inner.pause(); tl?.pause(); },
    destroy() { inner.destroy(); tl?.kill(); el.innerHTML = ''; },
    duration: secs,
  };
}
