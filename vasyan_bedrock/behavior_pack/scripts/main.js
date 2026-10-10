// Bobik, the dumbest neural network, and Klod, his smart (and just as foul-mouthed) colleague.
// Both follow their owner, talk out loud (voice + subtitle above the hotbar, never the chat), react to what
// happens around them and argue with each other. Bobik "helps" in the worst possible way, Klod actually helps
// and cleans up after Bobik.
// Stable Script API only (@minecraft/server 2.0.0, @minecraft/server-ui 2.0.0), Bedrock 1.21.90+.
import { world, system, GameMode, Player, ItemStack, EntityDamageCause } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";
import { VOICE } from "./voice.js";

/** @typedef {import("@minecraft/server").Entity} Entity */
/** @typedef {import("@minecraft/server").Block} Block */
/** @typedef {import("@minecraft/server").Vector3} Vector3 */

// Bobik keeps the id of the first version ("vasyan"), so the ones already living in worlds stay.
const BOBIK = "vasyan:vasyan";
const KLOD = "vasyan:klod";
const CHIPS = { "vasyan:ai_chip": BOBIK, "vasyan:klod_chip": KLOD };
const CHIP_OF = { [BOBIK]: "vasyan:ai_chip", [KLOD]: "vasyan:klod_chip" };
const VOICE_OF = { [BOBIK]: "vasyan", [KLOD]: "klod" }; // key in VOICE
const COLOR = { [BOBIK]: "§b", [KLOD]: "§6" };
const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];
// names given automatically (in any language, plus the first version's); a name tag the player chose stays
const DEFAULT_NAMES = new Set(["Васян", ...Object.values(VOICE).flatMap((c) => Object.values(c).map((l) => l.ui.name))]);

// entity dynamic properties
const OWNER = "vasyan:owner"; // id of the player the companion belongs to
const NEXT = "vasyan:next"; // tick of the next "help" (Bobik) or useful thing (Klod)
const QUIET = "vasyan:quiet"; // tick until which he promised to keep quiet
const STAY = "vasyan:stay"; // true while told to stay in place
// player dynamic property prefix: id of the player's current Bobik / Klod ("vasyan:cur_vasyan", "vasyan:cur_klod")
const CURRENT = "vasyan:cur_";
// world dynamic properties
const ENABLED = "vasyan:enabled"; // false turns Bobik's pranks off (/scriptevent vasyan:toggle)
const MAT = "vasyan:mat"; // false: no swearing (menu or /scriptevent vasyan:mat)
const LANG = "vasyan:lang"; // "uk" or "ru": the language both of them speak

const BOBIK_DELAY = [15 * 20, 40 * 20];
const KLOD_DELAY = [25 * 20, 50 * 20];
const QUIET_TICKS = 5 * 60 * 20;
const LEASH = 24; // further than this from the owner and the companion just appears next to them
const NEAR = 16; // close enough to talk and to react
const REACT_COOLDOWN = 12 * 20;

// blocks Bobik must never break, even while "digging"
const PROTECTED = /bedrock|barrier|command_block|structure_block|jigsaw|portal|end_gateway|reinforced_deepslate|spawner|vault|chest|barrel|shulker_box|_bed$|^minecraft:bed$|hopper|furnace|smoker|dispenser|dropper|crafter|beacon|respawn_anchor|light_block/;
const FLOWER = /poppy|dandelion|tulip|orchid|allium|azure_bluet|oxeye_daisy|cornflower|lily_of_the_valley|wither_rose|sunflower|lilac|rose_bush|peony|torchflower|pitcher_plant|pink_petals|red_flower|yellow_flower/;
// monsters Klod leaves alone: hitting them only makes the whole group angry
const NEUTRAL = /enderman|zombie_pigman|zombified_piglin|^minecraft:piglin$|spider|wither$|ender_dragon|warden|elder_guardian/;
const SHINY = /^minecraft:(diamond|emerald|gold_ingot|netherite_ingot|amethyst_shard|raw_gold|gold_nugget)$/;

const rand = (n) => Math.floor(Math.random() * n);
const chance = (p) => Math.random() < p;
/** @param {number[]} range */
const between = (range) => range[0] + rand(range[1] - range[0]);
const floorLoc = (l) => ({ x: Math.floor(l.x), y: Math.floor(l.y), z: Math.floor(l.z) });
/** @param {Vector3} a @param {Vector3} b */
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);

const matOn = () => world.getDynamicProperty(MAT) !== false;
const lang = () => (world.getDynamicProperty(LANG) === "ru" ? "ru" : "uk");
/** @param {Entity} who */
const ui = (who) => VOICE[VOICE_OF[who.typeId]][lang()].ui;

/** @param {Entity} entity */
function container(entity) {
  return entity.getComponent("minecraft:inventory")?.container;
}

/** @param {Entity} entity @param {string} id @param {number} [pitch] */
function sound(entity, id, pitch = 1) {
  try {
    entity.dimension.playSound(id, entity.location, { pitch });
  } catch {}
}

/** @param {Entity} entity @param {string} id */
function particle(entity, id, dy = 2.2) {
  try {
    const l = entity.location;
    entity.dimension.spawnParticle(id, { x: l.x, y: l.y + dy, z: l.z });
  } catch {}
}

/** @param {Entity} entity @param {string} prop @param {boolean} on */
function setFlag(entity, prop, on) {
  try {
    if (entity.isValid) entity.setProperty(prop, on);
  } catch {}
}

/** @param {Player} player @param {ItemStack} item */
function giveOrDrop(player, item) {
  const inv = container(player);
  const left = inv ? inv.addItem(item) : item;
  if (left) player.dimension.spawnItem(left, player.location);
}

