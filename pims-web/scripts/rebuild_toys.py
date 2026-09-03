# -*- coding: utf-8 -*-
import re

p = 'D:/开发/PIMS/pims-web/src/views/Login.vue'
s = open(p, encoding='utf-8').read()

def eyes(k, cx1, cx2, cy, rw, pr, pc):
    return ('<g class="eye">\n'
            '              <ellipse class="eye-white anim-blink" cx="%s" cy="%s" rx="%s" ry="%s" fill="#fff"/>\n'
            '              <circle class="pupil" :style="{ transform: `translate(${toys.%s.pxL}px, ${toys.%s.py}px)` }" cx="%s" cy="%s" r="%s" fill="%s"/>\n'
            '              <circle cx="%.1f" cy="%.1f" r="%.1f" fill="#fff"/>\n'
            '            </g>\n'
            '            <g class="eye">\n'
            '              <ellipse class="eye-white anim-blink" cx="%s" cy="%s" rx="%s" ry="%s" fill="#fff"/>\n'
            '              <circle class="pupil" :style="{ transform: `translate(${toys.%s.pxR}px, ${toys.%s.py}px)` }" cx="%s" cy="%s" r="%s" fill="%s"/>\n'
            '              <circle cx="%.1f" cy="%.1f" r="%.1f" fill="#fff"/>\n'
            '            </g>') % (
        cx1, cy, rw, rw, k, k, cx1, cy, pr, pc, cx1 - pr * 0.25, cy - pr * 0.25, pr * 0.38,
        cx2, cy, rw, rw, k, k, cx2, cy, pr, pc, cx2 - pr * 0.25, cy - pr * 0.25, pr * 0.38)

def tufts(cy, R, st):
    pts = [
        (75, cy - R + 3, 7), (75 - R * 0.60, cy - R * 0.80, 7), (75 + R * 0.60, cy - R * 0.80, 7),
        (75 - R * 0.95, cy - R * 0.30, 6), (75 + R * 0.95, cy - R * 0.30, 6),
        (75 - R * 1.00, cy + R * 0.15, 5), (75 + R * 1.00, cy + R * 0.15, 5),
    ]
    return ''.join('<circle cx="%.1f" cy="%.1f" r="%d" fill="%s" opacity="0.9"/>' % (x, y, r, st) for x, y, r in pts)

BEAR_B = ('<ellipse class="bf" cx="75" cy="131" rx="37" ry="23" fill="#c9935e" stroke="#a97c4a" stroke-width="2.5"/>\n'
          '            <circle cx="51" cy="145" r="9" fill="#c9935e"/><circle cx="99" cy="145" r="9" fill="#c9935e"/>\n'
          '            <circle cx="51" cy="145" r="4.5" fill="#e8c49a"/><circle cx="99" cy="145" r="4.5" fill="#e8c49a"/>\n'
          '            <g class="anim-wave">\n'
          '              <ellipse cx="118" cy="98" rx="9" ry="20" transform="rotate(35 118 98)" fill="#c9935e"/>\n'
          '              <circle cx="127" cy="78" r="8" fill="#c9935e"/><circle cx="127" cy="78" r="4" fill="#e8c49a"/>\n'
          '            </g>')
BEAR_H = ('<circle class="ef" cx="38" cy="44" r="15" fill="#c9935e"/>\n'
          '            <circle cx="38" cy="44" r="7" fill="#e8c49a"/>\n'
          '            <circle class="ef" cx="112" cy="44" r="15" fill="#c9935e"/>\n'
          '            <circle cx="112" cy="44" r="7" fill="#e8c49a"/>\n'
          '            <circle class="hf" cx="75" cy="80" r="46" fill="#c9935e" stroke="#a97c4a" stroke-width="2.5"/>\n'
          '            <ellipse cx="75" cy="95" rx="20" ry="15" fill="#f3dcc0"/>\n'
          '            <ellipse cx="75" cy="88" rx="7" ry="5" fill="#6b4a2f"/>\n'
          '            <path d="M75 93 q0 8 -7 8 M75 93 q0 8 7 8" fill="none" stroke="#6b4a2f" stroke-width="2" stroke-linecap="round"/>\n'
          '            <ellipse cx="46" cy="88" rx="7" ry="4.5" fill="#f5a8a8" opacity="0.7"/>\n'
          '            <ellipse cx="104" cy="88" rx="7" ry="4.5" fill="#f5a8a8" opacity="0.7"/>')

