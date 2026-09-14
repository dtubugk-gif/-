"""SVG sources copied verbatim from the design canvas (Rikavon design, round 1 + rot cycle demo).

Each constant is one <svg> element in a 200x200 viewBox. generate_lottie.py slices these into body, face,
mold and fly groups and animates them; nothing here is hand-edited so the app matches the canvas exactly.
"""

POTATO_CYCLE = r"""<svg width="260" height="250" viewBox="0 0 200 200" role="img" aria-label="תפוח האדמה נרקב בהדרגה">
          <ellipse cx="100" cy="176" rx="52" ry="8" fill="#000" opacity=".35"></ellipse>
          <ellipse cx="78" cy="164" rx="13" ry="9" fill="#c08c2f"></ellipse>
          <ellipse cx="122" cy="164" rx="13" ry="9" fill="#c08c2f"></ellipse>
          <path d="M100 40c35 0 63 27 65 62 2 35-27 62-65 62s-67-27-65-62c2-35 30-62 65-62z" fill="#dda94a" stroke="#8a6220" stroke-width="4"></path>
          <g>
            <path d="M100 42q-1-9 6-13" stroke="#7a934a" stroke-width="4" fill="none" stroke-linecap="round"></path>
            <path d="M106 29c7-3 12 1 12 6-6 3-12 0-12-6z" fill="#8fae56" stroke="#5f7a33" stroke-width="2.5" stroke-linejoin="round"></path>
            <circle cx="76" cy="100" r="8" fill="#3b2c12"></circle><circle cx="124" cy="100" r="8" fill="#3b2c12"></circle>
            <circle cx="79" cy="97" r="2.8" fill="#fff"></circle><circle cx="127" cy="97" r="2.8" fill="#fff"></circle>
            <ellipse cx="64" cy="118" rx="9" ry="5" fill="#e08b4f" opacity=".55"></ellipse><ellipse cx="136" cy="118" rx="9" ry="5" fill="#e08b4f" opacity=".55"></ellipse>
            <path d="M90 128q10 6 20 0" stroke="#3b2c12" stroke-width="4.5" fill="none" stroke-linecap="round"></path>
          </g>
          <g opacity="0">
            <path d="M100 42q-1-9 6-13" stroke="#7a934a" stroke-width="4" fill="none" stroke-linecap="round" opacity=".5"></path>
            <circle cx="76" cy="102" r="7" fill="#3b2c12"></circle><circle cx="124" cy="102" r="7" fill="#3b2c12"></circle>
            <circle cx="78.5" cy="99.5" r="2.2" fill="#fff"></circle><circle cx="126.5" cy="99.5" r="2.2" fill="#fff"></circle>
            <path d="M66 93l20-4M134 93l-20-4" stroke="#8a6220" stroke-width="4" stroke-linecap="round"></path>
            <path d="M90 130h20" stroke="#3b2c12" stroke-width="4.5" stroke-linecap="round"></path>
          </g>
          <g opacity="0">
            <path d="M70 98q8 8 16 0M114 98q8 8 16 0" stroke="#23200c" stroke-width="4.5" fill="none" stroke-linecap="round"></path>
            <circle cx="78" cy="106" r="4" fill="#23200c"></circle><circle cx="122" cy="106" r="4" fill="#23200c"></circle>
            <path d="M62 90l18-6M138 84l-18 6" stroke="#37320e" stroke-width="4" stroke-linecap="round"></path>
            <path d="M84 136q8-8 16 0t16 0" stroke="#23200c" stroke-width="4.5" fill="none" stroke-linecap="round"></path>
          </g>
          <g opacity="0">
            <ellipse cx="64" cy="126" rx="15" ry="10" fill="#5c6e2a" stroke="#3a4519" stroke-width="2.5"></ellipse>
            <circle cx="59" cy="123" r="2.2" fill="#3a4519"></circle><circle cx="69" cy="129" r="1.7" fill="#3a4519"></circle>
            <ellipse cx="138" cy="140" rx="12" ry="8" fill="#5c6e2a" stroke="#3a4519" stroke-width="2.5"></ellipse>
            <ellipse cx="120" cy="72" rx="8" ry="5" fill="#465522"></ellipse>
          </g>
          <g opacity="0">
            <g><circle cx="58" cy="42" r="3.4" fill="#2f331f"></circle><path d="M53 38l-4-3M63 38l4-3" stroke="#2f331f" stroke-width="1.6"></path></g>
            <g><circle cx="150" cy="52" r="3" fill="#2f331f"></circle><path d="M146 48l-4-2M154 48l4-2" stroke="#2f331f" stroke-width="1.6"></path></g>
            <path d="M84 26q4-10 0-18" stroke="#59623a" stroke-width="3" fill="none" stroke-linecap="round"></path>
            <path d="M112 30q-4-10 0-18" stroke="#59623a" stroke-width="3" fill="none" stroke-linecap="round"></path>
          </g>
        </svg>"""