/** Takes one item out of the given slot. @param {Player} player @param {number} slot */
function takeOne(player, slot) {
  const inv = container(player);
  const it = inv?.getItem(slot);
  if (!inv || !it) return;
  if (it.amount > 1) {
    it.amount -= 1;
    inv.setItem(slot, it);
  } else {
    inv.setItem(slot, undefined);
  }
}

// ---------------------------------------------------------------------------------------------
// Talking: one line at a time per player, played as a sound at the speaker with a subtitle on the
// player's screen, while the speaker's mouth moves (entity property vasyan:talking).

/** @type {Map<string, {busy: boolean, items: {who: Entity, key: string, then?: () => void}[]}>} */
const queues = new Map();

/** @param {Player} player */
function queueOf(player) {
  let q = queues.get(player.id);
  if (!q) queues.set(player.id, (q = { busy: false, items: [] }));
  return q;
}

/** @param {Player} player */
function busy(player) {
  const q = queues.get(player.id);
  return !!q && (q.busy || q.items.length > 0);
}

/** @param {Entity} who @param {string} key */
function hasLine(who, key) {
  const v = VOICE[VOICE_OF[who.typeId]][lang()];
  return !!(v.normal[key] || v.mat[key]);
}

/** Splits a long subtitle into lines that fit on a phone screen. @param {string} text */
function wrap(text, width = 44) {
  const out = [];
  let line = "";
  for (const word of text.split(" ")) {
    if (line && line.length + word.length + 1 > width) {
      out.push(line);
      line = word;
    } else {
      line = line ? `${line} ${word}` : word;
    }
  }
  if (line) out.push(line);
  return out.join("\n");
}

/**
 * Queues a line. Ordinary lines are dropped while someone is already talking to this player;
 * important ones (answers, a conversation between the two) wait their turn. `then` runs as the line starts.
 * @param {Player} player @param {Entity} who @param {string} key
 * @param {{important?: boolean, then?: () => void}} [opts]
 */
function say(player, who, key, opts = {}) {
  const q = queueOf(player);
  if (!opts.important && (q.busy || q.items.length)) return false;
  if (q.items.length >= 4 || !hasLine(who, key)) return false;
  q.items.push({ who, key, then: opts.then });
  pump(player);
  return true;
}

/** @param {Player} player */
function pump(player) {
  const q = queueOf(player);
  if (q.busy) return;
  const item = q.items.shift();
  if (!item) return;
  const who = item.who;
  if (!player.isValid || !who.isValid) return pump(player);
  const ch = VOICE_OF[who.typeId];
  const l = lang();
  const v = VOICE[ch][l];
  let set = matOn() ? "mat" : "normal";
  if (!v[set][item.key]) set = set === "mat" ? "normal" : "mat";
  const lines = v[set][item.key];
  const i = rand(lines.length);
  const [text, ticks] = lines[i];
  // the end of the line is scheduled first, so nothing below can leave the queue busy forever
  q.busy = true;
  const subtitle = `${COLOR[who.typeId]}${v.ui.name}:§r ${wrap(text)}`;
  const show = () => {
    try {
      if (player.isValid) player.onScreenDisplay.setActionBar(subtitle);
    } catch {}
  };
  const refresh = system.runInterval(show, 40);
  system.runTimeout(() => {
    system.clearRun(refresh);
    setFlag(who, "vasyan:talking", false);
    q.busy = false;
    system.runTimeout(() => pump(player), 8);
  }, ticks);
  sound(who, `${ch}.${l}.${set}.${item.key}.${i + 1}`);
  setFlag(who, "vasyan:talking", true);
  try {
    item.then?.();
  } catch {}
  show();
}

// ---------------------------------------------------------------------------------------------
// Owners and companions

/** Every companion in the world, grouped by owner id. @returns {Map<string, Entity[]>} */
function companionsByOwner() {
  const map = new Map();
  for (const id of DIMENSIONS) {
    const dim = world.getDimension(id);
    for (const e of [...dim.getEntities({ type: BOBIK }), ...dim.getEntities({ type: KLOD })]) {
      const owner = e.getDynamicProperty(OWNER);
      if (typeof owner !== "string") continue;
      if (!map.has(owner)) map.set(owner, []);
      map.get(owner).push(e);
    }
  }
  return map;
}

/** The player's Bobik and Klod that are close enough to take part. @param {Player} player @param {Entity[]} [list] */
function near(player, list = companionsByOwner().get(player.id) ?? []) {
  /** @type {{bobik?: Entity, klod?: Entity}} */
  const out = {};
  for (const e of list) {
    if (e.dimension.id !== player.dimension.id || dist(e.location, player.location) > NEAR) continue;
    if (e.typeId === BOBIK && !out.bobik) out.bobik = e;
    if (e.typeId === KLOD && !out.klod) out.klod = e;
  }
  return out;
}

/** @param {Entity} who */
const quiet = (who) => (/** @type {number} */ (who.getDynamicProperty(QUIET)) ?? 0) > system.currentTick;

/** Makes `player` the owner: vanilla taming (so follow_owner works) plus our own owner record. @param {Entity} e @param {Player} player */
function adopt(e, player) {
  try {
    e.getComponent("minecraft:tameable")?.tame(player);
  } catch {}
  e.triggerEvent("vasyan:on_tame");
  e.setDynamicProperty(OWNER, player.id);
  player.setDynamicProperty(CURRENT + VOICE_OF[e.typeId], e.id);
  e.setDynamicProperty(NEXT, system.currentTick + between(e.typeId === BOBIK ? BOBIK_DELAY : KLOD_DELAY));
  e.nameTag = ui(e).name;
}

