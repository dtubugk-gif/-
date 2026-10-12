// Goat model builders. Each part has its origin at its joint so the same parts
// work assembled (normal goat) or as separate ragdoll pieces.
(function () {
  const { mat, shade, outline } = G;

  const PAL = {
    fur: 0xf3ede0, tuft: 0xe2d8c4, spot: 0xb08d6a, horn: 0x8a7560, hoof: 0x3a2f28,
    snout: 0xe9c8b5, beard: 0xd8cfc0, collar: 0xe53935, bell: 0xffc400, eye: 0xffffff, pupil: 0x111111, inner: 0xf2a7b5
  };
  const GOLD = Object.fromEntries(Object.keys(PAL).map(k => [k, 0xffc400]));

  function M(pal, key, extra) { return pal === GOLD ? mat(0xffc400, { emissive: 0x4a3200 }) : mat(pal[key], extra); }
  const mesh = (geo, m) => shade(new THREE.Mesh(geo, m));

  function makeParts(pal) {
    pal = pal || PAL;
    const fine = pal !== GOLD; // statues skip the small fur details: six of them are on screen at once
    const ball = (r, key, w, h) => mesh(new THREE.SphereGeometry(r, w || 10, h || 8), M(pal, key));
    // Torso: barrel body, cream belly, fluffy chest ruff, shaggy spine, patchy spots
    const torso = new THREE.Group();
    const barrel = mesh(new THREE.CapsuleGeometry(0.34, 0.62, 6, 14), M(pal, 'fur'));
    barrel.rotation.x = Math.PI / 2; barrel.scale.set(0.92, 1, 1);
    torso.add(barrel);
    const belly = ball(0.3, 'tuft', 12, 8); belly.position.set(0, -0.14, 0.02); belly.scale.set(0.95, 0.55, 1.5); torso.add(belly);
    for (const [x, y, z, s] of [[0.2, 0.18, -0.12, 0.7], [-0.22, 0.05, 0.15, 0.5], [0.12, 0.05, 0.3, 0.35]]) {
      const sp = ball(0.2, 'spot'); sp.position.set(x, y, z); sp.scale.set(0.45, s, 1.1); torso.add(sp);
    }
    if (fine) {
      for (const [x, y, z, r] of [[0, 0.02, 0.5, 0.2], [0.13, 0.08, 0.46, 0.15], [-0.13, 0.08, 0.46, 0.15], [0, -0.14, 0.44, 0.16], [0.09, -0.22, 0.38, 0.11], [-0.09, -0.22, 0.38, 0.11]]) {
        const t = ball(r, 'tuft', 8, 6); t.position.set(x, y, z); torso.add(t);
      }
      for (const [x, z] of [[0.24, 0.28], [-0.24, 0.28], [0.24, -0.3], [-0.24, -0.3]]) { const f = ball(0.14, 'fur', 8, 6); f.position.set(x, 0.06, z); torso.add(f); }
    }
    for (let k = 0; k < (fine ? 9 : 6); k++) {
      const t = mesh(new THREE.ConeGeometry(0.07, 0.24, 5), M(pal, 'tuft'));
      t.position.set((k % 2 - 0.5) * 0.09, 0.33, 0.4 - k * 0.1); t.rotation.x = -0.95; t.rotation.z = (k % 2 - 0.5) * 0.5;
      torso.add(t);
    }
    const tail = new THREE.Group(); tail.position.set(0, 0.16, -0.62);
    const tailM = mesh(new THREE.ConeGeometry(0.08, 0.3, 6), M(pal, 'tuft')); tailM.position.y = 0.12; tail.add(tailM);
    if (fine) { const tip = ball(0.07, 'spot', 8, 6); tip.position.y = 0.27; tail.add(tip); }
    tail.rotation.x = -0.5; torso.add(tail);
    const collar = mesh(new THREE.TorusGeometry(0.2, 0.045, 6, 18), M(pal, 'collar'));
    collar.position.set(0, 0.16, 0.5); collar.rotation.x = 0.8; torso.add(collar);
    const bell = mesh(new THREE.SphereGeometry(0.08, 10, 8), M(pal, 'bell', { emissive: 0x5a3d00 }));
    bell.position.set(0, -0.05, 0.62); torso.add(bell);
    if (fine) { const clap = ball(0.03, 'hoof', 6, 4); clap.position.set(0, -0.12, 0.63); torso.add(clap); }

    // Legs: hip -> knee -> cloven hoof with a dark sock
    const legs = [], knees = [];
    for (let i = 0; i < 4; i++) {
      const hip = new THREE.Group();
      const up = mesh(new THREE.CapsuleGeometry(0.085, 0.18, 4, 8), M(pal, 'fur')); up.position.y = -0.13; hip.add(up);
      if (fine) { const fluff = ball(0.1, 'tuft', 8, 6); fluff.position.y = -0.02; fluff.scale.set(1, 0.8, 1); hip.add(fluff); }
      const knee = new THREE.Group(); knee.position.y = -0.27; hip.add(knee);
      const kneeCap = ball(0.075, 'fur', 8, 6); knee.add(kneeCap);
      const low = mesh(new THREE.CapsuleGeometry(0.065, 0.16, 4, 8), M(pal, 'fur')); low.position.y = -0.11; knee.add(low);
      const sock = mesh(new THREE.CylinderGeometry(0.068, 0.072, 0.07, 8), M(pal, 'spot')); sock.position.y = -0.2; knee.add(sock);
      for (const s of [-1, 1]) { // two toes
        const toe = mesh(new THREE.CylinderGeometry(0.03, 0.042, 0.09, 6), M(pal, 'hoof')); toe.position.set(s * 0.036, -0.27, 0.01); knee.add(toe);
      }
      legs.push(hip); knees.push(knee);
    }

    // Neck with a little mane
    const neck = new THREE.Group();
    const neckM = mesh(new THREE.CapsuleGeometry(0.14, 0.36, 4, 10), M(pal, 'fur'));
    neckM.rotation.x = Math.PI / 2; neckM.position.z = 0.25; neck.add(neckM);
    if (fine) for (let k = 0; k < 4; k++) {
      const m = mesh(new THREE.ConeGeometry(0.05, 0.18, 5), M(pal, 'tuft'));
      m.position.set(0, 0.13, 0.08 + k * 0.11); m.rotation.x = -1.1; neck.add(m);
    }

    // Head: skull, muzzle with nose pad, forelock, ridged horns, droopy-lidded googly eyes, shaggy beard
    const head = new THREE.Group();
    const skull = mesh(new THREE.CapsuleGeometry(0.19, 0.28, 4, 12), M(pal, 'fur'));
    skull.rotation.x = Math.PI / 2; skull.position.z = 0.18; head.add(skull);
    const snout = ball(0.17, 'snout', 12, 10);
    snout.position.set(0, -0.06, 0.44); snout.scale.set(1, 0.85, 0.9); head.add(snout);
    const pad = ball(0.08, 'hoof', 10, 6); pad.position.set(0, -0.01, 0.58); pad.scale.set(1.3, 0.7, 0.5); head.add(pad);
    for (const s of [-1, 1]) {
      const n = mesh(new THREE.SphereGeometry(0.025, 6, 4), M(pal, 'pupil')); n.position.set(s * 0.05, -0.01, 0.61); head.add(n);
    }
    const mouth = mesh(new THREE.BoxGeometry(0.16, 0.025, 0.04), M(pal, 'pupil')); mouth.position.set(0, -0.15, 0.52); head.add(mouth);
    for (const [x, len, rz] of fine ? [[0, 0.36, 0], [0.05, 0.26, 0.25], [-0.05, 0.26, -0.25]] : [[0, 0.32, 0]]) {
      const beard = mesh(new THREE.ConeGeometry(0.06, len, 6), M(pal, 'beard'));
      beard.position.set(x, -0.3, 0.4); beard.rotation.set(Math.PI + 0.25, 0, rz); head.add(beard);
    }
    if (fine) for (let k = 0; k < 3; k++) {
      const f = mesh(new THREE.ConeGeometry(0.05, 0.16, 5), M(pal, 'tuft'));
      f.position.set((k - 1) * 0.06, 0.2, 0.26); f.rotation.x = 0.5; f.rotation.z = (k - 1) * -0.4; head.add(f);
    }
    // Horns: tapered, ridged, sweeping back
    const curve = new THREE.CatmullRomCurve3([[0, 0, 0], [0.02, 0.17, -0.05], [0.06, 0.26, -0.22], [0.09, 0.16, -0.38], [0.09, 0.03, -0.34]].map(p => new THREE.Vector3(p[0], p[1], p[2])));
    // A tube whose radius shrinks from base to tip.
    function hornGeo(hi) {
      const g = new THREE.TubeGeometry(curve, hi ? 16 : 8, 0.062, hi ? 8 : 6, false);
      const pos = g.attributes.position, segs = hi ? 16 : 8, radial = (hi ? 8 : 6) + 1;
      const v = new THREE.Vector3(), c = new THREE.Vector3();
      for (let i = 0; i <= segs; i++) {
        const t = i / segs, k = 1 - t * 0.68;
        curve.getPointAt(t, c);
        for (let j = 0; j < radial; j++) {
          const idx = i * radial + j;
          v.fromBufferAttribute(pos, idx).sub(c).multiplyScalar(k).add(c);
          pos.setXYZ(idx, v.x, v.y, v.z);
        }
      }
      g.computeVertexNormals();
      return g;
    }
    const ears = [], eyes = [], pupils = [];
    for (const s of [-1, 1]) {
      const horn = new THREE.Group();
      horn.add(mesh(hornGeo(fine), M(pal, 'horn')));
      if (fine) for (const t of [0.22, 0.42, 0.62]) { // growth ridges
        const r = (0.062 - t * 0.042) * 1.02;
        const ring = mesh(new THREE.TorusGeometry(r, 0.011, 4, 12), M(pal, 'hoof'));
        ring.position.copy(curve.getPointAt(t));
        ring.quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), curve.getTangentAt(t));
        horn.add(ring);
      }
      horn.position.set(s * 0.09, 0.15, 0.1); horn.scale.x = s; head.add(horn);
      const ear = new THREE.Group(); ear.position.set(s * 0.18, 0.1, 0.06);
      const earM = mesh(new THREE.CapsuleGeometry(0.06, 0.18, 4, 8), M(pal, 'fur')); earM.position.x = s * 0.13; earM.rotation.z = Math.PI / 2; earM.scale.z = 0.5; ear.add(earM);
      if (fine) { const inner = mesh(new THREE.CapsuleGeometry(0.035, 0.12, 3, 6), M(pal, 'inner')); inner.position.set(s * 0.14, -0.02, 0.015); inner.rotation.z = Math.PI / 2; inner.scale.z = 0.3; ear.add(inner); }
      ear.rotation.z = s * -0.6; head.add(ear); ears.push(ear);
      const eye = mesh(new THREE.SphereGeometry(0.1, 14, 10), M(pal, 'eye'));
      eye.position.set(s * 0.15, 0.09, 0.27); head.add(eye); eyes.push(eye);
      const p = mesh(new THREE.BoxGeometry(0.09, 0.035, 0.03), M(pal, 'pupil')); // goats have sideways pupils
      p.position.z = 0.09; eye.add(p); pupils.push(p);
      if (fine) {
        const lid = mesh(new THREE.SphereGeometry(0.108, 12, 6, 0, Math.PI * 2, 0, Math.PI * 0.38), M(pal, 'fur'));
        lid.position.copy(eye.position); lid.rotation.x = 0.35; head.add(lid);
      }
    }
    return { torso, legs, knees, neck, head, tail, bell, ears, eyes, pupils, mouth };
  }

  const HIPS = [[-0.2, 0.38], [0.2, 0.38], [-0.2, -0.38], [0.2, -0.38]];
  const NECK_AT = new THREE.Vector3(0, 0.16, 0.48), HEAD_AT = 0.5, HIP_Y = -0.05;

  function assemble(p) {
    const rig = new THREE.Group();
    rig.add(p.torso);
    p.legs.forEach((l, i) => { l.position.set(HIPS[i][0], HIP_Y, HIPS[i][1]); p.torso.add(l); });
    p.neck.position.copy(NECK_AT); p.neck.rotation.x = -0.75; p.torso.add(p.neck);
    p.head.position.z = HEAD_AT; p.head.rotation.x = 0.85; p.neck.add(p.head);
    return rig;
  }

  G.goatLayout = { HIPS, NECK_AT, HEAD_AT, HIP_Y };
  G.makeGoatParts = makeParts;
  G.assembleGoat = assemble;
  G.makeGoatStatue = function () {
    const p = makeParts(GOLD);
    const g = assemble(p);
    p.legs.forEach((l, i) => { l.rotation.x = i < 2 ? -0.3 : 0.3; });
    g.scale.setScalar(1.4);
    return outline(g, 0.02);
  };
  G.makeJetpack = function () {
    const g = new THREE.Group();
    for (const x of [-0.17, 0.17]) {
      const t = mesh(new THREE.CapsuleGeometry(0.13, 0.42, 4, 10), mat(0xcfd6e0)); t.position.x = x; g.add(t);
      const n = mesh(new THREE.ConeGeometry(0.13, 0.22, 10), mat(0xe53935)); n.position.set(x, 0.42, 0); g.add(n);
      const noz = mesh(new THREE.CylinderGeometry(0.07, 0.1, 0.12, 8), mat(0x2b1d3a)); noz.position.set(x, -0.38, 0); g.add(noz);
    }
    const strap = mesh(new THREE.BoxGeometry(0.6, 0.08, 0.2), mat(0x2b1d3a)); g.add(strap);
    return outline(g, 0.02);
  };
})();