POTATO_HEALTHY = r"""<svg width="230" height="230" viewBox="0 0 200 200" role="img" aria-label="תפוח האדמה במצב טוב">
        <defs><radialGradient id="g1a" cx="40%" cy="32%" r="78%"><stop offset="0%" stop-color="#f2cb72"></stop><stop offset="62%" stop-color="#dda94a"></stop><stop offset="100%" stop-color="#bd8a2e"></stop></radialGradient></defs>
        <ellipse cx="100" cy="174" rx="50" ry="8" fill="#000" opacity=".35"></ellipse>
        <ellipse cx="78" cy="163" rx="13" ry="9" fill="#c08c2f" stroke="#8a6220" stroke-width="3.5"></ellipse>
        <ellipse cx="122" cy="163" rx="13" ry="9" fill="#c08c2f" stroke="#8a6220" stroke-width="3.5"></ellipse>
        <path d="M100 40c35 0 63 27 65 62 2 35-27 62-65 62s-67-27-65-62c2-35 30-62 65-62z" fill="url(#g1a)" stroke="#8a6220" stroke-width="4"></path>
        <path d="M100 42q-1-9 6-13" stroke="#7a934a" stroke-width="4" fill="none" stroke-linecap="round"></path>
        <path d="M106 29c7-3 12 1 12 6-6 3-12 0-12-6z" fill="#8fae56" stroke="#5f7a33" stroke-width="2.5" stroke-linejoin="round"></path>
        <path d="M56 90q7-5 12 1M132 140q6-4 11 1M118 70q5-3 9 1" stroke="#b07f2a" stroke-width="3.5" fill="none" stroke-linecap="round"></path>
        <circle cx="76" cy="102" r="8" fill="#3b2c12"></circle>
        <circle cx="124" cy="102" r="8" fill="#3b2c12"></circle>
        <circle cx="79" cy="99" r="2.8" fill="#fff"></circle>
        <circle cx="127" cy="99" r="2.8" fill="#fff"></circle>
        <path d="M66 93q10-5 20 0M114 93q10-5 20 0" stroke="#8a6220" stroke-width="4" fill="none" stroke-linecap="round"></path>
        <ellipse cx="64" cy="118" rx="9" ry="5" fill="#e08b4f" opacity=".55"></ellipse>
        <ellipse cx="136" cy="118" rx="9" ry="5" fill="#e08b4f" opacity=".55"></ellipse>
        <path d="M92 128q8 5 16 0" stroke="#3b2c12" stroke-width="4.5" fill="none" stroke-linecap="round"></path>
      </svg>"""