/** @param {Player} player @param {Entity} e */
function setStay(player, e, stay) {
  e.setDynamicProperty(STAY, stay);
  e.triggerEvent(stay ? "vasyan:stay" : "vasyan:follow");
  say(player, e, stay ? "sit" : "follow", { important: true });
}

// ---------------------------------------------------------------------------------------------
// Bobik's "help". Each returns the line key and, when it can be fixed, how Klod undoes it.

/** @param {Block | undefined} block */
function canBreak(block) {
  if (!block || block.isAir || block.isLiquid) return false;
  if (PROTECTED.test(block.typeId)) return false;
  return !block.getComponent("minecraft:inventory");
}

/** @typedef {{key: string, undo?: () => void} | false} Prank */

/** Digs the block under the player's feet (only with solid ground one block lower). @param {Player} player @returns {Prank} */
function dig(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const below = dim.getBlock({ x: p.x, y: p.y - 1, z: p.z });
  const floor = dim.getBlock({ x: p.x, y: p.y - 2, z: p.z });
  if (!canBreak(below) || !floor || floor.isAir || floor.isLiquid) return false;
  dim.runCommand(`setblock ${p.x} ${p.y - 1} ${p.z} air destroy`);
  return {
    key: "dig",
    undo: () => {
      const b = dim.getBlock({ x: p.x, y: p.y - 1, z: p.z });
      if (b?.isAir) b.setType("minecraft:dirt");
    },
  };
}

/** Builds a "house": dirt on the four sides of the player and a roof, wherever there is air. @param {Player} player @returns {Prank} */
function house(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const spots = [{ x: p.x, y: p.y + 2, z: p.z }];
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    spots.push({ x: p.x + dx, y: p.y, z: p.z + dz }, { x: p.x + dx, y: p.y + 1, z: p.z + dz });
  }
  const placed = [];
  for (const s of spots) {
    const b = dim.getBlock(s);
    if (b?.isAir) {
      b.setType("minecraft:dirt");
      placed.push(s);
    }
  }
  if (placed.length < 3) return false;
  return {
    key: "house",
    undo: () => {
      for (const s of placed) {
        const b = dim.getBlock(s);
        if (b?.typeId === "minecraft:dirt") b.setType("minecraft:air");
      }
    },
  };
}

/** Gives everything in Bobik's pockets back to the player. @param {Player} player @param {Entity} bobik */
function returnStolen(player, bobik) {
  const bag = container(bobik);
  let n = 0;
  for (let i = 0; bag && i < bag.size; i++) {
    const it = bag.getItem(i);
    if (!it) continue;
    giveOrDrop(player, it);
    bag.setItem(i, undefined);
    n++;
  }
  return n;
}

/** "I'll carry it for you": takes the held item (or a random hotbar item). @param {Player} player @param {Entity} bobik @returns {Prank} */
function steal(player, bobik) {
  const inv = container(player);
  const bag = container(bobik);
  if (!inv || !bag || bag.emptySlotsCount === 0) return false;
  let slot = player.selectedSlotIndex;
  if (!inv.getItem(slot)) {
    const full = [];
    for (let i = 0; i < 9; i++) if (inv.getItem(i)) full.push(i);
    if (!full.length) return false;
    slot = full[rand(full.length)];
  }
  const item = inv.getItem(slot);
  if (!item || CHIPS[item.typeId]) return false;
  inv.setItem(slot, undefined);
  const left = bag.addItem(item);
  if (left) inv.setItem(slot, left);
  return { key: "steal", undo: () => returnStolen(player, bobik) };
}

/** "Sorted your inventory": swaps two random slots. @param {Player} player @returns {Prank} */
function sort(player) {
  const inv = container(player);
  if (!inv) return false;
  for (let tries = 0; tries < 12; tries++) {
    const a = rand(inv.size);
    const b = rand(inv.size);
    const ia = inv.getItem(a);
    const ib = inv.getItem(b);
    if (a === b || (!ia && !ib) || (ia && ib && ia.typeId === ib.typeId)) continue;
    inv.swapItems(a, b, inv);
    return { key: "sort", undo: () => inv.isValid && inv.swapItems(a, b, inv) };
  }
  return false;
}

/** Eats one piece of the player's food. @param {Player} player @param {Entity} bobik @returns {Prank} */
function eat(player, bobik) {
  const inv = container(player);
  if (!inv) return false;
  const food = [];
  for (let i = 0; i < inv.size; i++) {
    if (inv.getItem(i)?.getComponent("minecraft:food")) food.push(i);
  }
  if (!food.length) return false;
  takeOne(player, food[rand(food.length)]);
  sound(bobik, "random.burp");
  return { key: "eat" };
}

/** "Vitamins": a little regeneration with a lot of side effects. @param {Player} player @returns {Prank} */
function heal(player) {
  player.addEffect("nausea", 200, { amplifier: 0 });
  player.addEffect("slowness", 100, { amplifier: 1 });
  player.addEffect("regeneration", 60, { amplifier: 0 });
  return {
    key: "heal",
    undo: () => {
      player.removeEffect("nausea");
      player.removeEffect("slowness");
    },
  };
}

/** "A shortcut": throws the player 8-15 blocks away onto the surface (overworld only). @param {Player} player @returns {Prank} */
function shortcut(player) {
  const dim = player.dimension;
  if (dim.id !== "minecraft:overworld") return false;
  const a = Math.random() * Math.PI * 2;
  const r = 8 + rand(8);
  const x = Math.floor(player.location.x + Math.cos(a) * r);
  const z = Math.floor(player.location.z + Math.sin(a) * r);
  const top = dim.getTopmostBlock({ x, z });
  if (!top || top.isLiquid || /lava|fire|magma|cactus|powder_snow/.test(top.typeId)) return false;
  if (Math.abs(top.location.y - player.location.y) > 12) return false;
  const from = { ...player.location };
  player.teleport({ x: x + 0.5, y: top.location.y + 1, z: z + 0.5 });
  sound(player, "mob.endermen.portal");
  return { key: "shortcut", undo: () => player.isValid && player.teleport(from, { dimension: dim }) };
}

