// The town: roads, houses with walkable roofs, trees, scenery and street furniture.
(function () {
  const { scene, world, mat, shade, boxMesh, addStatic, addStaticQ, tilt, addBox, addSphere, outline } = G;
  const V3 = CANNON.Vec3, Q = CANNON.Quaternion;
  const mobile = G.mobile;
  const qAxis = (x, y, z, a) => { const q = new Q(); q.setFromAxisAngle(new V3(x, y, z), a); return q; };
  const rand = (() => { let s = 7; return () => (s = (s * 16807) % 2147483647) / 2147483647; })();

  // ---------- Ground & roads ----------
  const ground = new THREE.Mesh(new THREE.CircleGeometry(700, 48), mat(0x6fd06a));
  ground.rotation.x = -Math.PI / 2;
  ground.receiveShadow = true;
  scene.add(ground);
  function flat(geo, x, z, color, y) {
    const m = new THREE.Mesh(geo, mat(color));
    m.rotation.x = -Math.PI / 2;
    m.position.set(x, y, z);
    m.receiveShadow = true;
    scene.add(m);
    return m;
  }
  const R = G.ringR;
  flat(new THREE.RingGeometry(R - 7, R + 7, 96), 0, 0, 0xd9cfb8, 0.02);   // sidewalks
  flat(new THREE.RingGeometry(R - 5, R + 5, 96), 0, 0, 0x4a4458, 0.03);   // asphalt
  for (let i = 0; i < 48; i++) {
    const a = i / 48 * Math.PI * 2;
    const d = boxMesh(0.35, 0.02, 2.2, 0xffd23f);
    d.position.set(Math.cos(a) * R, 0.045, Math.sin(a) * R);
    d.rotation.y = -a;
    d.castShadow = false;
    scene.add(d);
  }
  flat(new THREE.PlaneGeometry(9, 104), 0, 0, 0x4a4458, 0.025);
  flat(new THREE.CircleGeometry(16, 40), 0, 0, 0xe8d9b5, 0.035);          // plaza
  flat(new THREE.RingGeometry(15.4, 16, 40), 0, 0, 0xc9b48a, 0.04);

  // ---------- Hills and clouds ----------
  for (let i = 0; i < 12; i++) {
    const a = i / 12 * Math.PI * 2 + rand() * 0.3, r = 270 + rand() * 60, s = 60 + rand() * 50;
    const h = new THREE.Mesh(new THREE.SphereGeometry(s, 20, 10), mat([0x5cb85a, 0x4fa64d, 0x76c26a][i % 3]));
    h.scale.y = 0.35;
    h.position.set(Math.cos(a) * r, -4, Math.sin(a) * r);
    scene.add(h);
  }
  const cloudMat = mat(0xffffff, { emissive: 0x9fb8cc });
  G.clouds = [];
  for (let i = 0; i < (mobile ? 9 : 14); i++) {
    const c = new THREE.Group();
    const n = 4 + Math.floor(rand() * 3);
    for (let k = 0; k < n; k++) {
      const s = new THREE.Mesh(new THREE.SphereGeometry(4 + rand() * 4, 12, 8), cloudMat);
      s.position.set(k * 5 - n * 2.5, rand() * 2, rand() * 4);
      c.add(s);
    }
    c.position.set((rand() - 0.5) * 500, 70 + rand() * 40, (rand() - 0.5) * 500);
    c.userData.v = 1.5 + rand() * 2;
    scene.add(c);
    G.clouds.push(c);
  }

  // ---------- Houses (gabled roofs you can climb and slide off) ----------
  const wallColors = [0xff9bb0, 0x8fb8ff, 0xffe07a, 0xc3a6ff, 0x86e3cc, 0xffb07a];
  const roofColors = [0xd2453a, 0x2f6f9f, 0x8a4b2d, 0x3e8f6e];
  const trim = mat(0xfff7e0), glass = mat(0xbfe9ff, { emissive: 0x1d3b52 });
  function windowAt(parent, x, y, z, ry, shutter) {
    const g = new THREE.Group();
    const f = new THREE.Mesh(new THREE.BoxGeometry(1.5, 1.5, 0.12), trim);
    const p = new THREE.Mesh(new THREE.BoxGeometry(1.2, 1.2, 0.14), glass);
    const bar = new THREE.Mesh(new THREE.BoxGeometry(0.08, 1.2, 0.16), trim);
    const bar2 = new THREE.Mesh(new THREE.BoxGeometry(1.2, 0.08, 0.16), trim);
    g.add(f, p, bar, bar2);
    for (const s of [-1, 1]) { const sh = new THREE.Mesh(new THREE.BoxGeometry(0.4, 1.5, 0.1), mat(shutter)); sh.position.x = s * 0.98; g.add(sh); }
    g.position.set(x, y, z); g.rotation.y = ry;
    parent.add(g);
  }
  function house(x, z, w, d, h, rot, i) {
    const body = addStatic(w, h, d, x, h / 2, z, wallColors[i % wallColors.length], rot, { kind: 'house' });
    const m = body.mesh;
    const base = boxMesh(w + 0.2, 0.5, d + 0.2, 0x9a8f86); base.position.y = -h / 2 + 0.25; m.add(base);
    // Roof prism
    const rh = d * 0.42, o = 0.6, half = d / 2;
    const shape = new THREE.Shape();
    shape.moveTo(-(half + o), -o * rh / half); shape.lineTo(half + o, -o * rh / half); shape.lineTo(0, rh); shape.closePath();
    const rg = new THREE.ExtrudeGeometry(shape, { depth: w + 0.8, bevelEnabled: false });
    rg.translate(0, 0, -(w + 0.8) / 2); rg.rotateY(Math.PI / 2);
    const roof = shade(new THREE.Mesh(rg, mat(roofColors[i % roofColors.length])));
    roof.position.y = h / 2;
    m.add(roof);
    const chim = boxMesh(0.9, rh, 0.9, 0xa0522d); chim.position.set(w / 4, h / 2 + rh * 0.7, -d / 8); m.add(chim);
    // Door, step, windows
    const door = boxMesh(1.3, 2.3, 0.15, 0x7a4426); door.position.set(0, -h / 2 + 1.15, d / 2 + 0.05); m.add(door);
    const knob = new THREE.Mesh(new THREE.SphereGeometry(0.08, 8, 6), mat(0xffd23f)); knob.position.set(0.4, 0, 0.1); door.add(knob);
    const step = boxMesh(2, 0.25, 0.8, 0x9a8f86); step.position.set(0, -h / 2 + 0.12, d / 2 + 0.4); m.add(step);
    const shut = roofColors[(i + 1) % roofColors.length];
    for (const sx of [-w / 3.2, w / 3.2]) windowAt(m, sx, h * 0.12, d / 2 + 0.06, 0, shut);
    for (const sx of [-w / 3.2, w / 3.2]) windowAt(m, sx, h * 0.12, -d / 2 - 0.06, Math.PI, shut);
    if (h > 8) for (const sx of [-w / 3.2, 0, w / 3.2]) windowAt(m, sx, h * 0.36, d / 2 + 0.06, 0, shut);
    // Physics roof slabs, one per slope
    const a = Math.atan2(rh, half), L = Math.hypot(half, rh) + o, qy = qAxis(0, 1, 0, rot || 0);
    for (const s of [1, -1]) {
      const local = new V3(0, h + rh / 2 - 0.1, s * half / 2);
      const pos = qy.vmult(local).vadd(new V3(x, 0, z));
      addStaticQ(w + 0.8, 0.3, L, pos, qy.mult(qAxis(1, 0, 0, s * a)), null, { kind: 'roof' });
    }
    return body;
  }
  [
    [-28, -28, 9, 8, 7, 0.3], [28, -30, 10, 9, 9, -0.2], [-32, 22, 8, 10, 6, 0.1],
    [34, 26, 11, 8, 8, 0.4], [-14, -42, 8, 7, 6, 0], [16, 44, 9, 9, 10, 0.6],
    [-85, 10, 12, 12, 14, 0], [85, -15, 10, 14, 8, 0.2]
  ].forEach((h, i) => house(h[0], h[1], h[2], h[3], h[4], h[5], i));

  // ---------- Trees and bushes ----------
  const leafCols = [0x3fae4f, 0x2f9e44, 0x56c25e];
  function tree(x, z, s, pine) {
    addStatic(0.7 * s, 4 * s, 0.7 * s, x, 2 * s, z, null);
    const g = new THREE.Group();
    const trunk = shade(new THREE.Mesh(new THREE.CylinderGeometry(0.3 * s, 0.45 * s, 4 * s, 8), mat(0x8a5a33)));
    trunk.position.y = 2 * s; g.add(trunk);
    if (pine) {
      for (let k = 0; k < 3; k++) {
        const c = shade(new THREE.Mesh(new THREE.ConeGeometry((2.6 - k * 0.6) * s, 2.8 * s, 9), mat(0x2a8a4a)));
        c.position.y = (3.6 + k * 1.6) * s; g.add(c);
      }
    } else {
      for (let k = 0; k < 3; k++) {
        const b = shade(new THREE.Mesh(new THREE.SphereGeometry((1.9 - k * 0.3) * s, 12, 9), mat(leafCols[(k + Math.abs(Math.round(x + z))) % 3])));
        b.position.set(Math.cos(k * 2.1) * 0.9 * s, (4.6 + k * 0.9) * s, Math.sin(k * 2.1) * 0.9 * s);
        g.add(b);
      }
    }
    g.position.set(x, 0, z);
    scene.add(g);
  }
  for (let i = 0; i < (mobile ? 34 : 50); i++) {
    const a = i * 2.39996, r = 75 + (i * 37 % 60);
    tree(Math.cos(a) * r, Math.sin(a) * r, 0.8 + (i % 5) * 0.15, i % 3 === 0);
  }
  [[-12, 18], [14, -16], [-46, -6], [44, 4], [-20, 30], [26, 12]].forEach((p, i) => tree(p[0], p[1], 1, i % 2 === 1));
  for (let i = 0; i < 26; i++) {
    const a = rand() * Math.PI * 2, r = 20 + rand() * 30;
    const g = new THREE.Group();
    for (let k = 0; k < 3; k++) {
      const b = shade(new THREE.Mesh(new THREE.SphereGeometry(0.7 + rand() * 0.4, 10, 8), mat(leafCols[k])));
      b.position.set(k * 0.7 - 0.7, 0.4, rand() * 0.4);
      g.add(b);
    }
    g.position.set(Math.cos(a) * r, 0, Math.sin(a) * r);
    if (Math.hypot(g.position.x, g.position.z) > 17 && Math.abs(g.position.x) > 6) scene.add(g);
  }

  // Grass tufts and flowers (instanced so phones stay fast)
  const offRoad = (x, z) => { const d = Math.hypot(x, z); return d > 17 && Math.abs(d - R) > 8 && Math.abs(x) > 5.5; };
  const tufts = new THREE.InstancedMesh(new THREE.ConeGeometry(0.12, 0.6, 3), mat(0x3fae4f), mobile ? 500 : 1400);
  const flowers = new THREE.InstancedMesh(new THREE.SphereGeometry(0.16, 6, 4), mat(0xffffff), mobile ? 160 : 400);
  const dummy = new THREE.Object3D(), fc = [0xff4fa3, 0xffd23f, 0xffffff, 0xb48fff, 0xff7043].map(c => new THREE.Color(c));
  [[tufts, 0.3], [flowers, 0.45]].forEach(([im, y]) => {
    let n = 0;
    while (n < im.count) {
      const x = (rand() - 0.5) * 220, z = (rand() - 0.5) * 220;
      if (!offRoad(x, z)) continue;
      dummy.position.set(x, y, z); dummy.rotation.set(0, rand() * 3, (rand() - 0.5) * 0.4);
      dummy.scale.setScalar(0.7 + rand() * 0.8);
      dummy.updateMatrix();
      im.setMatrixAt(n, dummy.matrix);
      if (im === flowers) im.setColorAt(n, fc[n % fc.length]);
      n++;
    }
    scene.add(im);
  });

  // ---------- Plaza fountain ----------
  const vertical = qAxis(1, 0, 0, Math.PI / 2);
  function addCylinder(r, h, x, y, z, color) {
    const b = new CANNON.Body({ mass: 0 });
    b.addShape(new CANNON.Cylinder(r, r, h, 16), new V3(), vertical);
    b.position.set(x, y, z);
    b.tag = { kind: 'static' };
    world.addBody(b);
    const m = shade(new THREE.Mesh(new THREE.CylinderGeometry(r, r, h, 24), mat(color)));
    m.position.set(x, y, z);
    scene.add(m);
    return m;
  }
  addCylinder(4, 1, 0, 0.5, 0, 0xb8c2d6);
  const water = new THREE.Mesh(new THREE.CylinderGeometry(3.6, 3.6, 0.1, 24), mat(0x4fc3f7, { emissive: 0x0b4f73 }));
  water.position.y = 1.0; scene.add(water);
  addCylinder(0.6, 3, 0, 2, 0, 0xdfe6f3);
  const bowl = shade(new THREE.Mesh(new THREE.CylinderGeometry(1.2, 0.6, 0.4, 16), mat(0xdfe6f3)));
  bowl.position.y = 3.6; scene.add(bowl);
  const spray = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.5, 1.2, 10, 1, true), new THREE.MeshBasicMaterial({ color: 0xbfe9ff, transparent: true, opacity: 0.55 }));
  spray.position.y = 4.4; scene.add(spray);
  G.fountainSpray = spray;

  // ---------- Street furniture (all knockable) ----------
  function bench(x, z, ry) {
    const g = new THREE.Group();
    const seat = boxMesh(2, 0.15, 0.6, 0xc98b4b); g.add(seat);
    const back = boxMesh(2, 0.5, 0.12, 0xc98b4b); back.position.set(0, 0.35, -0.26); g.add(back);
    for (const sx of [-0.8, 0.8]) { const l = boxMesh(0.12, 0.45, 0.5, 0x444444); l.position.set(sx, -0.28, 0); g.add(l); }
    const b = addBox(2, 0.6, 0.6, x, 0.55, z, 0, 25, { kind: 'prop', pts: 20, name: 'BENCH PRESS' }, g);
    b.quaternion.setFromAxisAngle(new V3(0, 1, 0), ry);
    b.sleep();
  }
  for (let i = 0; i < 6; i++) { const a = i / 6 * Math.PI * 2 + 0.5; bench(Math.cos(a) * 12, Math.sin(a) * 12, -a - Math.PI / 2); }
  function trashCan(x, z) {
    const g = new THREE.Group();
    const c = shade(new THREE.Mesh(new THREE.CylinderGeometry(0.4, 0.34, 1, 12), mat(0x3e8f6e))); g.add(c);
    const lid = shade(new THREE.Mesh(new THREE.CylinderGeometry(0.44, 0.44, 0.1, 12), mat(0x2d6b52))); lid.position.y = 0.55; g.add(lid);
    addBox(0.75, 1.1, 0.75, x, 0.55, z, 0, 6, { kind: 'prop', pts: 15, name: 'LITTERING' }, g).sleep();
  }
  [[6.5, -24], [-6.5, -34], [6.5, 30], [-6.5, 40], [18, 2], [-18, -2]].forEach(p => trashCan(p[0], p[1]));
  function lamp(x, z) {
    addStatic(0.25, 5, 0.25, x, 2.5, z, 0x2b1d3a);
    const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.35, 10, 8), mat(0xfff3b0, { emissive: 0xffd23f }));
    bulb.position.set(x, 5.2, z); scene.add(bulb);
  }
  for (let i = 0; i < 8; i++) { const a = i / 8 * Math.PI * 2; lamp(Math.cos(a) * (R - 6.5), Math.sin(a) * (R - 6.5)); }

  // ---------- Ramps, tower, pond ----------
  tilt(addStatic(8, 1, 22, -55, 4.2, -60, 0xff9f5a, 0, { kind: 'ramp' }), -0.42, 0, 0);
  tilt(addStatic(7, 1, 16, 46, 2.6, 4, 0xff9f5a, 0, { kind: 'ramp' }), -0.33, 0, 0);

  const tx = 50, tz = -62;
  for (const [ox, oz] of [[-3, -3], [3, -3], [-3, 3], [3, 3]]) addStatic(0.6, 24, 0.6, tx + ox, 12, tz + oz, 0x9aa5b8);
  addStatic(8, 0.6, 8, tx, 12, tz, 0xc9a26b);
  addStatic(8, 0.6, 8, tx, 24.3, tz, 0xc9a26b);
  for (let k = 0; k < 4; k++) {
    const brace = boxMesh(0.2, 8.6, 0.2, 0x7d8899);
    brace.position.set(tx + (k < 2 ? -3 : 3), 6 + (k % 2) * 12, tz);
    brace.rotation.x = 0.75 * (k % 2 ? 1 : -1);
    scene.add(brace);
  }
  const flag = boxMesh(1.6, 1, 0.05, 0xff4fa3); flag.position.set(tx + 3.8, 28, tz - 3); scene.add(flag);
  const pole = boxMesh(0.12, 4, 0.12, 0x2b1d3a); pole.position.set(tx + 3, 26.3, tz - 3); scene.add(pole);
  G.flag = flag;

  flat(new THREE.CircleGeometry(11, 32), 70, 60, 0x4fc3f7, 0.05).scale.set(1, 0.65, 1);
  for (let i = 0; i < 18; i++) {
    const a = i / 18 * Math.PI * 2;
    const st = shade(new THREE.Mesh(new THREE.DodecahedronGeometry(0.6 + rand() * 0.3, 0), mat(0x9aa5b8)));
    st.position.set(70 + Math.cos(a) * 11.3, 0.2, 60 + Math.sin(a) * 7.4);
    scene.add(st);
  }
  // Rubber ducks
  function duck(x, z) {
    const g = new THREE.Group();
    const b = shade(new THREE.Mesh(new THREE.SphereGeometry(0.4, 12, 8), mat(0xffd23f))); b.scale.set(1, 0.8, 1.2); g.add(b);
    const h = shade(new THREE.Mesh(new THREE.SphereGeometry(0.25, 10, 8), mat(0xffd23f))); h.position.set(0, 0.35, 0.25); g.add(h);
    const beak = shade(new THREE.Mesh(new THREE.ConeGeometry(0.1, 0.25, 6), mat(0xff7043))); beak.rotation.x = Math.PI / 2; beak.position.set(0, 0.33, 0.52); g.add(beak);
    for (const s of [-1, 1]) { const e = new THREE.Mesh(new THREE.SphereGeometry(0.04, 6, 4), mat(0x111111)); e.position.set(s * 0.12, 0.42, 0.43); g.add(e); }
    outline(g, 0.02);
    addSphere(0.4, x, 0.5, z, 0, 1, { kind: 'prop', pts: 40, name: 'QUACK', duck: true }, g).sleep();
  }
  [[66, 58], [72, 62], [74, 56], [68, 64]].forEach(p => duck(p[0], p[1]));
  G.spawn.set(0, 2, 22);
})();