BRAIN_MID = r"""<svg width="200" height="200" viewBox="0 0 200 200" role="img" aria-label="המוח במצב בינוני">
          <defs><radialGradient id="g1b" cx="42%" cy="30%" r="80%"><stop offset="0%" stop-color="#f6afc5"></stop><stop offset="60%" stop-color="#e2799c"></stop><stop offset="100%" stop-color="#c65f82"></stop></radialGradient></defs>
          <ellipse cx="100" cy="170" rx="48" ry="8" fill="#000" opacity=".3"></ellipse>
          <ellipse cx="80" cy="160" rx="12" ry="8" fill="#d06a8d" stroke="#9c4364" stroke-width="3.5"></ellipse>
          <ellipse cx="120" cy="160" rx="12" ry="8" fill="#d06a8d" stroke="#9c4364" stroke-width="3.5"></ellipse>
          <path d="M100 36c-16 0-24 9-32 9-18 0-34 14-34 34 0 8 3 14 3 20 0 16 12 30 30 32 8 12 22 17 33 17s25-5 33-17c18-2 30-16 30-32 0-6 3-12 3-20 0-20-16-34-34-34-8 0-16-9-32-9z" fill="url(#g1b)" stroke="#9c4364" stroke-width="4"></path>
          <path d="M100 38v108" stroke="#c25a7e" stroke-width="3.5"></path>
          <path d="M56 74q12 8 0 18M70 54q10 10-2 18M140 74q-12 8 0 18M128 54q-10 10 2 18M62 112q12 4 4 16M136 112q-12 4-4 16M84 46q6 8-2 14M116 46q-6 8 2 14" stroke="#c25a7e" stroke-width="3.5" fill="none" stroke-linecap="round"></path>
          <circle cx="80" cy="101" r="7" fill="#4a1f2e"></circle>
          <circle cx="120" cy="101" r="7" fill="#4a1f2e"></circle>
          <circle cx="82.5" cy="98.5" r="2.4" fill="#fff"></circle>
          <circle cx="122.5" cy="98.5" r="2.4" fill="#fff"></circle>
          <path d="M71 94h18M111 94h18" stroke="#4a1f2e" stroke-width="4" stroke-linecap="round"></path>
          <ellipse cx="68" cy="114" rx="8" ry="4.5" fill="#f295b4" opacity=".7"></ellipse>
          <ellipse cx="132" cy="114" rx="8" ry="4.5" fill="#f295b4" opacity=".7"></ellipse>
          <path d="M88 127q12 5 24-2" stroke="#4a1f2e" stroke-width="4.5" fill="none" stroke-linecap="round"></path>
        </svg>"""