/** Knocks the player away from the entity. @param {Player} player @param {Entity} from */
function knock(player, from, strength = 1.6) {
  let dx = player.location.x - from.location.x;
  let dz = player.location.z - from.location.z;
  const len = Math.hypot(dx, dz);
  if (len < 0.1) {
    const a = Math.random() * Math.PI * 2;
    dx = Math.cos(a);
    dz = Math.sin(a);
  } else {
    dx /= len;
    dz /= len;
  }
  player.applyKnockback({ x: dx * strength, z: dz * strength }, 0.5);
}

/** "Was pushing you out of danger". @param {Player} player @param {Entity} bobik @returns {Prank} */
function push(player, bobik) {
  knock(player, bobik);
  return { key: "push" };
}

/** "Anti-theft trap": a cobweb at the player's feet that disappears after 8 seconds. @param {Player} player @returns {Prank} */
function web(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const b = dim.getBlock(p);
  if (!b?.isAir) return false;
  b.setType("minecraft:web");
  const clear = () => {
    try {
      const w = dim.getBlock(p);
      if (w?.typeId === "minecraft:web") w.setType("minecraft:air");
    } catch {}
  };
  system.runTimeout(clear, 160);
  return { key: "web", undo: clear };
}

/** "Brought a friend": usually a harmless animal, sometimes a zombie. @param {Player} player @param {Entity} bobik @returns {Prank} */
function friend(player, bobik) {
  if (chance(0.15)) {
    const z = bobik.dimension.spawnEntity("minecraft:zombie", bobik.location);
    return { key: "friend_zombie", undo: () => z.isValid && z.kill() };
  }
  bobik.dimension.spawnEntity(["minecraft:chicken", "minecraft:rabbit", "minecraft:pig"][rand(3)], bobik.location);
  return { key: "friend" };
}

const PRANKS = [
  { weight: 3, run: dig },
  { weight: 2, run: house },
  { weight: 3, run: steal },
  { weight: 3, run: sort },
  { weight: 2, run: eat },
  { weight: 2, run: heal },
  { weight: 2, run: shortcut },
  { weight: 3, run: push },
  { weight: 2, run: web },
  { weight: 1, run: friend },
  { weight: 3, run: () => ({ key: "idle" }) },
];
const PRANK_TOTAL = PRANKS.reduce((s, h) => s + h.weight, 0);

/** @returns {(player: Player, bobik: Entity) => Prank} */
function pickPrank() {
  let r = rand(PRANK_TOTAL);
  for (const h of PRANKS) {
    if ((r -= h.weight) < 0) return h.run;
  }
  return PRANKS[0].run;
}

/**
 * Bobik "helps", then, if Klod is around, Klod comments, sometimes fixes it, and Bobik answers back.
 * @param {Player} player @param {Entity} bobik @param {Entity} [klod]
 */
function bobikHelp(player, bobik, klod) {
  for (let tries = 0; tries < 5; tries++) {
    /** @type {Prank} */
    let prank = false;
    try {
      prank = pickPrank()(player, bobik);
    } catch {
      // unloaded chunk, world border etc. - just try something else
    }
    if (!prank) continue;
    say(player, bobik, prank.key, { important: true });
    if (klod?.isValid && prank.key !== "idle" && chance(0.55)) {
      const fix = prank.undo && chance(0.6);
      say(player, klod, fix ? "undo" : "roast", { important: true, then: fix ? prank.undo : undefined });
      if (chance(0.45)) say(player, bobik, "reply_klod", { important: true });
    }
    return;
  }
}

// ---------------------------------------------------------------------------------------------
// Klod's actual help

/** @param {Player} player @param {Entity} klod */
function nearestMonster(player, klod, range = 8) {
  const mobs = player.dimension.getEntities({ families: ["monster"], location: player.location, maxDistance: range });
  return mobs.find((m) => m.isValid && m.typeId !== BOBIK && m.typeId !== KLOD && !NEUTRAL.test(m.typeId));
}

const lastGuardLine = new Map();
/** Hits the nearest monster hard. Talks about it only now and then. @param {Player} player @param {Entity} klod */
function guard(player, klod) {
  const mob = nearestMonster(player, klod);
  if (!mob) return false;
  particle(mob, "minecraft:critical_hit_emitter", 1);
  sound(mob, "random.orb", 0.6);
  mob.applyDamage(20, { cause: EntityDamageCause.entityAttack, damagingEntity: klod });
  const now = system.currentTick;
  if (now - (lastGuardLine.get(player.id) ?? -10000) > 30 * 20 && !quiet(klod) && say(player, klod, "guard")) {
    lastGuardLine.set(player.id, now);
  }
  return true;
}

/** @param {Player} player */
function isDark(player) {
  if (player.dimension.id !== "minecraft:overworld") return true;
  const t = world.getTimeOfDay();
  return (t > 13000 && t < 23000) || player.location.y < 50;
}

/** Places a torch where the player stands. @param {Player} player */
function torch(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  if (p.y <= dim.heightRange.min || p.y >= dim.heightRange.max) return false;
  const here = dim.getBlock(p);
  const below = dim.getBlock({ x: p.x, y: p.y - 1, z: p.z });
  if (!here?.isAir || !below || below.isAir || below.isLiquid || /leaves|glass|fence|wall|slab|stairs|web|torch/.test(below.typeId)) return false;
  here.setType("minecraft:torch");
  return true;
}

