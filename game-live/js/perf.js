// Performance layer. The models are built from hundreds of small meshes, which is fine to author
// but kills phones (each mesh is a draw call, twice with shadows). This file merges them:
//  - bakeRig(): per animated joint, merges every rigid mesh under it into one vertex-coloured mesh
//    (+ one outline mesh). Joints keep moving; parts listed in `keep` stay separate.
//  - optimizeScene(): merges all static scenery by area, bakes props, and turns repeated props
//    (crates, dominoes, pins, fences...) into one InstancedMesh each.
//  - adaptive resolution: drops the pixel ratio if the device can't hold its frame rate.
(function () {
  const { scene } = G;
  const vcMats = {};
  function vcMat(emissive) {
    const k = emissive || 0;
    return vcMats[k] || (vcMats[k] = new THREE.MeshToonMaterial({ vertexColors: true, gradientMap: G.ramp, emissive: k }));
  }
  const inkMat = new THREE.MeshBasicMaterial({ color: 0x2b1d3a, side: THREE.BackSide });

  const isInk = m => m.isMeshBasicMaterial && m.side === THREE.BackSide;
  const mergeable = m => m && !Array.isArray(m) && !m.map && !m.transparent &&
    (m.isMeshToonMaterial || m.isMeshLambertMaterial || m.isMeshStandardMaterial || isInk(m));
  const geoSig = g => g.type + JSON.stringify(g.parameters || g.uuid);

  // Merge a list of {geo, m (Matrix4), color} into one non-indexed BufferGeometry.
  function merge(items, withColor) {
    let n = 0;
    const flat = items.map(it => { const g = it.geo.index ? it.geo.toNonIndexed() : it.geo; n += g.attributes.position.count; return g; });
    const pos = new Float32Array(n * 3), nor = new Float32Array(n * 3), col = withColor ? new Float32Array(n * 3) : null;
    const v = new THREE.Vector3(), nm = new THREE.Matrix3();
    let o = 0;
    items.forEach((it, k) => {
      const g = flat[k], P = g.attributes.position, N = g.attributes.normal;
      nm.getNormalMatrix(it.m);
      const flip = it.m.determinant() < 0; // mirrored parts: keep triangle winding facing out
      for (let i = 0; i < P.count; i++) {
        const src = flip && i % 3 ? (i % 3 === 1 ? i + 1 : i - 1) : i;
        v.fromBufferAttribute(P, src).applyMatrix4(it.m);
        pos[o * 3] = v.x; pos[o * 3 + 1] = v.y; pos[o * 3 + 2] = v.z;
        if (N) v.fromBufferAttribute(N, src).applyMatrix3(nm).normalize(); else v.set(0, 1, 0);
        nor[o * 3] = v.x; nor[o * 3 + 1] = v.y; nor[o * 3 + 2] = v.z;
        if (col) { col[o * 3] = it.color.r; col[o * 3 + 1] = it.color.g; col[o * 3 + 2] = it.color.b; }
        o++;
      }
    });
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    geo.setAttribute('normal', new THREE.BufferAttribute(nor, 3));
    if (col) geo.setAttribute('color', new THREE.BufferAttribute(col, 3));
    geo.computeBoundingSphere();
    return geo;
  }

  // Identical props produce identical merged geometry: share it so they can be instanced.
  const bakeCache = new Map();

  // Merge the rigid meshes under each joint. `joints` move independently; `keep` nodes are left alone.
  function bakeRig(root, joints, keep) {
    joints = joints || [root];
    const jointSet = new Set(joints), keepSet = new Set(keep || []);
    if (!jointSet.has(root)) { jointSet.add(root); joints.unshift(root); }
    root.updateMatrixWorld(true);
    const buckets = new Map(); // joint -> {vc: Map(emissive -> items), ink: items, removed: []}
    const visit = (obj, j) => {
      for (const c of obj.children.slice()) {
        if (keepSet.has(c)) continue;
        const cj = jointSet.has(c) ? c : j;
        if (c.isMesh && !jointSet.has(c) && c.visible && mergeable(c.material) && !c.isInstancedMesh) {
          let b = buckets.get(j);
          if (!b) buckets.set(j, b = { vc: new Map(), ink: [], parts: [], cast: false });
          const inv = new THREE.Matrix4().copy(j.matrixWorld).invert();
          const m = inv.multiply(c.matrixWorld);
          if (isInk(c.material)) b.ink.push({ geo: c.geometry, m, color: null });
          else {
            const e = c.material.emissive ? c.material.emissive.getHex() : 0;
            if (!b.vc.has(e)) b.vc.set(e, []);
            b.vc.get(e).push({ geo: c.geometry, m, color: c.material.color });
          }
          b.cast = b.cast || c.castShadow;
          b.parts.push(c);
        }
        visit(c, cj);
      }
    };
    for (const j of joints) if (j === root) visit(root, root);
    for (const [j, b] of buckets) {
      // Detach merged parts but keep their children (joints / kept nodes) attached to the joint.
      for (const p of b.parts) {
        for (const ch of p.children.slice()) {
          if (b.parts.includes(ch)) continue;
          j.attach(ch);
        }
        p.parent && p.parent.remove(p);
      }
      const add = (items, material, withColor) => {
        if (!items.length) return;
        const sig = items.map(it => geoSig(it.geo) + (it.color ? it.color.getHexString() : 'ink') + it.m.elements.map(x => x.toFixed(3)).join(',')).join('|');
        let geo = bakeCache.get(sig);
        if (!geo) { geo = merge(items, withColor); bakeCache.set(sig, geo); }
        const mesh = new THREE.Mesh(geo, material);
        mesh.castShadow = b.cast && !isInk(material); mesh.receiveShadow = !isInk(material);
        mesh.userData.baked = true;
        j.add(mesh);
      };
      for (const [e, items] of b.vc) add(items, vcMat(e), true);
      add(b.ink, inkMat, false);
    }
    return root;
  }
  G.bakeRig = bakeRig;

  // ---------- Glow sprite for things that used to carry a point light ----------
  const glowTex = (() => {
    const c = document.createElement('canvas'); c.width = c.height = 64;
    const x = c.getContext('2d'), g = x.createRadialGradient(32, 32, 0, 32, 32, 32);
    g.addColorStop(0, 'rgba(255,230,120,0.9)'); g.addColorStop(0.4, 'rgba(255,210,63,0.35)'); g.addColorStop(1, 'rgba(255,210,63,0)');
    x.fillStyle = g; x.fillRect(0, 0, 64, 64);
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
  })();
  G.glowSprite = function (size) {
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending }));
    s.scale.setScalar(size || 4);
    return s;
  };

  // ---------- Character friction ----------
  // cannon.js over-applies friction to rotation-locked bodies (~35x): the goat lost ~92% of its speed
  // every physics step, so it crawled. Characters whose speed the code sets directly get a frictionless
  // material against the world; the controllers do the braking. Knocked-down people and wrecked cars
  // switch back to normal friction so they don't slide forever.
  const slick = new CANNON.Material('slick'), solid = new CANNON.Material('solid');
  function setupFriction() {
    const w = G.world;
    for (const b of w.bodies) if (!b.material) b.material = solid;
    w.addContactMaterial(new CANNON.ContactMaterial(slick, solid, { friction: 0, restitution: 0 }));
    w.addContactMaterial(new CANNON.ContactMaterial(solid, solid, { friction: w.defaultContactMaterial.friction, restitution: w.defaultContactMaterial.restitution }));
    G.goat.body.material = slick;
    const upd = G.updatePeople;
    G.updatePeople = function (dt, time) {
      for (const h of G.humans) h.body.material = h.state === 'down' ? solid : slick;
      for (const c of G.cars) c.body.material = c.wrecked || c.parkedT > 0 ? solid : slick;
      return upd.call(this, dt, time);
    };
  }

  // ---------- Whole-scene pass, run once after everything is built ----------
  G.optimizeScene = function () {
    setupFriction();
    // 1. Point lights cost every pixel on screen and force shader recompiles when added/removed.
    const lights = [];
    scene.traverse(o => { if (o.isPointLight) lights.push(o); });
    for (const l of lights) {
      const glow = G.glowSprite(5);
      glow.position.copy(l.position);
      l.parent.add(glow);
      l.parent.remove(l);
    }

    for (const s of G.statues) {
      bakeRig(s.m, [s.m], []);
      // The glow sprite already makes statues pop; their outline shell doubled ~6k triangles each.
      const ink = []; s.m.traverse(o => { if (o.isMesh && isInk(o.material)) ink.push(o); });
      for (const o of ink) o.parent.remove(o);
    }
    if (G.jetpackPickup) bakeRig(G.jetpackPickup, [G.jetpackPickup], []);

    // 2. Props: bake each multi-part prop into one mesh.
    const dynSet = new Set();
    for (const b of G.dyn) {
      if (!b.mesh) continue;
      dynSet.add(b.mesh);
      if (b.human || b.car || b.mesh.userData.baked) continue;
      // A mergeable mesh with child parts (domino + pips, pin + band): wrap it so it merges with them.
      if (b.mesh.isMesh && b.mesh.children.length && mergeable(b.mesh.material)) {
        const m = b.mesh, g = new THREE.Group();
        g.position.copy(m.position); g.quaternion.copy(m.quaternion);
        scene.remove(m); m.position.set(0, 0, 0); m.quaternion.set(0, 0, 0, 1);
        g.add(m); scene.add(g);
        dynSet.delete(m); dynSet.add(g); b.mesh = g;
      }
      let count = 0; b.mesh.traverse(o => { if (o.isMesh) count++; });
      if (count > 1 || b.mesh.isGroup) bakeRig(b.mesh, [b.mesh], []);
      // A group holding a single mesh at its origin collapses to that mesh.
      const m = b.mesh;
      if (m.isGroup && m.children.length === 1 && m.children[0].isMesh && m.children[0].position.lengthSq() === 0 && m.children[0].quaternion.w === 1 && m.children[0].scale.x === 1) {
        const c = m.children[0];
        scene.remove(m); m.remove(c); c.position.copy(m.position); c.quaternion.copy(m.quaternion); scene.add(c);
        b.mesh = c; dynSet.delete(m); dynSet.add(c);
      }
    }

    // 3. Repeated props become one InstancedMesh per shape.
    const groups = new Map(), canonical = new Map();
    for (const b of G.dyn) {
      const m = b.mesh;
      if (!m || !m.isMesh || m.children.length || b.human || b.car || b.type === CANNON.Body.KINEMATIC || b.tag?.kind === 'barrel') continue;
      if (m.material.transparent || Array.isArray(m.material)) continue;
      const gs = m.userData.baked ? m.geometry.uuid : geoSig(m.geometry);
      if (!canonical.has(gs)) canonical.set(gs, m.geometry);
      const key = gs + '|' + m.material.uuid;
      if (!groups.has(key)) groups.set(key, { geo: canonical.get(gs), mat: m.material, bodies: [] });
      groups.get(key).bodies.push(b);
    }
    const instancers = [];
    for (const g of groups.values()) {
      if (g.bodies.length < 4) continue;
      const im = new THREE.InstancedMesh(g.geo, g.mat, g.bodies.length);
      im.castShadow = im.receiveShadow = true;
      im.frustumCulled = false;
      scene.add(im);
      g.bodies.forEach((b, i) => {
        const proxy = new THREE.Object3D(); proxy.scale.copy(b.mesh.scale);
        scene.remove(b.mesh);
        b.mesh = proxy; b.inst = { im, i }; b.synced = false;
      });
      instancers.push(im);
    }

    // 4. Static scenery: merge everything that never moves, bucketed by area so off-screen chunks are culled.
    const skip = new Set([...dynSet, ...G.dyn.map(b => b.mesh)]);
    for (const s of G.statues) skip.add(s.m);
    for (const c of G.clouds || []) skip.add(c);
    for (const t of G.tramps || []) skip.add(t);
    [G.jetpackPickup, G.fountainSpray, G.flag, G.sky].forEach(o => o && skip.add(o));
    scene.traverse(o => { if (o.userData.noBake) skip.add(o); });
    const isSkipped = o => { for (let p = o; p; p = p.parent) if (skip.has(p) || !p.visible) return true; return false; };
    scene.updateMatrixWorld(true);
    const CELL = 80, buckets = new Map(), parts = [];
    scene.traverse(o => {
      if (!o.isMesh || o.isInstancedMesh || !o.visible || !mergeable(o.material) || isSkipped(o) || o.userData.baked) return;
      const p = new THREE.Vector3().setFromMatrixPosition(o.matrixWorld);
      const ink = isInk(o.material);
      const e = !ink && o.material.emissive ? o.material.emissive.getHex() : 0;
      const key = [Math.floor(p.x / CELL), Math.floor(p.z / CELL), ink ? 'ink' : e, o.castShadow ? 1 : 0, o.receiveShadow ? 1 : 0].join('|');
      if (!buckets.has(key)) buckets.set(key, { ink, e, cast: o.castShadow, recv: o.receiveShadow, items: [] });
      buckets.get(key).items.push({ geo: o.geometry, m: o.matrixWorld.clone(), color: ink ? null : o.material.color });
      parts.push(o);
    });
    for (const o of parts) {
      for (const ch of o.children.slice()) if (!parts.includes(ch)) o.parent.attach(ch);
      o.parent && o.parent.remove(o);
    }
    for (const b of buckets.values()) {
      const mesh = new THREE.Mesh(merge(b.items, !b.ink), b.ink ? inkMat : vcMat(b.e));
      mesh.castShadow = b.cast; mesh.receiveShadow = b.recv;
      mesh.matrixAutoUpdate = false; mesh.userData.baked = true;
      scene.add(mesh);
    }
    // Clouds: one mesh each.
    for (const c of G.clouds || []) bakeRig(c, [c], []);

    // 5. Sync: instanced bodies write matrices; sleeping bodies are skipped.
    const tmp = new THREE.Matrix4(), q = new THREE.Quaternion(), p3 = new THREE.Vector3();
    const SLEEPING = CANNON.Body.SLEEPING;
    G.sync = function () {
      const dirty = new Set();
      for (const b of G.dyn) {
        if (b.sleepState === SLEEPING && b.synced) continue;
        b.synced = b.sleepState === SLEEPING;
        if (b.inst) {
          p3.set(b.position.x, b.position.y, b.position.z);
          q.set(b.quaternion.x, b.quaternion.y, b.quaternion.z, b.quaternion.w);
          tmp.compose(p3, q, b.mesh.scale);
          b.inst.im.setMatrixAt(b.inst.i, tmp);
          dirty.add(b.inst.im);
        } else G.syncMesh(b);
      }
      for (const im of dirty) im.instanceMatrix.needsUpdate = true;
    };
    const removeBody = G.removeBody;
    G.removeBody = function (b) {
      if (b.inst) { b.inst.im.setMatrixAt(b.inst.i, tmp.makeScale(0, 0, 0)); b.inst.im.instanceMatrix.needsUpdate = true; }
      removeBody(b);
    };
    G.sync();
    let meshes = 0; scene.traverse(o => { if (o.isMesh) meshes++; });
    G.perf = { meshes, instancers: instancers.length };
  };

  // ---------- Level of detail for townsfolk: hide ink outlines and eyes when far away ----------
  let lodT = 0;
  G.lodTick = function (dt) {
    lodT -= dt;
    if (lodT > 0) return;
    lodT = 0.25;
    const cam = G.camera.position;
    for (const h of G.humans) {
      const far = h.body.position.distanceTo(cam) > (G.mobile ? 26 : 40);
      if (h.lodFar === far) continue;
      h.lodFar = far;
      if (!h.lodParts) { h.lodParts = []; h.rig.traverse(o => { if (o.isMesh && (isInk(o.material) || h.eyes.includes(o))) h.lodParts.push(o); }); }
      for (const o of h.lodParts) o.visible = !far;
    }
  };

  // ---------- Adaptive resolution: keep 30+ fps on weaker phones ----------
  let acc = 0, frames = 0, slow = 0, settled = false;
  G.perfTick = function (dt) {
    if (settled) return;
    acc += dt; frames++;
    if (dt > 1 / 40) slow++;
    if (acc < 2.5) return;
    const r = G.renderer, pr = r.getPixelRatio();
    if (slow > frames * 0.4) {
      if (pr > 1.01) r.setPixelRatio(Math.max(1, pr - 0.25));
      else if (G.sun.castShadow) { G.sun.castShadow = false; r.shadowMap.enabled = false; scene.traverse(o => { if (o.material && !Array.isArray(o.material)) o.material.needsUpdate = true; }); }
      else if (pr > 0.76) r.setPixelRatio(0.75);
      else settled = true;
    } else if (acc > 12) settled = true;
    acc = 0; frames = 0; slow = 0;
  };
})();