POTATO_ROTTEN = r"""<svg width="210" height="210" viewBox="0 0 200 200" role="img" aria-label="תפוח האדמה רקוב לגמרי">
      <g><circle cx="58" cy="42" r="3.4" fill="#2f331f"></circle><path d="M53 38l-4-3M63 38l4-3" stroke="#2f331f" stroke-width="1.6"></path></g>
      <g><circle cx="150" cy="55" r="3" fill="#2f331f"></circle><path d="M146 51l-4-2M154 51l4-2" stroke="#2f331f" stroke-width="1.6"></path></g>
      <path d="M84 30q4-10 0-18" stroke="#59623a" stroke-width="3" fill="none" stroke-linecap="round"></path>
      <path d="M112 34q-4-10 0-18" stroke="#59623a" stroke-width="3" fill="none" stroke-linecap="round"></path>
      <defs><radialGradient id="g1c" cx="42%" cy="32%" r="80%"><stop offset="0%" stop-color="#94873e"></stop><stop offset="60%" stop-color="#6f6428"></stop><stop offset="100%" stop-color="#544b1a"></stop></radialGradient></defs>
      <ellipse cx="100" cy="180" rx="54" ry="8" fill="#000" opacity=".4"></ellipse>
      <ellipse cx="76" cy="170" rx="13" ry="8" fill="#57501c" stroke="#37320e" stroke-width="3.5" transform="rotate(-8 76 170)"></ellipse>
      <ellipse cx="124" cy="170" rx="13" ry="8" fill="#57501c" stroke="#37320e" stroke-width="3.5" transform="rotate(8 124 170)"></ellipse>
      <path d="M100 58c36 0 66 22 68 56 2 32-26 60-68 60s-70-28-68-60c2-34 32-56 68-56z" fill="url(#g1c)" stroke="#37320e" stroke-width="4"></path>
      <path d="M98 60q-3-9 3-14" stroke="#4c5726" stroke-width="4" fill="none" stroke-linecap="round"></path>
      <path d="M101 46c5-4 10-2 11 3-4 4-10 2-11-3z" fill="#5c6e2a" stroke="#3a4519" stroke-width="2.5" transform="rotate(35 106 48)"></path>
      <ellipse cx="64" cy="102" rx="17" ry="12" fill="#5c6e2a" stroke="#3a4519" stroke-width="2.5"></ellipse>
      <circle cx="58" cy="99" r="2.4" fill="#3a4519"></circle>
      <circle cx="70" cy="106" r="1.9" fill="#3a4519"></circle>
      <ellipse cx="138" cy="144" rx="14" ry="10" fill="#5c6e2a" stroke="#3a4519" stroke-width="2.5"></ellipse>
      <circle cx="142" cy="142" r="2.2" fill="#3a4519"></circle>
      <ellipse cx="122" cy="88" rx="9" ry="6" fill="#465522"></ellipse>
      <path d="M70 106q8 8 16 0" stroke="#23200c" stroke-width="4.5" fill="none" stroke-linecap="round"></path>
      <path d="M114 106q8 8 16 0" stroke="#23200c" stroke-width="4.5" fill="none" stroke-linecap="round"></path>
      <circle cx="78" cy="114" r="4" fill="#23200c"></circle>
      <circle cx="122" cy="114" r="4" fill="#23200c"></circle>
      <path d="M62 96l18-6M138 90l-18 6" stroke="#37320e" stroke-width="4" stroke-linecap="round"></path>
      <path d="M84 146q8-8 16 0t16 0" stroke="#23200c" stroke-width="4.5" fill="none" stroke-linecap="round"></path>
      <path d="M64 158q4 10 0 18M140 156q2 10 7 15" stroke="#37320e" stroke-width="3.5" fill="none" stroke-linecap="round"></path>
      <path d="M88 173q0 10-5 14M115 173q1 9 6 13" stroke="#4c5726" stroke-width="4" fill="none" stroke-linecap="round"></path>
    </svg>"""

CAT_HEALTHY = r"""<svg width="150" height="150" viewBox="0 0 200 200" role="img" aria-label="החתול, המחמד הנבחר">
        <defs><radialGradient id="g1d" cx="42%" cy="30%" r="80%"><stop offset="0%" stop-color="#b3bac7"></stop><stop offset="60%" stop-color="#8d94a3"></stop><stop offset="100%" stop-color="#71788a"></stop></radialGradient></defs>
        <ellipse cx="100" cy="176" rx="50" ry="8" fill="#000" opacity=".3"></ellipse>
        <ellipse cx="80" cy="166" rx="12" ry="8" fill="#7d8494" stroke="#565c6b" stroke-width="3.5"></ellipse>
        <ellipse cx="120" cy="166" rx="12" ry="8" fill="#7d8494" stroke="#565c6b" stroke-width="3.5"></ellipse>
        <path d="M54 74 40 32l34 20z" fill="url(#g1d)" stroke="#565c6b" stroke-width="4" stroke-linejoin="round"></path>
        <path d="M146 74l14-42-34 20z" fill="url(#g1d)" stroke="#565c6b" stroke-width="4" stroke-linejoin="round"></path>
        <path d="M52 66 45 44l18 11z" fill="#e8a5b5"></path>
        <path d="M148 66l7-22-18 11z" fill="#e8a5b5"></path>
        <ellipse cx="100" cy="114" rx="64" ry="56" fill="url(#g1d)" stroke="#565c6b" stroke-width="4"></ellipse>
        <circle cx="78" cy="104" r="7" fill="#22242c"></circle>
        <circle cx="122" cy="104" r="7" fill="#22242c"></circle>
        <circle cx="80.5" cy="101.5" r="2.3" fill="#fff"></circle>
        <circle cx="124.5" cy="101.5" r="2.3" fill="#fff"></circle>
        <path d="M69 97h18M113 97h18" stroke="#565c6b" stroke-width="4" stroke-linecap="round"></path>
        <path d="M96 118l4 4 4-4" stroke="#22242c" stroke-width="4" fill="none" stroke-linecap="round" stroke-linejoin="round"></path>
        <path d="M92 128q4 5 8 0q4 5 8 0" stroke="#22242c" stroke-width="3.5" fill="none" stroke-linecap="round"></path>
        <ellipse cx="66" cy="120" rx="8" ry="4.5" fill="#e8a5b5" opacity=".55"></ellipse>
        <ellipse cx="134" cy="120" rx="8" ry="4.5" fill="#e8a5b5" opacity=".55"></ellipse>
        <path d="M42 114H20M44 128l-20 7M158 114h22M156 128l20 7" stroke="#565c6b" stroke-width="3.5" stroke-linecap="round"></path>
      </svg>"""

