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
    // Torso
    const torso = new THREE.Group();
    const barrel = mesh(new THREE.CapsuleGeometry(0.34, 0.62, 6, 14), M(pal, 'fur'));
    barrel.rotation.x = Math.PI / 2; barrel.scale.set(0.92, 1, 1);
    torso.add(barrel);
    for (const [x, y, z, s] of [[0.2, 0.18, -0.12, 0.7], [-0.22, 0.05, 0.15, 0.5]]) {
      const sp = mesh(new THREE.SphereGeometry(0.2, 10, 8), M(pal, 'spot'));
      sp.position.set(x, y, z); sp.scale.set(0.45, s, 1.1); torso.add(sp);
    }
    for (let k = 0; k < 6; k++) {
      const t = mesh(new THREE.ConeGeometry(0.07, 0.22, 5), M(pal, 'tuft'));
      t.position.set((k % 2 - 0.5) * 0.08, 0.33, 0.32 - k * 0.13); t.rotation.x = -0.9;
      torso.add(t);
    }
    const tail = new THREE.Group(); tail.position.set(0, 0.16, -0.62);
    const tailM = mesh(new THREE.ConeGeometry(0.08, 0.3, 6), M(pal, 'tuft')); tailM.position.y = 0.12; tail.add(tailM);
    tail.rotation.x = -0.5; torso.add(tail);
    const collar = mesh(new THREE.TorusGeometry(0.2, 0.045, 6, 18), M(pal, 'collar'));
    collar.position.set(0, 0.16, 0.5); collar.rotation.x = 0.8; torso.add(collar);
    const bell = mesh(new THREE.SphereGeometry(0.08, 10, 8), M(pal, 'bell', { emissive: 0x5a3d00 }));
    bell.position.set(0, -0.05, 0.62); torso.add(bell);

    // Legs: hip -> knee -> hoof
    const legs = [], knees = [];
    for (let i = 0; i < 4; i++) {
      const hip = new THREE.Group();
      const up = mesh(new THREE.CapsuleGeometry(0.085, 0.18, 4, 8), M(pal, 'fur')); up.position.y = -0.13; hip.add(up);
      const knee = new THREE.Group(); knee.position.y = -0.27; hip.add(knee);
      const low = mesh(new THREE.CapsuleGeometry(0.065, 0.16, 4, 8), M(pal, 'fur')); low.position.y = -0.11; knee.add(low);
      const hoof = mesh(new THREE.CylinderGeometry(0.07, 0.085, 0.09, 8), M(pal, 'hoof')); hoof.position.y = -0.27; knee.add(hoof);
      legs.push(hip); knees.push(knee);
    }

    // Neck: pivot at the shoulders, extends along +z
    const neck = new THREE.Group();
    const neckM = mesh(new THREE.CapsuleGeometry(0.14, 0.36, 4, 10), M(pal, 'fur'));
    neckM.rotation.x = Math.PI / 2; neckM.position.z = 0.25; neck.add(neckM);

    // Head: pivot at the top of the neck, snout points along +z
    const head = new THREE.Group();
    const skull = mesh(new THREE.CapsuleGeometry(0.19, 0.28, 4, 12), M(pal, 'fur'));
    skull.rotation.x = Math.PI / 2; skull.position.z = 0.18; head.add(skull);
    const snout = mesh(new THREE.SphereGeometry(0.17, 12, 10), M(pal, 'snout'));
    snout.position.set(0, -0.06, 0.44); snout.scale.set(1, 0.85, 0.9); head.add(snout);
    for (const s of [-1, 1]) {
      const n = mesh(new THREE.SphereGeometry(0.025, 6, 4), M(pal, 'pupil')); n.position.set(s * 0.06, 0, 0.59); head.add(n);
    }
    const mouth = mesh(new THREE.BoxGeometry(0.16, 0.025, 0.04), M(pal, 'pupil')); mouth.position.set(0, -0.15, 0.52); head.add(mouth);
    const beard = mesh(new THREE.ConeGeometry(0.07, 0.32, 6), M(pal, 'beard'));
    beard.position.set(0, -0.3, 0.4); beard.rotation.x = Math.PI + 0.25; head.add(beard);
    const curve = new THREE.CatmullRomCurve3([[0, 0, 0], [0.02, 0.17, -0.05], [0.06, 0.26, -0.22], [0.09, 0.16, -0.38], [0.09, 0.03, -0.34]].map(p => new THREE.Vector3(p[0], p[1], p[2])));
    const ears = [], eyes = [], pupils = [];
    for (const s of [-1, 1]) {
      const horn = mesh(new THREE.TubeGeometry(curve, 12, 0.045, 6), M(pal, 'horn'));
      horn.position.set(s * 0.09, 0.15, 0.1); horn.scale.x = s; head.add(horn);
      const ear = new THREE.Group(); ear.position.set(s * 0.18, 0.1, 0.06);
      const earM = mesh(new THREE.CapsuleGeometry(0.06, 0.18, 4, 8), M(pal, 'fur')); earM.position.x = s * 0.13; earM.rotation.z = Math.PI / 2; earM.scale.z = 0.5; ear.add(earM);
      ear.rotation.z = s * -0.6; head.add(ear); ears.push(ear);
      const eye = mesh(new THREE.SphereGeometry(0.1, 14, 10), M(pal, 'eye'));
      eye.position.set(s * 0.15, 0.09, 0.27); head.add(eye); eyes.push(eye);
      const p = mesh(new THREE.BoxGeometry(0.09, 0.035, 0.03), M(pal, 'pupil')); // goats have sideways pupils
      p.position.z = 0.09; eye.add(p); pupils.push(p);
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