/** @param {Player} player */
function hurtBy(player) {
  const h = player.getComponent("minecraft:health");
  return h ? h.effectiveMax - h.currentValue : 0;
}

/**
 * Picks one useful thing to do right now. Returns the line key, or false when nothing is needed.
 * @param {Player} player @param {Entity} klod @param {Entity} [bobik] @param {boolean} [tips]
 */
function klodUseful(player, klod, bobik, tips = true) {
  if (bobik && container(bobik)?.emptySlotsCount !== container(bobik)?.size) {
    returnStolen(player, bobik);
    return "fix_vasyan";
  }
  if (hurtBy(player) >= 6) {
    player.addEffect("regeneration", 100, { amplifier: 1 });
    particle(player, "minecraft:heart_particle");
    return "heal";
  }
  if (isDark(player) && chance(0.6) && torch(player)) return "torch";
  if (chance(0.25)) {
    player.addEffect("saturation", 2, { amplifier: 0 });
    return "feed";
  }
  return tips ? "tip" : false;
}

// ---------------------------------------------------------------------------------------------
// Reactions to what happens to the owner

const lastReact = new Map();
/** One of the companions near the player says something about it (not too often). @param {Player} player @param {string} key */
function react(player, key, p = 1, force = false) {
  const now = system.currentTick;
  if (!force && now - (lastReact.get(player.id) ?? -10000) < REACT_COOLDOWN) return;
  if (!chance(p) || busy(player)) return;
  const { bobik, klod } = near(player);
  const who = [bobik, klod].filter((e) => e && !quiet(e) && hasLine(e, key));
  if (!who.length) return;
  if (say(player, who[rand(who.length)], key)) lastReact.set(player.id, now);
}

// per player: last position and since when, low health and creeper cooldowns
const afk = new Map();
const lowHp = new Map();
const creeperSeen = new Map();
let lastTime = -1;