ICON_POTATO = r"""<svg width="52" height="52" viewBox="0 0 200 200" aria-label="תפוח אדמה"><defs><radialGradient id="gt1" cx="42%" cy="32%" r="78%"><stop offset="0%" stop-color="#f2cb72"></stop><stop offset="65%" stop-color="#dda94a"></stop><stop offset="100%" stop-color="#bd8a2e"></stop></radialGradient></defs><path d="M100 34c38 0 68 30 70 68 2 38-30 68-70 68s-72-30-70-68c2-38 32-68 70-68z" fill="url(#gt1)" stroke="#8a6220" stroke-width="6"></path><circle cx="76" cy="100" r="9" fill="#3b2c12"></circle><circle cx="124" cy="100" r="9" fill="#3b2c12"></circle><circle cx="79" cy="97" r="3" fill="#fff"></circle><circle cx="127" cy="97" r="3" fill="#fff"></circle><ellipse cx="64" cy="120" rx="10" ry="6" fill="#e08b4f" opacity=".55"></ellipse><ellipse cx="136" cy="120" rx="10" ry="6" fill="#e08b4f" opacity=".55"></ellipse><path d="M90 130q10 6 20 0" stroke="#3b2c12" stroke-width="6" fill="none" stroke-linecap="round"></path></svg>"""

ICON_BRAIN = r"""<svg width="52" height="52" viewBox="0 0 200 200" aria-label="מוח"><defs><radialGradient id="gt2" cx="42%" cy="30%" r="80%"><stop offset="0%" stop-color="#f6afc5"></stop><stop offset="60%" stop-color="#e2799c"></stop><stop offset="100%" stop-color="#c65f82"></stop></radialGradient></defs><path d="M100 38c-16 0-24 9-32 9-18 0-34 15-34 34 0 28 20 47 42 49 8 10 16 12 24 12s16-2 24-12c22-2 42-21 42-49 0-19-16-34-34-34-8 0-16-9-32-9z" fill="url(#gt2)" stroke="#9c4364" stroke-width="6"></path><path d="M100 40v98" stroke="#c25a7e" stroke-width="5"></path><path d="M60 76q12 8 0 18M138 76q-12 8 0 18" stroke="#c25a7e" stroke-width="5" fill="none" stroke-linecap="round"></path><circle cx="78" cy="98" r="8.5" fill="#4a1f2e"></circle><circle cx="122" cy="98" r="8.5" fill="#4a1f2e"></circle><circle cx="81" cy="95" r="2.8" fill="#fff"></circle><circle cx="125" cy="95" r="2.8" fill="#fff"></circle><path d="M90 122q10 6 20 0" stroke="#4a1f2e" stroke-width="6" fill="none" stroke-linecap="round"></path></svg>"""