BUNNY_B = ('<ellipse class="bf" cx="75" cy="131" rx="35" ry="23" fill="#f6ede2" stroke="#e2d3bd" stroke-width="2.5"/>\n'
           '            <ellipse cx="75" cy="135" rx="21" ry="13" fill="#fff"/>\n'
           '            <circle cx="55" cy="145" r="8" fill="#f6ede2"/><circle cx="95" cy="145" r="8" fill="#f6ede2"/>')
BUNNY_H = ('<ellipse class="ef" cx="48" cy="26" rx="11" ry="30" transform="rotate(-18 48 26)" fill="#f6ede2"/>\n'
           '            <ellipse cx="48" cy="28" rx="6" ry="20" transform="rotate(-18 48 28)" fill="#f3c3cf"/>\n'
           '            <g class="anim-twitch">\n'
           '              <ellipse class="ef" cx="102" cy="26" rx="11" ry="30" transform="rotate(42 102 30)" fill="#f6ede2"/>\n'
           '              <ellipse cx="102" cy="28" rx="6" ry="20" transform="rotate(42 102 32)" fill="#f3c3cf"/>\n'
           '            </g>\n'
           '            <circle class="hf" cx="75" cy="88" r="40" fill="#f6ede2" stroke="#e2d3bd" stroke-width="2.5"/>\n'
           '            <ellipse cx="75" cy="102" rx="13" ry="10" fill="#fff"/>\n'
           '            <ellipse cx="75" cy="98" rx="5" ry="4" fill="#e58ba2"/>\n'
           '            <path d="M75 101 q-3 5 -7 2 M75 101 q3 5 7 2" fill="none" stroke="#3a2c22" stroke-width="2" stroke-linecap="round"/>\n'
           '            <ellipse cx="52" cy="100" rx="6" ry="4" fill="#f9c6d0" opacity="0.7"/>\n'
           '            <ellipse cx="98" cy="100" rx="6" ry="4" fill="#f9c6d0" opacity="0.7"/>')

PANDA_B = ('<ellipse class="bf" cx="75" cy="131" rx="36" ry="24" fill="#fff" stroke="#e6e6e6" stroke-width="2.5"/>\n'
           '            <circle cx="36" cy="116" r="12" fill="#2e2a33"/><circle cx="114" cy="116" r="12" fill="#2e2a33"/>\n'
           '            <circle cx="44" cy="131" r="11" fill="#2e2a33"/><circle cx="106" cy="131" r="11" fill="#2e2a33"/>\n'
           '            <ellipse cx="58" cy="140" rx="13" ry="8" fill="#2e2a33"/><ellipse cx="92" cy="140" rx="13" ry="8" fill="#2e2a33"/>')
PANDA_H = ('<circle class="ef" cx="40" cy="52" r="13" fill="#2e2a33"/>\n'
           '            <circle class="ef" cx="110" cy="52" r="13" fill="#2e2a33"/>\n'
           '            <circle class="hf" cx="75" cy="88" r="42" fill="#fff" stroke="#e6e6e6" stroke-width="2.5"/>\n'
           '            <ellipse cx="60" cy="84" rx="11" ry="13" transform="rotate(-15 60 84)" fill="#2e2a33"/>\n'
           '            <ellipse cx="90" cy="84" rx="11" ry="13" transform="rotate(15 90 84)" fill="#2e2a33"/>\n'
           '            <ellipse cx="75" cy="104" rx="14" ry="11" fill="#f0e9de"/>\n'
           '            <ellipse cx="75" cy="100" rx="5.5" ry="4" fill="#2e2a33"/>\n'
           '            <path d="M75 104 q0 7 -7 7 M75 104 q0 7 7 7" fill="none" stroke="#2e2a33" stroke-width="2" stroke-linecap="round"/>\n'
           '            <ellipse cx="50" cy="100" rx="6" ry="4" fill="#f9c6d0" opacity="0.65"/>\n'
           '            <ellipse cx="100" cy="100" rx="6" ry="4" fill="#f9c6d0" opacity="0.65"/>')