/** @param {Player} player */
function watch(player, now) {
  const pos = afk.get(player.id);
  const rot = player.getRotation();
  if (!pos || dist(pos.at, player.location) > 0.5 || Math.abs(rot.x - pos.rot.x) + Math.abs(rot.y - pos.rot.y) > 10) {
    afk.set(player.id, { at: { ...player.location }, rot, since: now, said: false });
  } else if (!pos.said && now - pos.since > 60 * 20) {
    pos.said = true;
    react(player, "afk", 1, true);
  }
  const h = player.getComponent("minecraft:health");
  if (h && h.currentValue > 0 && h.currentValue <= 6 && now - (lowHp.get(player.id) ?? -10000) > 60 * 20) {
    lowHp.set(player.id, now);
    react(player, "low_hp", 1, true);
  }
  if (now - (creeperSeen.get(player.id) ?? -10000) > 60 * 20) {
    const c = player.dimension.getEntities({ type: "minecraft:creeper", location: player.location, maxDistance: 8 });
    if (c.length) {
      creeperSeen.set(player.id, now);
      react(player, "creeper", 1, true);
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Main loop: once a second

/** @param {Player} player */
function safeSpot(player) {
  if (!player.isOnGround) return false;
  try {
    return !/lava|fire/.test(player.dimension.getBlock(floorLoc(player.location))?.typeId ?? "");
  } catch {
    return false;
  }
}

/** Removes a companion for good, dropping whatever is in his pockets. @param {Entity} e */
function retire(e) {
  const bag = container(e);
  for (let i = 0; bag && i < bag.size; i++) {
    const it = bag.getItem(i);
    if (it) e.dimension.spawnItem(it, e.location);
  }
  bag?.clearAll();
  e.remove();
}

const lastGuard = new Map();
system.runInterval(() => {
  const now = system.currentTick;
  const enabled = world.getDynamicProperty(ENABLED) !== false;
  const owned = companionsByOwner();
  const time = world.getTimeOfDay();
  const nightfall = lastTime >= 0 && lastTime < 12500 && time >= 12500;
  lastTime = time;
  for (const player of world.getPlayers()) {
    try {
      tickPlayer(player, owned.get(player.id) ?? [], now, enabled, nightfall);
    } catch (err) {
      console.warn(`[Bobik & Klod] ${err}`);
    }
  }
}, 20);

/** @param {Player} player @param {Entity[]} all @param {number} now @param {boolean} enabled @param {boolean} nightfall */
function tickPlayer(player, all, now, enabled, nightfall) {
  const hp = player.getComponent("minecraft:health");
  if (!hp || hp.currentValue <= 0 || player.getGameMode() === GameMode.Spectator) return;
  const list = [];
  for (const e of all) {
    // one Bobik and one Klod per player: an older one that turns up again (unloaded chunk) is retired
    const cur = player.getDynamicProperty(CURRENT + VOICE_OF[e.typeId]);
    if (typeof cur !== "string") player.setDynamicProperty(CURRENT + VOICE_OF[e.typeId], e.id);
    else if (cur !== e.id) {
      retire(e);
      continue;
    }
    list.push(e);
    if ((!e.nameTag || DEFAULT_NAMES.has(e.nameTag)) && e.nameTag !== ui(e).name) e.nameTag = ui(e).name;
    if (!busy(player) && e.getProperty("vasyan:talking") === true) setFlag(e, "vasyan:talking", false);
    if (!dancing.has(e.id) && e.getProperty("vasyan:dancing") === true) setFlag(e, "vasyan:dancing", false);
    if (e.getDynamicProperty(STAY) === true) continue;
    // keep up with the owner, but only to a safe spot (not mid-fall over the void, not into lava)
    if ((e.dimension.id !== player.dimension.id || dist(e.location, player.location) > LEASH) && safeSpot(player)) {
      e.teleport(player.location, { dimension: player.dimension });
    }
  }
  if (!list.length) return;
  const { bobik, klod } = near(player, list);
  watch(player, now);
  if (nightfall && player.dimension.id === "minecraft:overworld") react(player, "night", 1, true);

  if (klod && now - (lastGuard.get(player.id) ?? -1000) >= 80 && guard(player, klod)) lastGuard.set(player.id, now);

  if (bobik && enabled) {
    const next = bobik.getDynamicProperty(NEXT);
    if (typeof next !== "number" || next > now + BOBIK_DELAY[1]) {
      bobik.setDynamicProperty(NEXT, now + between(BOBIK_DELAY));
    } else if (now >= next && !busy(player)) {
      bobik.setDynamicProperty(NEXT, now + between(BOBIK_DELAY));
      if (!quiet(bobik)) {
        bobikHelp(player, bobik, klod);
      } else if (chance(0.1)) {
        say(player, bobik, "quiet_broke", { important: true });
        bobikHelp(player, bobik, klod);
      }
    }
  }

  if (klod) {
    const next = klod.getDynamicProperty(NEXT);
    if (typeof next !== "number" || next > now + KLOD_DELAY[1]) {
      klod.setDynamicProperty(NEXT, now + between(KLOD_DELAY));
    } else if (now >= next && !busy(player)) {
      klod.setDynamicProperty(NEXT, now + between(KLOD_DELAY));
      const key = klodUseful(player, klod, bobik, !quiet(klod));
      if (key && !quiet(klod)) {
        say(player, klod, key);
        if (key === "fix_vasyan" && bobik && chance(0.5)) say(player, bobik, "reply_klod", { important: true });
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Events

const hits = new Map();
const lastMobHeal = new Map();
world.afterEvents.entityHurt.subscribe((ev) => {
  const target = ev.hurtEntity;
  const attacker = ev.damageSource.damagingEntity;
  if (target instanceof Player) {
    if (ev.damage > 0 && attacker?.typeId !== BOBIK && attacker?.typeId !== KLOD) react(target, "hurt", 0.35);
    return;
  }
  if (!(attacker instanceof Player)) return;
  // the owner hits his own companion: "ouch", and after three hits in a row he gets angry
  if ((target.typeId === BOBIK || target.typeId === KLOD) && target.getDynamicProperty(OWNER) === attacker.id) {
    const now = system.currentTick;
    const list = (hits.get(target.id) ?? []).filter((t) => now - t < 200);
    list.push(now);
    hits.set(target.id, list);
    if (list.length >= 3) {
      hits.set(target.id, []);
      say(attacker, target, "angry", {
        important: true,
        then: () => target.typeId === BOBIK && attacker.isValid && knock(attacker, target, 2.2),
      });
    } else if (chance(0.5)) {
      say(attacker, target, "ouch");
    }
    return;
  }
  // hitting a mob near Bobik: sometimes he feels sorry for the mob and heals it
  if (world.getDynamicProperty(ENABLED) === false || target.typeId === BOBIK || target.typeId === KLOD) return;
  if (!target.isValid || !((target.getComponent("minecraft:health")?.currentValue ?? 0) > 0)) return;
  const now = system.currentTick;
  if (now - (lastMobHeal.get(attacker.id) ?? -1000) < 300 || !chance(0.25)) return;
  const { bobik } = near(attacker);
  if (!bobik || quiet(bobik) || busy(attacker)) return;
  lastMobHeal.set(attacker.id, now);
  target.addEffect("regeneration", 100, { amplifier: 1 });
  particle(target, "minecraft:heart_particle", 1.5);
  say(attacker, bobik, "mobheal");
});

world.afterEvents.entityDie.subscribe((ev) => {
  const killer = ev.damageSource.damagingEntity;
  const dead = ev.deadEntity;
  if (!(killer instanceof Player) || dead instanceof Player) return;
  if (dead.typeId === BOBIK || dead.typeId === KLOD) return;
  react(killer, "kill", 0.3);
});

world.afterEvents.playerSpawn.subscribe((ev) => {
  if (ev.initialSpawn) return;
  const player = ev.player;
  system.runTimeout(() => player.isValid && react(player, "respawn", 1, true), 60);
});

const lastDiamonds = new Map();
world.afterEvents.playerBreakBlock.subscribe((ev) => {
  if (!ev.brokenBlockPermutation.type.id.includes("diamond_ore")) return;
  const now = system.currentTick;
  if (now - (lastDiamonds.get(ev.player.id) ?? -10000) < 60 * 20) return;
  lastDiamonds.set(ev.player.id, now);
  react(ev.player, "diamond_ore", 1, true);
});

world.afterEvents.playerDimensionChange.subscribe((ev) => {
  const player = ev.player;
  try {
    for (const e of ev.fromDimension.getEntities({ location: ev.fromLocation, maxDistance: LEASH + 8 })) {
      if ((e.typeId !== BOBIK && e.typeId !== KLOD) || e.getDynamicProperty(OWNER) !== player.id) continue;
      if (e.getDynamicProperty(STAY) !== true) e.teleport(ev.toLocation, { dimension: ev.toDimension });
    }
  } catch {}
  system.runTimeout(() => player.isValid && react(player, "dimension", 1, true), 80);
});

world.afterEvents.weatherChange.subscribe((ev) => {
  if (ev.previousWeather !== "Clear" || ev.newWeather === "Clear" || !ev.dimension.endsWith("overworld")) return;
  for (const player of world.getPlayers()) {
    if (player.dimension.id === "minecraft:overworld") react(player, "rain", 1, true);
  }
});

// ---------------------------------------------------------------------------------------------
// Menus

/**
 * @param {Player} player @param {Entity} who @param {string} title @param {string} body
 * @param {[string, () => void][]} buttons
 */
async function menu(player, who, title, body, buttons) {
  const form = new ActionFormData().title(title).body(body);
  for (const [label] of buttons) form.button(label);
  const res = await form.show(player);
  if (res.canceled || res.selection === undefined || !who.isValid || !player.isValid) return;
  buttons[res.selection][1]();
}

/** @param {Player} player @param {Entity} who */
function toggleMat(player, who) {
  world.setDynamicProperty(MAT, !matOn());
  say(player, who, matOn() ? "mat_on" : "mat_off", { important: true });
}

/** @param {Player} player @param {Entity} who */
function toggleLang(player, who) {
  world.setDynamicProperty(LANG, lang() === "uk" ? "ru" : "uk");
  say(player, who, "lang", { important: true });
}

const dancing = new Set();
/** @param {Player} player @param {Entity} who */
function dance(player, who) {
  if (dancing.has(who.id)) return;
  say(player, who, "dance", { important: true });
  dancing.add(who.id);
  setFlag(who, "vasyan:dancing", true);
  let n = 0;
  const id = who.id;
  const beat = system.runInterval(() => {
    if (!who.isValid || ++n > 12) {
      system.clearRun(beat);
      dancing.delete(id);
      setFlag(who, "vasyan:dancing", false);
      return;
    }
    particle(who, "minecraft:note_particle");
    sound(who, n % 4 === 0 ? "note.bass" : "note.pling", [0.8, 1, 1.2, 1.5][n % 4]);
  }, 8);
}

/** @param {Player} player @param {Entity} who */
function dismiss(player, who) {
  say(player, who, "bye", {
    important: true,
    then: () => {
      if (who.typeId === BOBIK) {
        const bag = container(who);
        for (let i = 0; bag && i < bag.size; i++) {
          const it = bag.getItem(i);
          if (it) who.dimension.spawnItem(it, who.location);
        }
        bag?.clearAll();
      }
      giveOrDrop(player, new ItemStack(CHIP_OF[who.typeId]));
      particle(who, "minecraft:large_explosion", 1);
      who.remove();
    },
  });
}

/** A real-looking diamond. @param {Entity} bobik */
function fakeDiamond(bobik) {
  const it = new ItemStack("minecraft:dirt", 1);
  it.nameTag = ui(bobik).fake_diamond;
  return it;
}

/** Bobik takes what is in the player's hand and reacts to it. @param {Player} player @param {Entity} bobik */
function gift(player, bobik) {
  const slot = player.selectedSlotIndex;
  const item = container(player)?.getItem(slot);
  if (!item) return say(player, bobik, "gift_none", { important: true });
  const id = item.typeId;
  if (id === "minecraft:redstone") {
    takeOne(player, slot);
    const h = bobik.getComponent("minecraft:health");
    h?.setCurrentValue(h.effectiveMax);
    particle(bobik, "minecraft:villager_happy");
    return say(player, bobik, "gift_redstone", { important: true });
  }
  if (item.getComponent("minecraft:food")) {
    takeOne(player, slot);
    sound(bobik, "random.burp");
    return say(player, bobik, "gift_food", { important: true });
  }
  if (SHINY.test(id)) {
    takeOne(player, slot);
    return say(player, bobik, "gift_shiny", { important: true, then: () => giveOrDrop(player, fakeDiamond(bobik)) });
  }
  if (FLOWER.test(id)) {
    takeOne(player, slot);
    particle(bobik, "minecraft:heart_particle");
    return say(player, bobik, "gift_flower", { important: true });
  }
  return say(player, bobik, "gift_junk", { important: true });
}

/** @param {Player} player @param {Entity} bobik */
function bobikMenu(player, bobik) {
  const t = ui(bobik);
  const klod = near(player).klod;
  const stay = bobik.getDynamicProperty(STAY) === true;
  return menu(player, bobik, t.title, matOn() ? t.body_mat : t.body, [
    [t.talk, () => bobikTalk(player, bobik)],
    [
      t.give_back,
      () => {
        const bag = container(bobik);
        const items = [];
        for (let i = 0; bag && i < bag.size; i++) if (bag.getItem(i)) items.push(i);
        if (!items.length) return say(player, bobik, "give_back_empty", { important: true });
        // sometimes he "forgets" one thing for later
        const keep = items.length > 1 && chance(0.2) ? items[rand(items.length)] : -1;
        for (const i of items) {
          if (i === keep) continue;
          giveOrDrop(player, bag.getItem(i));
          bag.setItem(i, undefined);
        }
        say(player, bobik, keep >= 0 ? "give_back_forgot" : "give_back", { important: true });
      },
    ],
    [
      t.help,
      () => {
        bobik.setDynamicProperty(NEXT, system.currentTick + between(BOBIK_DELAY));
        say(player, bobik, "help", { important: true, then: () => system.runTimeout(() => bobik.isValid && bobikHelp(player, bobik, klod), 30) });
      },
    ],
    [t.gift, () => gift(player, bobik)],
    [t.dance, () => dance(player, bobik)],
    [stay ? t.follow : t.sit, () => setStay(player, bobik, !stay)],
    [
      t.quiet,
      () => {
        bobik.setDynamicProperty(QUIET, system.currentTick + QUIET_TICKS);
        say(player, bobik, "quiet", { important: true });
      },
    ],
    [matOn() ? t.mat_off : t.mat_on, () => toggleMat(player, bobik)],
    [t.lang, () => toggleLang(player, bobik)],
    [t.bye, () => dismiss(player, bobik)],
  ]);
}

/** @param {Player} player @param {Entity} bobik */
function bobikTalk(player, bobik) {
  const t = ui(bobik);
  const ask = (key, then) => () => say(player, bobik, key, { important: true, then });
  return menu(player, bobik, t.talk_title, matOn() ? t.talk_body_mat : t.talk_body, [
    [t.q_joke, ask("joke")],
    [t.q_advice, ask("advice")],
    [t.q_diamonds, ask("diamonds", () => giveOrDrop(player, fakeDiamond(bobik)))],
    [t.q_how, ask("how")],
    [t.q_stupid, ask("stupid", () => chance(0.4) && knock(player, bobik, 1.2))],
    [t.q_about, ask("about")],
    [t.back, () => bobikMenu(player, bobik)],
  ]);
}

/** @param {Player} player @param {Entity} klod */
function klodMenu(player, klod) {
  const t = ui(klod);
  const stay = klod.getDynamicProperty(STAY) === true;
  return menu(player, klod, t.title, matOn() ? t.body_mat : t.body, [
    [t.talk, () => klodTalk(player, klod)],
    [
      t.help,
      () => {
        const bobik = near(player).bobik;
        say(player, klod, "help", { important: true });
        if (guard(player, klod)) return;
        const key = klodUseful(player, klod, bobik, false);
        say(player, klod, key || "help_nothing", { important: true });
      },
    ],
    [t.dance, () => dance(player, klod)],
    [stay ? t.follow : t.sit, () => setStay(player, klod, !stay)],
    [
      t.quiet,
      () => {
        klod.setDynamicProperty(QUIET, system.currentTick + QUIET_TICKS);
        say(player, klod, "quiet", { important: true });
      },
    ],
    [matOn() ? t.mat_off : t.mat_on, () => toggleMat(player, klod)],
    [t.lang, () => toggleLang(player, klod)],
    [t.bye, () => dismiss(player, klod)],
  ]);
}

/** @param {Player} player @param {Entity} klod */
function klodTalk(player, klod) {
  const t = ui(klod);
  const ask = (key) => () => {
    say(player, klod, key, { important: true });
    // Bobik does not let an opinion about himself go unanswered
    const bobik = near(player).bobik;
    if (key === "vasyan" && bobik) say(player, bobik, "reply_klod", { important: true });
  };
  return menu(player, klod, t.talk_title, matOn() ? t.talk_body_mat : t.talk_body, [
    [t.q_joke, ask("joke")],
    [t.q_advice, ask("tip")],
    [t.q_how, ask("how")],
    [t.q_vasyan, ask("vasyan")],
    [t.q_smart, ask("smart")],
    [t.q_about, ask("about")],
    [t.back, () => klodMenu(player, klod)],
  ]);
}

// Talking to a companion: the first player to do it becomes the owner, the owner gets the menu.
world.afterEvents.playerInteractWithEntity.subscribe((ev) => {
  const e = ev.target;
  const player = ev.player;
  if (e.typeId !== BOBIK && e.typeId !== KLOD) return;
  const owner = e.getDynamicProperty(OWNER);
  if (typeof owner !== "string") {
    adopt(e, player);
    say(player, e, "hello", { important: true });
    return;
  }
  if (owner !== player.id) {
    say(player, e, "stranger");
    return;
  }
  if (ev.itemStack?.typeId === "minecraft:redstone") return;
  system.run(() => (e.typeId === BOBIK ? bobikMenu(player, e) : klodMenu(player, e)));
});

// The chips: summon your Bobik or Klod (or call him back if you already have one).
const lastChipUse = new Map();
/** @param {Player} player @param {string} type */
function useChip(player, type) {
  const now = system.currentTick;
  if (now - (lastChipUse.get(player.id) ?? -100) < 10) return;
  lastChipUse.set(player.id, now);
  const mine = (companionsByOwner().get(player.id) ?? []).find((e) => e.typeId === type);
  if (mine) {
    mine.setDynamicProperty(STAY, false);
    mine.triggerEvent("vasyan:follow");
    mine.teleport(player.location, { dimension: player.dimension });
    say(player, mine, "here", { important: true });
    return;
  }
  const e = player.dimension.spawnEntity(type, player.location);
  particle(e, "minecraft:huge_explosion_emitter", 1);
  system.runTimeout(() => {
    if (!e.isValid || !player.isValid) return;
    adopt(e, player);
    say(player, e, "hello", { important: true });
  }, 2);
  if (player.getGameMode() !== GameMode.Creative) takeOne(player, player.selectedSlotIndex);
}

world.afterEvents.itemUse.subscribe((ev) => {
  const type = CHIPS[ev.itemStack?.typeId];
  if (type) useChip(ev.source, type);
});
world.afterEvents.playerInteractWithBlock.subscribe((ev) => {
  const type = CHIPS[ev.itemStack?.typeId];
  if (ev.isFirstEvent && type) useChip(ev.player, type);
});

// /scriptevent vasyan:toggle - Bobik's pranks on or off for the whole world.
// /scriptevent vasyan:mat - swearing on or off.
system.afterEvents.scriptEventReceive.subscribe((ev) => {
  if (ev.id !== "vasyan:toggle" && ev.id !== "vasyan:mat") return;
  const owned = companionsByOwner();
  if (ev.id === "vasyan:mat") world.setDynamicProperty(MAT, !matOn());
  else world.setDynamicProperty(ENABLED, world.getDynamicProperty(ENABLED) === false);
  for (const player of world.getPlayers()) {
    const { bobik, klod } = near(player, owned.get(player.id) ?? []);
    const who = bobik ?? klod;
    if (ev.id === "vasyan:mat" && who) {
      say(player, who, matOn() ? "mat_on" : "mat_off", { important: true });
    } else if (ev.id === "vasyan:toggle") {
      const t = VOICE.vasyan[lang()].ui;
      player.onScreenDisplay.setActionBar(world.getDynamicProperty(ENABLED) === false ? t.pranks_off : t.pranks_on);
    }
  }
});