ICON_PLANT = r"""<svg width="52" height="52" viewBox="0 0 200 200" aria-label="עציץ"><defs><linearGradient id="gt3" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#e08a52"></stop><stop offset="100%" stop-color="#b25a2c"></stop></linearGradient></defs><path d="M100 106V64" stroke="#4f8c68" stroke-width="6" fill="none"></path><path d="M98 84q-26-4-32-32 28 0 32 32z" fill="#7dc9a6" stroke="#4f8c68" stroke-width="4" stroke-linejoin="round"></path><path d="M102 74q4-28 34-32-4 28-34 32z" fill="#8fd6b4" stroke="#4f8c68" stroke-width="4" stroke-linejoin="round"></path><path d="M58 106h84l-6 24H64l-6-24z" fill="url(#gt3)" stroke="#8a4520" stroke-width="5" stroke-linejoin="round"></path><path d="M66 130h68l-8 42H74l-8-42z" fill="url(#gt3)" stroke="#8a4520" stroke-width="5" stroke-linejoin="round"></path><circle cx="86" cy="148" r="6.5" fill="#4a2410"></circle><circle cx="114" cy="148" r="6.5" fill="#4a2410"></circle><circle cx="88" cy="146" r="2.2" fill="#fff"></circle><circle cx="116" cy="146" r="2.2" fill="#fff"></circle><path d="M94 160q6 4 12 0" stroke="#4a2410" stroke-width="4.5" fill="none" stroke-linecap="round"></path></svg>"""

ICON_FISH = r"""<svg width="52" height="52" viewBox="0 0 200 200" aria-label="דג זהב"><defs><linearGradient id="gt4" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#bfe3ef" stop-opacity=".25"></stop><stop offset="100%" stop-color="#5a9cb8" stop-opacity=".55"></stop></linearGradient><radialGradient id="gt4f" cx="40%" cy="35%" r="80%"><stop offset="0%" stop-color="#f5b268"></stop><stop offset="100%" stop-color="#dd7f33"></stop></radialGradient></defs><circle cx="100" cy="106" r="64" fill="url(#gt4)" stroke="#8d94a3" stroke-width="6"></circle><path d="M46 90q28 10 54 0t54 0" stroke="#7db6cc" stroke-width="4" fill="none"></path><path d="M64 66q8-10 20-12" stroke="#fff" stroke-width="5" opacity=".5" stroke-linecap="round" fill="none"></path><ellipse cx="96" cy="122" rx="27" ry="17" fill="url(#gt4f)" stroke="#a85e20" stroke-width="4"></ellipse><path d="M121 122l18-12v24l-18-12z" fill="#f0a457" stroke="#a85e20" stroke-width="4" stroke-linejoin="round"></path><path d="M92 106q6-8 12-1" stroke="#a85e20" stroke-width="3.5" fill="none"></path><circle cx="84" cy="118" r="4.5" fill="#3a2410"></circle><circle cx="85.5" cy="116.5" r="1.6" fill="#fff"></circle><circle cx="74" cy="98" r="3" fill="#fff" opacity=".7"></circle><circle cx="80" cy="86" r="2.2" fill="#fff" opacity=".6"></circle></svg>"""

ICON_ROBOT = r"""<svg width="52" height="52" viewBox="0 0 200 200" aria-label="רובוט נעול"><defs><linearGradient id="gt5" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#c3d8dd"></stop><stop offset="100%" stop-color="#8fabb3"></stop></linearGradient></defs><path d="M100 56V36" stroke="#8fabb3" stroke-width="6"></path><circle cx="100" cy="28" r="9" fill="#ffd66b" stroke="#6b858c" stroke-width="4"></circle><rect x="44" y="56" width="112" height="96" rx="22" fill="url(#gt5)" stroke="#6b858c" stroke-width="6"></rect><rect x="58" y="74" width="84" height="52" rx="12" fill="#22303a"></rect><circle cx="82" cy="100" r="8" fill="#7de3d2"></circle><circle cx="118" cy="100" r="8" fill="#7de3d2"></circle><path d="M92 138h16M74 152v12M126 152v12" stroke="#6b858c" stroke-width="6" stroke-linecap="round"></path></svg>"""