FOX_B = ('<g class="anim-wag">\n'
         '            <circle cx="34" cy="118" r="13" fill="#e8834f"/><circle cx="23" cy="130" r="11" fill="#e8834f"/>\n'
         '            <circle cx="27" cy="142" r="9" fill="#f8c896"/>\n'
         '          </g>\n'
         '            <ellipse class="bf" cx="75" cy="131" rx="35" ry="23" fill="#e8834f" stroke="#d56f3c" stroke-width="2.5"/>\n'
         '            <ellipse cx="75" cy="137" rx="20" ry="13" fill="#f8c896"/>\n'
         '            <circle cx="54" cy="145" r="8.5" fill="#e8834f"/><circle cx="96" cy="145" r="8.5" fill="#e8834f"/>\n'
         '            <circle cx="54" cy="145" r="4" fill="#f8c896"/><circle cx="96" cy="145" r="4" fill="#f8c896"/>')
FOX_H = ('<path class="ef" d="M40 52 L52 20 L66 44 Z" fill="#e8834f"/>\n'
         '            <path d="M47 47 L53 28 L61 43 Z" fill="#f8c896"/>\n'
         '            <path class="ef" d="M110 52 L98 20 L84 44 Z" fill="#e8834f"/>\n'
         '            <path d="M103 47 L97 28 L89 43 Z" fill="#f8c896"/>\n'
         '            <circle class="hf" cx="75" cy="84" r="40" fill="#e8834f" stroke="#d56f3c" stroke-width="2.5"/>\n'
         '            <ellipse cx="75" cy="102" rx="20" ry="14" fill="#f8c896"/>\n'
         '            <ellipse cx="75" cy="94" rx="6.5" ry="4.5" fill="#5c3a2a"/>\n'
         '            <path d="M75 98 q0 7 -6 7 M75 98 q0 7 6 7" fill="none" stroke="#5c3a2a" stroke-width="2" stroke-linecap="round"/>\n'
         '            <ellipse cx="48" cy="96" rx="6" ry="4" fill="#f9a8d4" opacity="0.6"/>\n'
         '            <ellipse cx="102" cy="96" rx="6" ry="4" fill="#f9a8d4" opacity="0.6"/>')

FROG_B = ('<ellipse class="bf" cx="75" cy="131" rx="36" ry="22" fill="#7cc576" stroke="#68b061" stroke-width="2.5"/>\n'
          '            <ellipse cx="75" cy="136" rx="20" ry="12" fill="#a9dd9b"/>\n'
          '            <ellipse cx="38" cy="143" rx="15" ry="8" transform="rotate(-24 38 143)" fill="#7cc576"/>\n'
          '            <ellipse cx="112" cy="143" rx="15" ry="8" transform="rotate(24 112 143)" fill="#7cc576"/>\n'
          '            <circle cx="52" cy="144" r="9" fill="#7cc576"/><circle cx="98" cy="144" r="9" fill="#7cc576"/>')
