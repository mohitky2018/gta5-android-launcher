// Touch controls for the game page, injected by the launcher's GameServer into index.html (before the page's own module script).
// The game runs in Chrome: Android WebView is never cross-origin isolated (checked on WebView 158 with COOP/COEP and Document-Isolation-Policy),
// so it has no SharedArrayBuffer for the engine's threads. The controls synthesize the keyboard and mouse events that the page's input bridge
// (installInput in index.html) already understands. Options come from the launcher's settings, as URL parameters (GameSettings.buildLaunchUrl).
(() => {
	'use strict';
	const q = new URLSearchParams(location.search);
	if (q.get('touch') === '0' || !(navigator.maxTouchPoints > 0)) return;
	const num = (k, d, lo, hi) => { const v = parseFloat(q.get(k)); return Number.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : d; };
	const opt = {
		cam: q.get('cam') === 'stick' ? 'stick' : 'swipe',
		camSpeed: num('cs', 1.6, 0.2, 6),
		aimSpeed: num('as', 1.0, 0.2, 6),
		fireAims: q.get('fa') !== '0',
		sprintButton: q.get('sb') !== '0',
		steer: q.get('steer') === '1',
		invertY: q.get('iy') === '1',
		size: num('bs', 1, 0.6, 1.6),
		opacity: num('bo', 0.75, 0.15, 1),
		gyro: ['aim', 'always'].includes(q.get('gyro')) ? q.get('gyro') : 'off',
		haptics: q.get('hap') !== '0',
		showFps: q.get('showfps') === '1',
	};

	// ---- simulated pointer lock -------------------------------------------------------------------------------------------------------------
	// The page turns mouse movement into camera movement only while document.pointerLockElement is the canvas, and Android Chrome cannot lock a
	// touch pointer. Report the canvas as locked so movementX/movementY reach the engine.
	const canvas = () => document.getElementById('canvas');
	try {
		Object.defineProperty(Document.prototype, 'pointerLockElement', { configurable: true, get() { return canvas(); } });
		Element.prototype.requestPointerLock = function () { return Promise.resolve(); };
		Document.prototype.exitPointerLock = function () {};
	} catch (e) { console.warn('touch: pointer lock emulation failed: ' + e); }
	const announceLock = () => document.dispatchEvent(new Event('pointerlockchange'));

	// ---- synthetic input (reference counted: the stick, a button and a gamepad can hold the same key) ------------------------------------------
	const KEY_NAMES = { Space: ' ', ShiftLeft: 'Shift', ControlLeft: 'Control', Tab: 'Tab', CapsLock: 'CapsLock', Escape: 'Escape', Enter: 'Enter',
		Backspace: 'Backspace', ArrowUp: 'ArrowUp', ArrowDown: 'ArrowDown', ArrowLeft: 'ArrowLeft', ArrowRight: 'ArrowRight', Equal: '=' };
	const keyName = (code) => KEY_NAMES[code] || (code.startsWith('Key') ? code.slice(3).toLowerCase() : code.startsWith('Digit') ? code.slice(5) : code);
	const keysDown = new Map();
	const key = (code, down) => {
		const n = keysDown.get(code) || 0;
		if (down) { keysDown.set(code, n + 1); if (n) return; }
		else { if (!n) return; keysDown.set(code, n - 1); if (n > 1) return; }
		dispatchEvent(new KeyboardEvent(down ? 'keydown' : 'keyup', { code, key: keyName(code), bubbles: true, cancelable: true }));
	};
	const tapKey = (code) => { key(code, true); setTimeout(() => key(code, false), 60); };
	const mouseDown = new Map();
	const mouse = (button, down) => {
		const n = mouseDown.get(button) || 0;
		if (down) { mouseDown.set(button, n + 1); if (n) return; }
		else { if (!n) return; mouseDown.set(button, n - 1); if (n > 1) return; }
		const c = canvas();
		if (c) c.dispatchEvent(new MouseEvent(down ? 'mousedown' : 'mouseup', { button, buttons: down ? 1 : 0, bubbles: true, cancelable: true }));
	};
	const aiming = () => (mouseDown.get(2) || 0) > 0;
	// The engine adds movement to an Int32 counter: send whole pixels and carry the fraction.
	let fx = 0, fy = 0;
	const look = (dx, dy) => {
		const k = aiming() ? opt.aimSpeed : opt.camSpeed;
		fx += dx * k; fy += dy * k * (opt.invertY ? -1 : 1);
		const ix = Math.trunc(fx), iy = Math.trunc(fy);
		if (!ix && !iy) return;
		fx -= ix; fy -= iy;
		dispatchEvent(new MouseEvent('mousemove', { movementX: ix, movementY: iy, bubbles: true }));
	};
	const buzz = () => { if (opt.haptics) try { navigator.vibrate && navigator.vibrate(8); } catch (e) { /* not allowed yet */ } };

	// ---- layout -----------------------------------------------------------------------------------------------------------------------------
	// [id, icon, label, action, right(vmin), bottom(vmin), size(vmin), layouts, flags]. Action: a key code, 'M0'/'M2' (mouse buttons) or 'FIRE'.
	// Positions from the bottom-right corner in vmin (steer buttons from the bottom-left), scaled by the button size option.
	const FOOT = 'foot', CAR = 'car';
	const BUTTONS = [
		['fire', 'fire', 'Fire', 'FIRE', 6, 16, 19, [FOOT, CAR], { drag: true }],
		['aim', 'aim', 'Aim', 'M2', 29, 34, 14, [FOOT], { drag: true }],
		['jump', 'jump', 'Jump', 'Space', 27, 4, 13, [FOOT]],
		['sprint', 'sprint', 'Sprint', 'ShiftLeft', 6, 39, 13, [FOOT], { hidden: !opt.sprintButton }],
		['crouch', 'crouch', 'Crouch', 'ControlLeft', 44, 4, 12, [FOOT]],
		['reload', 'reload', 'Reload', 'KeyR', 46, 19, 11, [FOOT]],
		['cover', 'cover', 'Cover', 'KeyQ', 46, 33, 11, [FOOT]],
		['enter', 'enter_exit', 'Enter vehicle', 'KeyF', 22, 54, 12, [FOOT]],
		['weapons', 'weapons', 'Weapons', 'Tab', 6, 56, 12, [FOOT]],
		['special', 'special', 'Ability', 'CapsLock', 38, 52, 11, [FOOT]],
		['gas', 'gas', 'Gas', 'KeyW', 6, 39, 15, [CAR]],
		['brake', 'brake', 'Brake', 'KeyS', 27, 4, 14, [CAR]],
		['handbrake', 'handbrake', 'Handbrake', 'Space', 45, 4, 12, [CAR]],
		['horn', 'horn', 'Horn', 'KeyE', 46, 19, 11, [CAR]],
		['lookback', 'look_back', 'Look back', 'KeyC', 29, 34, 11, [CAR]],
		['radio', 'radio', 'Radio', 'KeyQ', 46, 33, 11, [CAR]],
		['lights', 'lights', 'Lights', 'KeyH', 38, 52, 11, [CAR]],
		['exit', 'enter_exit', 'Exit vehicle', 'KeyF', 22, 54, 12, [CAR], { then: FOOT }],
		['steerl', 'steer_left', 'Steer left', 'KeyA', -6, 6, 17, [CAR], { left: true, hidden: !opt.steer }],
		['steerr', 'steer_right', 'Steer right', 'KeyD', -27, 6, 17, [CAR], { left: true, hidden: !opt.steer }],
	];
	const TOP = [		// top bar, from the right
		['pause', 'pause', 'Pause', 'Escape'],
		['phone', 'phone', 'Phone', 'ArrowUp'],
		['camera', 'camera', 'Camera', 'KeyV'],
		['mode', 'gas', 'Driving layout', 'toggleMode'],
		['menukeys', 'accept', 'Menu keys', 'toggleMenu'],		// ids differ from the panels' (#tc-menu, #tc-cfg)
		['settings', 'settings', 'Controls layout', 'toggleCfg'],
	];
	const MENU = [		// arrows + accept/back for the phone and menus
		['m-up', 'up', 'Up', 'ArrowUp', 1, 2], ['m-left', 'left', 'Left', 'ArrowLeft', 0, 1], ['m-right', 'right', 'Right', 'ArrowRight', 2, 1],
		['m-down', 'down', 'Down', 'ArrowDown', 1, 0], ['m-ok', 'accept', 'Select', 'Enter', 3.4, 1.5], ['m-back', 'back', 'Back', 'Backspace', 3.4, 0.5],
	];

	// Custom positions from the layout editor: id -> [x, y] of the button centre as fractions of the viewport.
	let custom = {};
	try { custom = JSON.parse(localStorage.getItem('touch-layout') || '{}') || {}; } catch (e) { /* storage unavailable */ }
	const saveLayout = () => { try { localStorage.setItem('touch-layout', JSON.stringify(custom)); } catch (e) { /* storage unavailable */ } };

	const ICON = (name) => '/__touch/icons/' + name + '.png';
	const css = `
#tc{position:fixed;inset:0;z-index:2147483000;display:none;touch-action:none;user-select:none;-webkit-user-select:none;-webkit-touch-callout:none;
 --s:${opt.size};font:600 12px system-ui,sans-serif;color:#fff}
#tc.show{display:block}
#tc.hidden>*:not(#tc-cfg){display:none!important}
#tc .tc-btn{position:absolute;border-radius:50%;background:rgba(0,0,0,.38) center/62% no-repeat;border:2px solid rgba(255,255,255,.45);
 box-sizing:border-box;touch-action:none;box-shadow:0 0 6px rgba(0,0,0,.4);opacity:${opt.opacity}}
#tc .tc-btn.on{background-color:rgba(253,184,19,.55);border-color:#fdb813;opacity:1}
#tc .tc-btn.hide{display:none}
#tc #tc-fire{border-color:rgba(255,90,90,.75)}
#tc .tc-top{position:absolute;top:calc(env(safe-area-inset-top) + 2vmin);display:flex;gap:2vmin;right:calc(env(safe-area-inset-right) + 3vmin)}
#tc .tc-top .tc-btn{position:relative;width:calc(10vmin*var(--s));height:calc(10vmin*var(--s))}
#tc .tc-btn.sel{border-color:#fdb813}
#tc.edit .tc-btn:not(.tc-sys){outline:2px dashed #fdb813;outline-offset:3px;opacity:1}
.tc-base{position:absolute;width:calc(30vmin*var(--s));height:calc(30vmin*var(--s));border-radius:50%;border:2px solid rgba(255,255,255,.35);
 background:rgba(0,0,0,.2);transform:translate(-50%,-50%);pointer-events:none;opacity:${opt.opacity}}
.tc-knob{position:absolute;left:50%;top:50%;width:42%;height:42%;border-radius:50%;background:rgba(253,184,19,.85);transform:translate(-50%,-50%)}
.tc-base.idle{opacity:${opt.opacity * 0.55}}
.tc-base.off{display:none}
#tc-menu{position:absolute;display:none;left:calc(env(safe-area-inset-left) + 4vmin);top:calc(env(safe-area-inset-top) + 4vmin);width:calc(52vmin*var(--s));height:calc(36vmin*var(--s))}
#tc-menu.open{display:block}
#tc-menu .tc-btn{width:calc(11vmin*var(--s));height:calc(11vmin*var(--s))}
#tc-cfg{position:absolute;display:none;left:50%;top:50%;transform:translate(-50%,-50%);background:rgba(16,17,21,.94);border:1px solid #444;
 border-radius:12px;padding:16px 20px;min-width:260px;font-size:14px;text-align:center}
#tc-cfg.open{display:block}
#tc-cfg h3{margin:0 0 6px;color:#fdb813;font-size:16px}
#tc-cfg p{margin:0 0 12px;color:#aaa;font-size:12px}
#tc-cfg button{margin:4px;padding:9px 14px;border-radius:8px;border:0;background:#2a2d35;color:#fff;font-weight:700}
#tc-cfg button.pri{background:#fdb813;color:#000}
#tc-editbar{position:absolute;display:none;top:calc(env(safe-area-inset-top) + 3vmin);left:50%;transform:translateX(-50%);background:rgba(0,0,0,.8);
 border-radius:20px;padding:8px 10px 8px 16px;font-size:13px;align-items:center;gap:10px}
#tc.edit #tc-editbar{display:flex}
#tc-editbar button{padding:7px 14px;border-radius:14px;border:0;background:#fdb813;color:#000;font-weight:700}
#tc-hint{position:fixed;left:50%;top:10px;transform:translateX(-50%);z-index:2147483001;background:rgba(0,0,0,.75);color:#fff;padding:8px 14px;
 border-radius:16px;font:13px system-ui,sans-serif;pointer-events:none}
`;

	let root, mode = FOOT, editing = false;
	const btnEls = [];
	const ACTIONS = {
		toggleMode: () => setMode(mode === FOOT ? CAR : FOOT),
		toggleMenu: (el) => { el.classList.toggle('sel', document.getElementById('tc-menu').classList.toggle('open')); },
		toggleCfg: () => document.getElementById('tc-cfg').classList.toggle('open'),
	};

	const press = (action, down) => {
		if (action === 'M0' || action === 'M2') mouse(action === 'M0' ? 0 : 2, down);
		else if (action === 'FIRE') { if (opt.fireAims) mouse(2, down); mouse(0, down); }		// fire also aims: right button held with the left
		else key(action, down);
	};

	function place(el, spec) {
		const [id, , , , right, bottom, size, , flags] = spec;
		el.style.width = el.style.height = 'calc(' + size + 'vmin * var(--s))';
		const c = custom[id];
		el.style.left = el.style.right = el.style.top = el.style.bottom = '';
		if (c) {
			el.style.left = 'calc(' + (c[0] * 100) + '% - ' + size / 2 + 'vmin * var(--s))';
			el.style.top = 'calc(' + (c[1] * 100) + '% - ' + size / 2 + 'vmin * var(--s))';
		} else if (flags && flags.left) {
			el.style.left = 'calc(env(safe-area-inset-left) + ' + (-right) + 'vmin * var(--s))';
			el.style.bottom = 'calc(env(safe-area-inset-bottom) + ' + bottom + 'vmin * var(--s))';
		} else {
			el.style.right = 'calc(env(safe-area-inset-right) + ' + right + 'vmin * var(--s))';
			el.style.bottom = 'calc(env(safe-area-inset-bottom) + ' + bottom + 'vmin * var(--s))';
		}
	}

	// A button: hold = key/mouse held. drag buttons also turn the camera while held (fire and aim while looking). In edit mode a drag moves it.
	function makeButton(spec, parent, system) {
		const [id, icon, label, action, , , , layouts, flags] = spec;
		const el = document.createElement('div');
		el.className = 'tc-btn' + (system ? ' tc-sys' : '');
		el.id = 'tc-' + id;
		el.setAttribute('aria-label', label);
		el.style.backgroundImage = 'url(' + ICON(icon) + ')';
		el.layouts = layouts;
		el.flags = flags || {};
		if (layouts) place(el, spec);
		const fn = ACTIONS[action];
		let pid = null, lx = 0, ly = 0, held = false;
		el.addEventListener('pointerdown', (e) => {
			e.preventDefault(); e.stopPropagation();		// preventDefault: no compatibility mouse events (a stray mouseup would release Fire)
			if (pid !== null) return;
			pid = e.pointerId; lx = e.clientX; ly = e.clientY;
			try { el.setPointerCapture(pid); } catch (err) { /* already released */ }
			if (editing) return;
			buzz();
			if (fn) { fn(el); return; }
			held = true;
			el.classList.add('on');
			press(action, true);
		});
		el.addEventListener('pointermove', (e) => {
			if (e.pointerId !== pid) return;
			if (editing && layouts) {
				custom[id] = [e.clientX / innerWidth, e.clientY / innerHeight];
				place(el, spec);
				return;
			}
			if (held && el.flags.drag) { look(e.clientX - lx, e.clientY - ly); lx = e.clientX; ly = e.clientY; }
		});
		const up = (e) => {
			if (e.pointerId !== pid) return;
			pid = null;
			if (editing) { saveLayout(); return; }
			if (!held) return;
			held = false;
			el.classList.remove('on');
			press(action, false);
			if (el.flags.then) setMode(el.flags.then);
		};
		el.addEventListener('pointerup', up);
		el.addEventListener('pointercancel', up);
		el.addEventListener('lostpointercapture', up);
		el.release = () => { if (held) { held = false; el.classList.remove('on'); press(action, false); } pid = null; };
		parent.appendChild(el);
		btnEls.push(el);
		return el;
	}

	function setMode(m) {
		if (m === mode) return;
		releaseAll();
		mode = m;
		for (const el of btnEls) if (el.layouts) el.classList.toggle('hide', !el.layouts.includes(m) || !!el.flags.hidden);
		const mb = document.getElementById('tc-mode');
		if (mb) mb.style.backgroundImage = 'url(' + ICON(m === FOOT ? 'gas' : 'sprint') + ')';
		if (leftStick) leftStick.base.classList.toggle('off', m === CAR && opt.steer);
	}

	// ---- sticks: floating (they start where the thumb lands) ---------------------------------------------------------------------------------
	function makeStick(cls, restLeft) {
		const base = document.createElement('div'); base.className = 'tc-base idle ' + cls;
		const knob = document.createElement('div'); knob.className = 'tc-knob';
		base.appendChild(knob); root.appendChild(base);
		const s = {
			base, pid: null, cx: 0, cy: 0, x: 0, y: 0,
			radius() { return base.offsetWidth / 2 || 60; },
			rest() {
				const r = this.radius(), h = innerHeight;
				base.style.top = (h - r - 0.08 * h) + 'px';
				base.style.left = restLeft ? (r + 0.06 * h) + 'px' : (innerWidth * 0.62) + 'px';
			},
			start(e) { this.pid = e.pointerId; this.cx = e.clientX; this.cy = e.clientY; base.style.left = this.cx + 'px'; base.style.top = this.cy + 'px'; base.classList.remove('idle'); this.move(e); },
			move(e) {
				const r = this.radius();
				let dx = (e.clientX - this.cx) / r, dy = (e.clientY - this.cy) / r;
				const len = Math.hypot(dx, dy);
				if (len > 1) { dx /= len; dy /= len; }
				knob.style.left = 50 + dx * 50 + '%'; knob.style.top = 50 + dy * 50 + '%';
				this.x = dx; this.y = dy;
				this.onMove && this.onMove(dx, dy, Math.min(1, len));
			},
			reset() {
				this.pid = null; this.x = this.y = 0;
				knob.style.left = knob.style.top = '50%'; base.classList.add('idle'); this.rest();
				this.onMove && this.onMove(0, 0, 0);
			},
		};
		return s;
	}
	let leftStick = null, rightStick = null;
	const moveKeys = { KeyW: false, KeyS: false, KeyA: false, KeyD: false, ShiftLeft: false };
	const setMoveKey = (code, on) => { if (moveKeys[code] !== on) { moveKeys[code] = on; key(code, on); } };
	function driveFromStick(dx, dy, len) {
		const dead = len < 0.25;
		setMoveKey('KeyW', !dead && dy < -0.38); setMoveKey('KeyS', !dead && dy > 0.38);
		setMoveKey('KeyA', !dead && dx < -0.38); setMoveKey('KeyD', !dead && dx > 0.38);
		setMoveKey('ShiftLeft', !opt.sprintButton && mode === FOOT && len > 0.92);		// no sprint button: sprint with the stick at its edge
	}

	// Right stick camera: continuous turning while deflected (curve for fine aim near the centre).
	let lastFrame = 0;
	function stickLoop(t) {
		const dt = lastFrame ? Math.min(50, t - lastFrame) : 16.7;
		lastFrame = t;
		if (rightStick && rightStick.pid !== null) {
			const curve = (v) => Math.sign(v) * Math.pow(Math.abs(v), 1.6);
			look(curve(rightStick.x) * 9 * dt / 16.7, curve(rightStick.y) * 6 * dt / 16.7);
		}
		requestAnimationFrame(stickLoop);
	}

	// ---- background touches: left part = movement stick, the rest = camera (swipe or right stick) -------------------------------------------
	const lookers = new Map();		// pointerId -> last position (swipe camera)
	function onBackgroundDown(e) {
		e.preventDefault();
		if (editing) return;
		try { root.setPointerCapture(e.pointerId); } catch (err) { /* gone */ }
		if (e.clientX < innerWidth * 0.42 && leftStick.pid === null && !(mode === CAR && opt.steer)) leftStick.start(e);
		else if (opt.cam === 'stick' && rightStick.pid === null) rightStick.start(e);
		else lookers.set(e.pointerId, [e.clientX, e.clientY]);
	}
	function onBackgroundMove(e) {
		if (e.pointerId === leftStick.pid) { leftStick.move(e); return; }
		if (rightStick && e.pointerId === rightStick.pid) { rightStick.move(e); return; }
		const p = lookers.get(e.pointerId);
		if (!p) return;
		look(e.clientX - p[0], e.clientY - p[1]);
		p[0] = e.clientX; p[1] = e.clientY;
	}
	function onBackgroundUp(e) {
		if (e.pointerId === leftStick.pid) leftStick.reset();
		if (rightStick && e.pointerId === rightStick.pid) rightStick.reset();
		lookers.delete(e.pointerId);
	}

	function releaseAll() {
		for (const el of btnEls) el.release && el.release();
		if (leftStick) leftStick.reset();
		if (rightStick) rightStick.reset();
		lookers.clear();
		for (const [code, n] of keysDown) if (n) { keysDown.set(code, 1); key(code, false); }
		for (const [b, n] of mouseDown) if (n) { mouseDown.set(b, 1); mouse(b, false); }
		for (const k in moveKeys) moveKeys[k] = false;
	}

	// ---- gyro aiming (devicemotion: rotation rates in the device frame, degrees per second) --------------------------------------------------
	function installGyro() {
		if (opt.gyro === 'off' || typeof DeviceMotionEvent === 'undefined') return;
		addEventListener('devicemotion', (e) => {
			const r = e.rotationRate;
			if (!r || !shown || editing || (opt.gyro === 'aim' && !aiming())) return;
			const dt = (e.interval || 16) / 1000;
			const angle = (screen.orientation && screen.orientation.angle) || 0;		// 90 = landscape-primary, 270 = landscape-secondary
			const s = angle === 270 ? -1 : 1;
			// landscape: turning left/right is a rotation about the device's x axis (beta), tilting up/down about its y axis (gamma)
			look(-s * (r.beta || 0) * dt * 12, -s * (r.gamma || 0) * dt * 12);
		});
	}

	// ---- layout panel -----------------------------------------------------------------------------------------------------------------------
	function buildCfg() {
		const p = document.createElement('div');
		p.id = 'tc-cfg';
		p.innerHTML = '<h3>Controls layout</h3><p>Camera, speeds, gyro and the other options are in the launcher\'s Settings.</p>' +
			'<button class="pri" id="tc-edit">Edit layout</button><button id="tc-reset">Reset layout</button><button id="tc-hide">Hide controls</button><br><button id="tc-close">Close</button>';
		for (const ev of ['pointerdown', 'pointermove', 'pointerup']) p.addEventListener(ev, (e) => e.stopPropagation());
		root.appendChild(p);
		p.querySelector('#tc-edit').addEventListener('click', () => { p.classList.remove('open'); setEditing(true); });
		p.querySelector('#tc-reset').addEventListener('click', () => { custom = {}; saveLayout(); relayout(); });
		p.querySelector('#tc-hide').addEventListener('click', () => { p.classList.remove('open'); setHidden(true); toast('Controls hidden: tap the top-right corner to show them'); });
		p.querySelector('#tc-close').addEventListener('click', () => p.classList.remove('open'));
		const bar = document.createElement('div');
		bar.id = 'tc-editbar';
		bar.innerHTML = '<span>Drag the buttons where you want them</span><button>Done</button>';
		bar.addEventListener('pointerdown', (e) => e.stopPropagation());
		bar.querySelector('button').addEventListener('click', () => setEditing(false));
		root.appendChild(bar);
	}
	function setEditing(on) {
		releaseAll();
		editing = on;
		root.classList.toggle('edit', on);
		if (on) for (const el of btnEls) if (el.layouts && !el.flags.hidden) el.classList.remove('hide');		// both layouts at once while editing
		else { const m = mode; mode = null; setMode(m); saveLayout(); }
	}
	function relayout() { for (const el of btnEls) if (el.layouts) place(el, BUTTONS.find((b) => 'tc-' + b[0] === el.id)); }

	let hidden = false;
	function setHidden(h) { hidden = h; if (root) { releaseAll(); root.classList.toggle('hidden', h); } }

	function build() {
		const style = document.createElement('style');
		style.textContent = css;
		document.head.appendChild(style);
		root = document.createElement('div');
		root.id = 'tc';
		root.addEventListener('pointerdown', (e) => {
			if (hidden) { if (e.clientX > innerWidth * 0.8 && e.clientY < innerHeight * 0.2) setHidden(false); return; }
			onBackgroundDown(e);
		});
		root.addEventListener('pointermove', onBackgroundMove);
		root.addEventListener('pointerup', onBackgroundUp);
		root.addEventListener('pointercancel', onBackgroundUp);
		root.addEventListener('contextmenu', (e) => e.preventDefault());
		leftStick = makeStick('tc-left', true);
		leftStick.onMove = driveFromStick;
		if (opt.cam === 'stick') { rightStick = makeStick('tc-right', false); requestAnimationFrame(stickLoop); }
		for (const b of BUTTONS) makeButton(b, root);
		const top = document.createElement('div'); top.className = 'tc-top';
		root.appendChild(top);
		for (const t of TOP) makeButton(t, top, true);
		const menu = document.createElement('div'); menu.id = 'tc-menu';
		root.appendChild(menu);
		for (const [id, icon, label, action, col, row] of MENU) {
			const el = makeButton([id, icon, label, action], menu, true);
			el.style.left = 'calc(' + col * 12 + 'vmin * var(--s))';
			el.style.bottom = 'calc(' + row * 12 + 'vmin * var(--s))';
		}
		buildCfg();
		document.body.appendChild(root);
		mode = null; setMode(FOOT);
		installGyro();
	}

	// ---- show the controls once the world is up (before that, taps go to the page's start menu) ---------------------------------------------
	let shown = false;
	function show() {
		if (shown) return;
		shown = true;
		if (!root) build();
		root.classList.add('show');
		if (hidden) root.classList.add('hidden');
		announceLock();		// the page notes the (simulated) lock in the engine's input block
		setTimeout(announceLock, 2000);
		leftStick.reset(); if (rightStick) rightStick.reset();
		if (opt.showFps) setTimeout(() => tapKey('Equal'), 500);		// the page's FPS counter (= key)
	}
	try { new BroadcastChannel('game-progress').addEventListener('message', (ev) => { if (ev.data && ev.data.world) setTimeout(show, 1200); }); } catch (e) { /* no channel */ }
	const poll = setInterval(() => {		// fallback: the page removes #loading when the world is shown
		if (document.readyState !== 'loading' && !document.getElementById('loading')) { clearInterval(poll); show(); }
	}, 1000);

	// Fullscreen and landscape on the first touch (a touch is a user gesture when the finger lifts, so pointerup). Asked for while loading,
	// so the resize happens before the world is up.
	let hint = null;
	const showHint = () => {
		if (document.fullscreenElement || hint || !document.body) return;
		hint = document.createElement('div'); hint.id = 'tc-hint'; hint.textContent = 'Tap the screen for fullscreen';
		document.body.appendChild(hint);
	};
	const dropHint = () => { if (hint) { hint.remove(); hint = null; } };
	document.addEventListener('DOMContentLoaded', () => { const st = document.createElement('style'); st.textContent = css.slice(css.indexOf('#tc-hint')); document.head.appendChild(st); showHint(); });
	addEventListener('pointerup', () => {
		if (document.fullscreenElement || !document.documentElement.requestFullscreen) return;
		document.documentElement.requestFullscreen({ navigationUI: 'hide' })
			.then(() => { dropHint(); return screen.orientation && screen.orientation.lock && screen.orientation.lock('landscape').catch(() => {}); })
			.catch(() => {});
	}, { capture: true });
	document.addEventListener('fullscreenchange', () => { if (document.fullscreenElement) dropHint(); else showHint(); });

	// Back gesture guard: one history entry absorbs the first back; a second back within 2.5 s really leaves.
	let guarded = false, lastBack = 0;
	addEventListener('pointerup', () => { if (shown && !guarded) { guarded = true; history.pushState({ tc: 1 }, ''); } }, { capture: true });
	addEventListener('popstate', () => {
		if (!guarded) return;
		if (Date.now() - lastBack < 2500) { guarded = false; history.back(); return; }
		lastBack = Date.now();
		history.pushState({ tc: 1 }, '');
		tapKey('Escape');		// the first back opens the pause menu, as on a console
		toast('Press back again to quit the game');
	});
	function toast(text) {
		const t = document.createElement('div');
		t.textContent = text;
		t.style.cssText = 'position:fixed;left:50%;bottom:12vh;transform:translateX(-50%);background:rgba(0,0,0,.8);color:#fff;padding:10px 16px;border-radius:20px;font:14px system-ui,sans-serif;z-index:2147483001;pointer-events:none';
		document.body.appendChild(t);
		setTimeout(() => t.remove(), 2500);
	}

	addEventListener('resize', () => { if (!root) return; if (leftStick.pid === null) leftStick.reset(); if (rightStick && rightStick.pid === null) rightStick.reset(); });
	addEventListener('blur', () => { if (root) releaseAll(); });
	document.addEventListener('visibilitychange', () => { if (document.hidden && root) releaseAll(); });

	// For gamepads (Gamepad API, below) and anything else that drives the game through the controls.
	window.TouchControls = { key, mouse, look, setHidden, releaseAll };

	// ---- gamepad (Gamepad API): left stick moves, right stick looks, buttons as on the controls. Hides the touch controls while in use. --------
	const PAD = { 0: 'Space', 1: 'ControlLeft', 2: 'KeyR', 3: 'KeyF', 4: 'Tab', 5: 'KeyQ', 6: 'M2', 7: 'M0', 8: 'KeyV', 9: 'Escape', 10: 'ShiftLeft', 11: 'KeyC',
		12: 'ArrowUp', 13: 'ArrowDown', 14: 'ArrowLeft', 15: 'ArrowRight' };
	const padHeld = {};
	let padActive = false;
	function padLoop() {
		const pads = navigator.getGamepads ? [...navigator.getGamepads()].filter(Boolean) : [];
		const p = pads[0];
		if (p) {
			if (!padActive && p.buttons.some((b) => b.pressed)) { padActive = true; if (root) setHidden(true); }
			for (const [i, action] of Object.entries(PAD)) {
				const b = p.buttons[i], on = !!(b && (b.pressed || b.value > 0.5));
				if (on !== !!padHeld[i]) { padHeld[i] = on; press(action, on); }
			}
			const ax = p.axes[0] || 0, ay = p.axes[1] || 0, len = Math.hypot(ax, ay);
			if (padActive) driveFromStick(ax, ay, len);
			const rx = p.axes[2] || 0, ry = p.axes[3] || 0;
			if (Math.abs(rx) > 0.12 || Math.abs(ry) > 0.12) look(rx * 9, ry * 6);
		}
		requestAnimationFrame(padLoop);
	}
	addEventListener('gamepadconnected', () => requestAnimationFrame(padLoop), { once: true });
	addEventListener('gamepaddisconnected', () => { padActive = false; if (root) { releaseAll(); setHidden(false); } });
})();