FROG_H = ('<circle class="ef" cx="58" cy="56" r="11" fill="#7cc576"/>\n'
          '            <circle class="ef" cx="92" cy="56" r="11" fill="#7cc576"/>\n'
          '            <circle class="hf" cx="75" cy="92" r="40" fill="#7cc576" stroke="#68b061" stroke-width="2.5"/>\n'
          '            <path d="M52 100 Q75 118 98 100" fill="none" stroke="#2f5d2b" stroke-width="2.5" stroke-linecap="round"/>\n'
          '            <ellipse cx="48" cy="104" rx="6.5" ry="4.5" fill="#f9a8d4" opacity="0.55"/>\n'
          '            <ellipse cx="102" cy="104" rx="6.5" ry="4.5" fill="#f9a8d4" opacity="0.55"/>')

PENG_B = ('<ellipse class="bf" cx="75" cy="97" rx="34" ry="42" fill="#3b5b9f" stroke="#2c4575" stroke-width="2.5"/>\n'
          '            <ellipse cx="75" cy="112" rx="22" ry="26" fill="#fff"/>\n'
          '            <g class="anim-flap">\n'
          '              <ellipse cx="38" cy="100" rx="9" ry="20" transform="rotate(25 38 100)" fill="#2c4575"/>\n'
          '              <ellipse cx="112" cy="100" rx="9" ry="20" transform="rotate(-25 112 100)" fill="#2c4575"/>\n'
          '            </g>\n'
          '            <ellipse cx="58" cy="141" rx="10" ry="6" fill="#f59e0b"/><ellipse cx="92" cy="141" rx="10" ry="6" fill="#f59e0b"/>')
PENG_H = ('<circle class="hf" cx="75" cy="66" r="26" fill="#3b5b9f" stroke="#2c4575" stroke-width="2.5"/>\n'
          '            <ellipse cx="75" cy="72" rx="22" ry="16" fill="#fff"/>\n'
          '            <path d="M69 76 L81 76 L75 87 Z" fill="#f59e0b"/>\n'
          '            <circle cx="60" cy="52" r="6" fill="#4a6cb8"/><circle cx="75" cy="47" r="7" fill="#4a6cb8"/><circle cx="90" cy="52" r="6" fill="#4a6cb8"/>\n'
          '            <ellipse cx="56" cy="80" rx="5" ry="3.5" fill="#f9a8d4" opacity="0.6"/>\n'
          '            <ellipse cx="94" cy="80" rx="5" ry="3.5" fill="#f9a8d4" opacity="0.6"/>')

MON_B = ('<g class="anim-wob">\n'
         '            <circle cx="52" cy="50" r="8" fill="#7c3aed"/><circle cx="98" cy="50" r="8" fill="#7c3aed"/>\n'
         '            <circle class="bf" cx="75" cy="88" r="42" fill="#9d6bf5" stroke="#7c3aed" stroke-width="2.5"/>\n'
         '            <circle cx="55" cy="48" r="7" fill="#b39cf7"/><circle cx="75" cy="44" r="8" fill="#b39cf7"/><circle cx="95" cy="48" r="7" fill="#b39cf7"/>\n'
         '            <circle cx="40" cy="112" r="9" fill="#8b5cf6"/><circle cx="110" cy="112" r="9" fill="#8b5cf6"/>\n'
         '            <circle cx="58" cy="140" r="8" fill="#8b5cf6"/><circle cx="92" cy="140" r="8" fill="#8b5cf6"/>\n'
         '          </g>')
MON_H = ('<circle class="hf" cx="75" cy="82" r="36" fill="#9d6bf5" stroke="#7c3aed" stroke-width="2.5"/>\n'
         '            <path d="M62 103 Q75 116 88 103" fill="none" stroke="#5b21b6" stroke-width="3" stroke-linecap="round"/>\n'
         '            <ellipse cx="48" cy="96" rx="6.5" ry="4.5" fill="#e9d5ff" opacity="0.75"/>\n'
         '            <ellipse cx="102" cy="96" rx="6.5" ry="4.5" fill="#e9d5ff" opacity="0.75"/>')

ARTS = {
    'bear':   dict(cy=80, R=46, body=BEAR_B, head=BEAR_H, eyes=eyes('bear', 58, 92, 72, 8, 4, '#2f1f12')),
    'bunny':  dict(cy=88, R=40, body=BUNNY_B, head=BUNNY_H, eyes=eyes('bunny', 60, 90, 84, 8, 4, '#3a2c22')),
    'panda':  dict(cy=88, R=42, body=PANDA_B, head=PANDA_H, eyes=eyes('panda', 60, 90, 84, 5.5, 2.8, '#1f1b23')),
    'fox':    dict(cy=84, R=40, body=FOX_B, head=FOX_H, eyes=eyes('fox', 60, 90, 80, 7.5, 3.8, '#4a2f1d')),
    'frog':   dict(cy=92, R=40, body=FROG_B, head=FROG_H, eyes=eyes('frog', 58, 92, 56, 7.5, 3.6, '#264b24')),
    'penguin':dict(cy=66, R=26, body=PENG_B, head=PENG_H, eyes=eyes('penguin', 62, 88, 68, 6.5, 3.5, '#1c2539')),
    'monster':dict(cy=82, R=36, body=MON_B, head=MON_H, eyes=eyes('monster', 60, 90, 82, 8.5, 4.5, '#3b1d6e')),
}
SIZES = {'bear': 1.12, 'panda': 1.08, 'fox': 1.0, 'monster': 1.02, 'penguin': 0.95, 'frog': 0.9, 'bunny': 0.85}
MARGINS = {'bear': -0.27, 'panda': -0.26, 'fox': -0.24, 'monster': -0.24, 'penguin': -0.23, 'frog': -0.22, 'bunny': -0.20}

STROKES = {'bear': '#a97c4a', 'bunny': '#e2d3bd', 'panda': '#e6e6e6', 'fox': '#d56f3c',
           'frog': '#68b061', 'penguin': '#2c4575', 'monster': '#7c3aed'}
rows = []
for k, a in ARTS.items():
    tuft = tufts(a['cy'], a['R'], STROKES[k])
    rows.append(
        '<span class="toywrap %s" :class="{ flash: toys.%s.flash }"\n'
        '      :style="{ transform: `translateY(${toys.%s.ty}px) rotate(${toys.%s.wob + toys.%s.lean}deg) scale(${2 - toys.%s.s}, ${toys.%s.s})` }"\n'
        '      @click="clickToy(\'%s\')" @mousedown="pressToy(\'%s\')" @mouseup="releaseToy(\'%s\')">\n'
        '  <svg class="plush" viewBox="0 0 150 150">\n'
        '          <ellipse class="pshadow" cx="75" cy="143" rx="38" ry="6" fill="rgba(0,0,0,0.24)"/>\n'
        '          <g class="body anim-bob">\n'
        '            %s\n'
        '          </g>\n'
        '          <g class="head" :style="{ transform: `rotate(${toys.%s.rot + toys.%s.baseRot}deg)` }">\n'
        '            %s\n'
        '            %s\n'
        '            %s\n'
        '          </g>\n'
        '        </svg>\n'
        '  <span class="toy-relief"></span>\n'
        '  <span class="toy-light" :style="{ transform: `translate(${toys.%s.lx}px, ${toys.%s.ly}px)` }"></span>\n'
        '  <span class="toy-dark" :style="{ transform: `translate(${toys.%s.dx}px, ${toys.%s.dy}px)` }"></span>\n'
        '</span>' % (
            k, k, k, k, k, k, k, k, k, k,
            a['body'], k, k, tuft, a['head'], a['eyes'],
            k, k, k, k)
    )

new_block = ('      <!-- 七只毛绒公仔（主色系/大小/动作各不相同） -->\n'
             '      <div class="plush-row" aria-hidden="true">\n' + '\n'.join(rows) + '\n      </div>\n    </aside>')

start = s.index('      <!-- 七只毛绒公仔')
end = s.index('    </aside>', start)
s = s[:start] + new_block + s[end + len('    </aside>'):]

open(p, 'w', encoding='utf-8').write(s)
print('plush-row rewritten, len:', len(s))
