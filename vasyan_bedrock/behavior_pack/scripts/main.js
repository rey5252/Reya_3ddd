// Bobik, the dumbest neural network, and Klod, his smart (and just as foul-mouthed) colleague.
// Both follow their owner, talk out loud (voice + subtitle above the hotbar, never the chat), react to what
// happens around them and argue with each other. Bobik "helps" in the worst possible way, Klod actually helps
// and cleans up after Bobik.
// Stable Script API only (@minecraft/server 2.0.0, @minecraft/server-ui 2.0.0), Bedrock 1.21.90+.
import { world, system, GameMode, Player, ItemStack, EntityDamageCause } from "@minecraft/server";
import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import { VOICE, DIALOGS } from "./voice.js";

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
// player dynamic properties (each owner chooses for his own companions; the world value is the fallback)
const MAT = "vasyan:mat"; // false: no swearing (menu or /scriptevent vasyan:mat)
const LANG = "vasyan:lang"; // "uk" or "ru": the language both of them speak

const BOBIK_DELAY = [15 * 20, 40 * 20];
const KLOD_DELAY = [25 * 20, 50 * 20];
const QUIET_TICKS = 5 * 60 * 20;
const ARGUE_DELAY = [3 * 60 * 20, 6 * 60 * 20]; // how often the two start an argument on their own
const LEASH = 24; // further than this from the owner and the companion just appears next to them
const NEAR = 16; // close enough to talk and to react
const REACT_COOLDOWN = 12 * 20;

// blocks Bobik must never break, even while "digging"
const PROTECTED = /bedrock|barrier|command_block|structure_block|jigsaw|portal|end_gateway|reinforced_deepslate|spawner|vault|chest|barrel|shulker_box|_bed$|^minecraft:bed$|hopper|furnace|smoker|dispenser|dropper|crafter|beacon|respawn_anchor|light_block/;
const FLOWER = /poppy|dandelion|tulip|orchid|allium|azure_bluet|oxeye_daisy|cornflower|lily_of_the_valley|wither_rose|sunflower|lilac|rose_bush|peony|torchflower|pitcher_plant|pink_petals|red_flower|yellow_flower/;
// monsters Klod leaves alone: hitting them only makes the whole group angry
const NEUTRAL = /enderman|zombie_pigman|zombified_piglin|^minecraft:piglin$|^minecraft:spider$|zombie_villager|wither$|ender_dragon|warden|elder_guardian/;
const SHINY = /^minecraft:(diamond|emerald|gold_ingot|netherite_ingot|amethyst_shard|raw_gold|gold_nugget)$/;

const rand = (n) => Math.floor(Math.random() * n);
const chance = (p) => Math.random() < p;
/** @param {number[]} range */
const between = (range) => range[0] + rand(range[1] - range[0]);
const floorLoc = (l) => ({ x: Math.floor(l.x), y: Math.floor(l.y), z: Math.floor(l.z) });
/** @param {Vector3} a @param {Vector3} b */
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);

/** @param {Player} player @param {string} id */
const setting = (player, id) => player.getDynamicProperty(id) ?? world.getDynamicProperty(id);
/** @param {Player} player */
const matOn = (player) => setting(player, MAT) !== false;
/** @param {Player} player @returns {"uk" | "ru"} */
const lang = (player) => (setting(player, LANG) === "ru" ? "ru" : "uk");
/** Menu texts of `who` in the language of `player`. @param {Entity} who @param {Player} player */
const ui = (who, player) => VOICE[VOICE_OF[who.typeId]][lang(player)].ui;

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

/** @typedef {{who: Entity, key?: string, line?: {sid: string, text: string, ticks: number}, then?: () => void}} QueueItem */
/** @type {Map<string, {busy: boolean, items: QueueItem[]}>} */
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

/** @param {Entity} who @param {string} key @param {Player} player */
function hasLine(who, key, player) {
  const v = VOICE[VOICE_OF[who.typeId]][lang(player)];
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
  if (q.items.length >= 8 || !hasLine(who, key, player)) return false;
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
  const l = lang(player);
  const v = VOICE[ch][l];
  let text, ticks, sid;
  if (item.line) {
    ({ text, ticks, sid } = item.line);
  } else {
    let set = matOn(player) ? "mat" : "normal";
    if (!v[set][item.key]) set = set === "mat" ? "normal" : "mat";
    const lines = v[set][item.key];
    const i = rand(lines.length);
    [text, ticks] = lines[i];
    sid = `${ch}.${l}.${set}.${item.key}.${i + 1}`;
  }
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
  sound(who, sid);
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
const quiet = (who) => {
  const until = /** @type {number} */ (who.getDynamicProperty(QUIET)) ?? 0;
  return until > system.currentTick && until - system.currentTick <= QUIET_TICKS;
};

/** Makes `player` the owner: vanilla taming (so follow_owner works) plus our own owner record. @param {Entity} e @param {Player} player */
function adopt(e, player) {
  try {
    e.getComponent("minecraft:tameable")?.tame(player);
  } catch {}
  e.triggerEvent("vasyan:on_tame");
  e.setDynamicProperty(OWNER, player.id);
  player.setDynamicProperty(CURRENT + VOICE_OF[e.typeId], e.id);
  e.setDynamicProperty(NEXT, system.currentTick + between(e.typeId === BOBIK ? BOBIK_DELAY : KLOD_DELAY));
  e.nameTag = ui(e, player).name;
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

// natural terrain Bobik may dig out (nothing built, nothing valuable to break)
const DIGGABLE = /^minecraft:(dirt|grass_block|coarse_dirt|rooted_dirt|podzol|mycelium|stone|cobblestone|deepslate|cobbled_deepslate|tuff|andesite|diorite|granite|calcite|sand|red_sand|sandstone|gravel|clay|mud|netherrack|soul_sand|soul_soil|blackstone|basalt|end_stone|snow|snow_block|moss_block|dripstone_block)$/;

const lastHurt = new Map();
/** Fighting, low on health or a monster close by: no pranks that could get the player killed. @param {Player} player */
function inDanger(player) {
  const h = player.getComponent("minecraft:health");
  if (!h || h.currentValue <= 8) return true;
  if (system.currentTick - (lastHurt.get(player.id) ?? -10000) < 10 * 20) return true;
  return player.dimension.getEntities({ families: ["monster"], location: player.location, maxDistance: 8 }).length > 0;
}

/** Pranks that change the world are off in Adventure mode (no way to undo them) and in mid-air. @param {Player} player */
const canBuild = (player) => player.getGameMode() !== GameMode.Adventure && player.isOnGround;

/** Digs the block under the player's feet (only with solid ground one block lower). @param {Player} player @returns {Prank} */
function dig(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  if (!canBuild(player) || p.y - 2 <= dim.heightRange.min) return false;
  const below = dim.getBlock({ x: p.x, y: p.y - 1, z: p.z });
  const floor = dim.getBlock({ x: p.x, y: p.y - 2, z: p.z });
  if (!canBreak(below) || !DIGGABLE.test(below.typeId) || !floor || floor.isAir || floor.isLiquid) return false;
  const was = below.permutation;
  dim.runCommand(`setblock ${p.x} ${p.y - 1} ${p.z} air destroy`);
  return {
    key: "dig",
    undo: () => {
      const b = dim.getBlock({ x: p.x, y: p.y - 1, z: p.z });
      if (b?.isAir) b.setPermutation(was);
    },
  };
}

/** Builds a "house": dirt on the four sides of the player and a roof, wherever there is air. @param {Player} player @returns {Prank} */
function house(player) {
  if (!canBuild(player)) return false;
  const p = floorLoc(player.location);
  const dim = player.dimension;
  if (p.y + 2 >= dim.heightRange.max) return false;
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
    const it = inv.getItem(i);
    if (it?.getComponent("minecraft:food") && !/golden_apple|golden_carrot/.test(it.typeId)) food.push(i);
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

/** "Was pushing you out of danger". Only where there is solid ground to land on. @param {Player} player @param {Entity} bobik @returns {Prank} */
function push(player, bobik) {
  const dim = player.dimension;
  if (dim.id !== "minecraft:overworld" || !player.isOnGround) return false;
  const dx = player.location.x - bobik.location.x;
  const dz = player.location.z - bobik.location.z;
  const len = Math.hypot(dx, dz) || 1;
  const p = floorLoc(player.location);
  for (let step = 1; step <= 4; step++) {
    const x = Math.floor(player.location.x + (dx / len) * step);
    const z = Math.floor(player.location.z + (dz / len) * step);
    let ground = false;
    for (let y = p.y - 1; y >= p.y - 3; y--) {
      const b = dim.getBlock({ x, y, z });
      if (!b || /lava|fire|magma|cactus|powder_snow|sweet_berry/.test(b.typeId)) return false;
      if (!b.isAir && !b.isLiquid) {
        ground = true;
        break;
      }
    }
    if (!ground) return false;
  }
  knock(player, bobik);
  return { key: "push" };
}

/** "Anti-theft trap": a cobweb at the player's feet that disappears after 8 seconds. @param {Player} player @returns {Prank} */
function web(player) {
  if (!canBuild(player)) return false;
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

/** "Made the world prettier": a flower or a sapling on the grass next to the player. @param {Player} player @returns {Prank} */
function garden(player) {
  if (!canBuild(player)) return false;
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const plants = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:oak_sapling", "minecraft:birch_sapling"];
  for (let tries = 0; tries < 8; tries++) {
    const x = p.x + rand(7) - 3;
    const z = p.z + rand(7) - 3;
    for (let y = p.y + 1; y >= p.y - 2; y--) {
      const b = dim.getBlock({ x, y, z });
      const below = dim.getBlock({ x, y: y - 1, z });
      if (b?.isAir && below && /grass_block|^minecraft:dirt$|podzol|moss_block/.test(below.typeId)) {
        b.setType(plants[rand(plants.length)]);
        try {
          dim.spawnParticle("minecraft:villager_happy", { x: x + 0.5, y: y + 0.5, z: z + 0.5 });
        } catch {}
        return { key: "garden" };
      }
    }
  }
  return false;
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
  { weight: 2, run: garden },
  { weight: 3, run: () => ({ key: "idle" }) },
];
const PRANK_TOTAL = PRANKS.reduce((s, h) => s + h.weight, 0);

// the only pranks allowed while the player is fighting or nearly dead
const HARMLESS = [sort, garden, () => ({ key: "idle" })];

/** @param {Player} player @returns {(player: Player, bobik: Entity) => Prank} */
function pickPrank(player) {
  if (inDanger(player)) return HARMLESS[rand(HARMLESS.length)];
  let r = rand(PRANK_TOTAL);
  for (const h of PRANKS) {
    if ((r -= h.weight) < 0) return h.run;
  }
  return PRANKS[0].run;
}

// per player: how to undo Bobik's last prank, until Klod gets to it (or 5 minutes pass)
const pendingUndo = new Map();

/**
 * Bobik "helps", then, if Klod is around, Klod comments, sometimes fixes it, and Bobik answers back.
 * @param {Player} player @param {Entity} bobik @param {Entity} [klod]
 */
function bobikHelp(player, bobik, klod) {
  for (let tries = 0; tries < 5; tries++) {
    /** @type {Prank} */
    let prank = false;
    try {
      prank = pickPrank(player)(player, bobik);
    } catch {
      // unloaded chunk, world border etc. - just try something else
    }
    if (!prank) continue;
    say(player, bobik, prank.key, { important: true });
    if (prank.undo) pendingUndo.set(player.id, { undo: prank.undo, at: system.currentTick });
    if (klod?.isValid && prank.key !== "idle" && !quiet(klod) && chance(0.6)) {
      if (prank.undo && chance(0.7)) {
        pendingUndo.delete(player.id);
        say(player, klod, "undo", { important: true, then: prank.undo });
      } else {
        say(player, klod, "roast", { important: true });
      }
      if (!quiet(bobik) && chance(0.45)) say(player, bobik, "reply_klod", { important: true });
    }
    return;
  }
}

// ---------------------------------------------------------------------------------------------
// Klod's actual help

/** The closest monster that is a real threat: not neutral, not named, not on a lead, not being cured. @param {Player} player */
function nearestMonster(player, range = 7) {
  const mobs = player.dimension.getEntities({ families: ["monster"], location: player.location, maxDistance: range });
  return mobs.find(
    (m) =>
      m.isValid &&
      m.typeId !== BOBIK &&
      m.typeId !== KLOD &&
      !NEUTRAL.test(m.typeId) &&
      !m.nameTag &&
      !m.getComponent("minecraft:leashable")?.isLeashed &&
      Math.abs(m.location.y - player.location.y) <= 3,
  );
}

const lastGuardLine = new Map();
/** Hits the nearest monster hard. Talks about it only now and then. @param {Player} player @param {Entity} klod */
function guard(player, klod) {
  if (player.getGameMode() === GameMode.Creative) return false;
  const mob = nearestMonster(player);
  if (!mob) return false;
  particle(mob, "minecraft:critical_hit_emitter", 1);
  sound(mob, "random.orb", 0.6);
  mob.applyDamage(30, { cause: EntityDamageCause.entityAttack, damagingEntity: klod });
  const dead = !mob.isValid || (mob.getComponent("minecraft:health")?.currentValue ?? 0) <= 0;
  const now = system.currentTick;
  if (dead && now - (lastGuardLine.get(player.id) ?? -10000) > 30 * 20 && !quiet(klod) && say(player, klod, "guard")) {
    lastGuardLine.set(player.id, now);
  }
  return true;
}

/** @param {Player} player */
function isDark(player) {
  if (player.dimension.id !== "minecraft:overworld") return false;
  const t = world.getTimeOfDay();
  return (t > 13000 && t < 23000) || player.location.y < 50;
}

/** @param {Player} player */
function lightNearby(player, r = 4) {
  const p = floorLoc(player.location);
  for (let x = -r; x <= r; x++)
    for (let z = -r; z <= r; z++)
      for (let y = -2; y <= 2; y++) {
        const id = player.dimension.getBlock({ x: p.x + x, y: p.y + y, z: p.z + z })?.typeId ?? "";
        if (/torch|lantern|glowstone|shroomlight|froglight|campfire/.test(id)) return true;
      }
  return false;
}

/** Places a torch where the player stands. @param {Player} player */
function torch(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  if (p.y <= dim.heightRange.min || p.y >= dim.heightRange.max) return false;
  const here = dim.getBlock(p);
  const below = dim.getBlock({ x: p.x, y: p.y - 1, z: p.z });
  if (!here?.isAir || !below || below.isAir || below.isLiquid || /leaves|glass|fence|wall|slab|stairs|web|torch/.test(below.typeId)) return false;
  if (lightNearby(player)) return false;
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
    pendingUndo.delete(player.id);
    return "fix_vasyan";
  }
  const pending = pendingUndo.get(player.id);
  if (pending) {
    pendingUndo.delete(player.id);
    if (system.currentTick - pending.at < 5 * 60 * 20) {
      pending.undo();
      return "undo";
    }
  }
  if (hurtBy(player) >= 6) {
    player.addEffect("regeneration", 100, { amplifier: 1 });
    particle(player, "minecraft:heart_particle");
    return "heal";
  }
  const loot = itemsAround(player, 8).filter((i) => dist(i.location, player.location) > 2.5);
  if (loot.length >= 3 && chance(0.5)) {
    for (const i of loot) i.teleport(player.location);
    return "collect_auto";
  }
  if (isDark(player) && torch(player)) return "torch";
  if (hurtBy(player) >= 2 && chance(0.5)) {
    player.addEffect("saturation", 2, { amplifier: 0 });
    return "feed";
  }
  return tips && chance(0.35) ? "tip" : false;
}

// ---------------------------------------------------------------------------------------------
// Reactions to what happens to the owner

const lastReact = new Map();
/**
 * One of the companions near the player says something about it (not too often).
 * @param {Player} player @param {string} key @param {number} [p] @param {boolean} [force]
 * @param {{skip?: string, then?: (who: Entity) => void}} [opts]
 */
function react(player, key, p = 1, force = false, opts = {}) {
  const skip = opts.skip;
  const now = system.currentTick;
  if (!force && now - (lastReact.get(player.id) ?? -10000) < REACT_COOLDOWN) return;
  if (!chance(p) || busy(player)) return;
  const { bobik, klod } = near(player);
  const who = [bobik, klod].filter((e) => e && e.typeId !== skip && !quiet(e) && hasLine(e, key, player));
  if (!who.length) return;
  const speaker = who[rand(who.length)];
  if (say(player, speaker, key, { then: opts.then && (() => opts.then(speaker)) })) lastReact.set(player.id, now);
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
    react(player, "low_hp", 1, true, {
      then: (who) => who.typeId === KLOD && player.addEffect("regeneration", 160, { amplifier: 1 }),
    });
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
    if ((!e.nameTag || DEFAULT_NAMES.has(e.nameTag)) && e.nameTag !== ui(e, player).name) e.nameTag = ui(e, player).name;
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

  if (bobik && klod && !busy(player) && !quiet(bobik) && !quiet(klod)) {
    const next = nextArgue.get(player.id);
    if (next === undefined || next > now + ARGUE_DELAY[1]) nextArgue.set(player.id, now + between(ARGUE_DELAY));
    else if (now >= next) {
      nextArgue.set(player.id, now + between(ARGUE_DELAY));
      return argue(player, bobik, klod);
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
        if (key === "fix_vasyan" && bobik && !quiet(bobik) && chance(0.5)) say(player, bobik, "reply_klod", { important: true });
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
    lastHurt.set(target.id, system.currentTick);
    if (ev.damage > 0 && attacker && attacker.typeId !== BOBIK && attacker.typeId !== KLOD) react(target, "hurt", 0.35);
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
        then: () => {
          if (!attacker.isValid || !target.isValid) return;
          if (target.typeId === BOBIK) return knock(attacker, target, 2.2);
          // Klod keeps his word and steps away
          const dx = target.location.x - attacker.location.x;
          const dz = target.location.z - attacker.location.z;
          const len = Math.hypot(dx, dz) || 1;
          target.applyKnockback({ x: (dx / len) * 1.8, z: (dz / len) * 1.8 }, 0.35);
        },
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
  const skip = world.gameRules.keepInventory ? KLOD : undefined;
  system.runTimeout(() => player.isValid && react(player, "respawn", 1, true, { skip }), 60);
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
// Arguments between the two

const nextArgue = new Map();

/** Plays one of the prepared arguments, each line in its speaker's voice. @param {Player} player @param {Entity} bobik @param {Entity} klod */
function argue(player, bobik, klod) {
  const l = lang(player);
  let set = matOn(player) ? "mat" : "normal";
  if (!DIALOGS[l][set].length) set = set === "mat" ? "normal" : "mat";
  const list = DIALOGS[l][set];
  if (!list.length) {
    say(player, klod, "roast", { important: true });
    say(player, bobik, "reply_klod", { important: true });
    return;
  }
  const d = rand(list.length);
  const q = queueOf(player);
  list[d].forEach(([speaker, text, ticks], i) => {
    q.items.push({ who: speaker === "vasyan" ? bobik : klod, line: { sid: `dialog.${l}.${set}.${d + 1}.${i + 1}`, text, ticks } });
  });
  pump(player);
}

/** @param {Player} player @param {Entity} who */
function argueFromMenu(player, who) {
  const { bobik, klod } = near(player);
  if (!bobik || !klod) return say(player, who, "cmd_cant", { important: true });
  argue(player, bobik, klod);
}

// ---------------------------------------------------------------------------------------------
// Commands the player types (menu "Дати команду"): Ukrainian or Russian words -> what to do.
// Klod does it properly, Bobik does it his way.

// [intent, word beginnings, phrases]; the first match wins, so the order matters
/** @type {[string, string[], string[]][]} */
const INTENTS = [
  ["bye", [], ["йди геть", "іди геть", "піди геть", "пішов геть", "уходи", "зникни", "исчезни", "пошел вон", "вали звідси", "вали отсюда"]],
  ["give_back", ["поверн", "верни", "віддай", "отдай"], []],
  ["quiet", ["мовч", "помовч", "замовк", "молч", "помолч", "замолч", "тихо", "заткн"], []],
  ["follow", ["слідуй", "следуй"], ["за мною", "за мной", "йди сюди", "іди сюди", "иди сюда", "ко мне", "до мене"]],
  ["stay", ["сиди", "стій", "стой", "чекай", "жди", "залишайся", "оставайся", "охороняй", "охраняй"], []],
  ["argue", ["свар", "посвар", "спор", "поспор", "сперечай", "посперечай", "руга", "поруга", "срач"], []],
  ["joke", ["анекдот", "жарт", "пожартуй", "шутк", "пошути", "смішн", "смешн", "розсміши", "рассмеши"], []],
  ["dance", ["танц", "потанц", "станц"], []],
  ["kill", ["вбий", "вбивай", "убей", "убивай", "атак", "бий", "бей", "монстр", "зомб", "скелет", "павук", "паук", "крипер", "кріпер", "захист", "защит"], []],
  ["tunnel", ["тунел", "туннел", "шахт", "копа", "покопа", "прокопа", "викопа", "выкопа", "рий", "рой", "вирий", "вырой"], []],
  ["chop", ["дерев", "руби", "рубай", "зрубай", "сруби", "наруби", "сокир", "топор", "колод", "дров", "бревн"], []],
  ["bridge", ["міст", "мост"], []],
  ["build", ["будин", "будуй", "збудуй", "побудуй", "дім", "хат", "дом", "строй", "построй", "укрит", "сховищ", "убежищ", "халуп"], []],
  ["torches", ["факел", "світл", "свет", "освіт", "освет", "темн", "темряв"], []],
  ["food", ["їж", "їст", "поїст", "голод", "жрат", "пожрат", "жерт", "хліб", "хлеб", "еда", "еды", "еду", "поесть", "поешь", "кушат", "покуш"], []],
  ["collect", ["збер", "позбир", "собер", "подбер", "підбер", "лут", "предмет", "підніми", "подними"], []],
  ["find", ["алмаз", "руд", "знайди", "найди", "шукай", "пошукай", "ищи", "поищи", "залізо", "железо", "золото", "смарагд", "изумруд"], []],
  ["heal", ["лік", "полікуй", "вилікуй", "леч", "вылечи", "хіл", "хил", "здоров"], []],
  ["time", ["день", "днем", "вдень", "ніч", "ночь", "сонц", "солнц", "ранок", "утро", "вечір", "вечер"], []],
];

/** @param {string} text */
function parseIntent(text) {
  const t = text.toLowerCase().replace(/ё/g, "е").replace(/[’ʼ`]/g, "'");
  const words = t.split(/[^a-zа-яіїєґ']+/).filter(Boolean);
  for (const [intent, stems, phrases] of INTENTS) {
    if (phrases.some((ph) => t.includes(ph))) return intent;
    if (words.some((w) => stems.some((st) => w.startsWith(st)))) return intent;
  }
  return "unknown";
}

/** @typedef {{key: string, run?: () => void, undo?: () => void} | false} Job */

/** Horizontal direction the player looks at, snapped to an axis. @param {Player} player */
function facing(player) {
  const d = player.getViewDirection();
  return Math.abs(d.x) > Math.abs(d.z) ? { x: Math.sign(d.x), z: 0 } : { x: 0, z: Math.sign(d.z) || 1 };
}

const ORE = /_ore$|^minecraft:ancient_debris$/;
/** @param {Block | undefined} b */
const minable = (b) => !!b && canBreak(b) && (DIGGABLE.test(b.typeId) || ORE.test(b.typeId));
/** Breaks a block the way a player would, so it drops. @param {import("@minecraft/server").Dimension} dim @param {Vector3} l */
const breakBlock = (dim, l) => dim.runCommand(`setblock ${l.x} ${l.y} ${l.z} air destroy`);
/** @param {import("@minecraft/server").Dimension} dim @param {Vector3} l */
const inWorld = (dim, l) => l.y > dim.heightRange.min && l.y < dim.heightRange.max - 1;
/** @param {Player} player */
const itemsAround = (player, r = 16) => player.dimension.getEntities({ type: "minecraft:item", location: player.location, maxDistance: r });

/** @param {Player} player */
function monstersAround(player, range = 12) {
  return player.dimension
    .getEntities({ families: ["monster"], location: player.location, maxDistance: range })
    .filter((m) => m.isValid && m.typeId !== BOBIK && m.typeId !== KLOD && !NEUTRAL.test(m.typeId) && !m.nameTag && !m.getComponent("minecraft:leashable")?.isLeashed);
}

/** A 1x2 tunnel up to 8 blocks forward, through natural ground only, stopping before water or lava. @param {Player} player */
function planTunnel(player) {
  const f = facing(player);
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const out = [];
  for (let i = 1; i <= 8; i++) {
    for (const dy of [0, 1]) {
      const l = { x: p.x + f.x * i, y: p.y + dy, z: p.z + f.z * i };
      if (!inWorld(dim, l)) return out;
      const b = dim.getBlock(l);
      if (!b || b.isLiquid) return out;
      for (const [nx, ny, nz] of [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, 0, 1], [0, 0, -1]]) {
        if (dim.getBlock({ x: l.x + nx, y: l.y + ny, z: l.z + nz })?.isLiquid) return out;
      }
      if (b.isAir) continue;
      if (!minable(b)) return out;
      out.push(l);
    }
  }
  return out;
}

const LOG = /_log$|_stem$/;
/** The nearest natural tree (logs with leaves on them), all its logs top first. @param {Player} player */
function planTree(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  let start;
  let best = Infinity;
  for (let x = -8; x <= 8; x++)
    for (let z = -8; z <= 8; z++)
      for (let y = -2; y <= 6; y++) {
        const l = { x: p.x + x, y: p.y + y, z: p.z + z };
        if (!inWorld(dim, l)) continue;
        const id = dim.getBlock(l)?.typeId ?? "";
        if (LOG.test(id) && !id.includes("stripped") && x * x + y * y + z * z < best) {
          best = x * x + y * y + z * z;
          start = l;
        }
      }
  if (!start) return [];
  const key = (l) => `${l.x},${l.y},${l.z}`;
  const seen = new Set([key(start)]);
  const logs = [start];
  let leaves = 0;
  for (let i = 0; i < logs.length && logs.length < 96; i++) {
    for (let dx = -1; dx <= 1; dx++)
      for (let dy = -1; dy <= 1; dy++)
        for (let dz = -1; dz <= 1; dz++) {
          const l = { x: logs[i].x + dx, y: logs[i].y + dy, z: logs[i].z + dz };
          if (seen.has(key(l)) || !inWorld(dim, l)) continue;
          seen.add(key(l));
          const id = dim.getBlock(l)?.typeId ?? "";
          if (LOG.test(id) && !id.includes("stripped")) logs.push(l);
          else if (id.includes("leaves")) leaves++;
        }
  }
  // a log cabin has no leaves on it: leave the player's buildings alone
  if (leaves < 4) return [];
  return logs.sort((a, b) => b.y - a.y);
}

/** Air, or a plant/snow layer that a block can simply replace. @param {Block | undefined} b */
const replaceable = (b) => !!b && (b.isAir || /short_grass|tall_grass|fern|snow_layer|deadbush|poppy|dandelion/.test(b.typeId));

/** A 5x5 plank hut around the player with a doorway where he looks: [layer][block]. @param {Player} player */
function planShelter(player) {
  const p = floorLoc(player.location);
  const f = facing(player);
  const dim = player.dimension;
  const layers = [[], [], [], []];
  if (!inWorld(dim, { x: p.x, y: p.y + 4, z: p.z })) return layers;
  for (let dy = 0; dy <= 3; dy++)
    for (let dx = -2; dx <= 2; dx++)
      for (let dz = -2; dz <= 2; dz++) {
        const wall = Math.max(Math.abs(dx), Math.abs(dz)) === 2;
        if (dy < 3 && !wall) continue;
        if (dy < 2 && dx === 2 * f.x && dz === 2 * f.z) continue; // the doorway
        const l = { x: p.x + dx, y: p.y + dy, z: p.z + dz };
        if (replaceable(dim.getBlock(l))) layers[dy].push(l);
      }
  return layers;
}

/** A cobblestone path forward over air, water or lava. @param {Player} player */
function planBridge(player) {
  const f = facing(player);
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const out = [];
  for (let i = 1; i <= 12; i++) {
    const l = { x: p.x + f.x * i, y: p.y - 1, z: p.z + f.z * i };
    if (!inWorld(dim, l)) break;
    const b = dim.getBlock(l);
    if (b && (b.isAir || b.isLiquid)) out.push(l);
  }
  return out;
}

/** Spots for a ring of torches five blocks around the player. @param {Player} player */
function planTorches(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const out = [];
  for (let k = 0; k < 8; k++) {
    const x = p.x + Math.round(Math.cos((k * Math.PI) / 4) * 5);
    const z = p.z + Math.round(Math.sin((k * Math.PI) / 4) * 5);
    for (let y = p.y + 2; y >= p.y - 3; y--) {
      if (!inWorld(dim, { x, y, z })) continue;
      const here = dim.getBlock({ x, y, z });
      const below = dim.getBlock({ x, y: y - 1, z });
      if (here?.isAir && below && !below.isAir && !below.isLiquid && !/leaves|glass|fence|wall|slab|stairs|web|torch/.test(below.typeId)) {
        out.push({ x, y, z });
        break;
      }
    }
  }
  return out;
}

// most valuable first
const ORES = [/diamond_ore/, /emerald_ore/, /ancient_debris/, /gold_ore/, /iron_ore/, /lapis_ore|redstone_ore/];
/** The best ore within 8 blocks. @param {Player} player @returns {Vector3 | undefined} */
function findOre(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  let found;
  let rank = ORES.length;
  let best = Infinity;
  for (let x = -8; x <= 8; x++)
    for (let y = -8; y <= 8; y++)
      for (let z = -8; z <= 8; z++) {
        const l = { x: p.x + x, y: p.y + y, z: p.z + z };
        if (!inWorld(dim, l)) continue;
        const id = dim.getBlock(l)?.typeId ?? "";
        if (!id.includes("ore") && !id.includes("debris")) continue;
        const r = ORES.findIndex((re) => re.test(id));
        const d = x * x + y * y + z * z;
        if (r >= 0 && (r < rank || (r === rank && d < best))) {
          rank = r;
          best = d;
          found = l;
        }
      }
  return found;
}

/** Sparkles from the player to the target for ten seconds. @param {Player} player @param {Vector3} target */
function trail(player, target) {
  let n = 0;
  const run = system.runInterval(() => {
    if (!player.isValid || ++n > 20) return system.clearRun(run);
    const from = { x: player.location.x, y: player.location.y + 1.2, z: player.location.z };
    const to = { x: target.x + 0.5, y: target.y + 0.5, z: target.z + 0.5 };
    const len = dist(from, to);
    for (let t = 1; t < len; t += 1) {
      try {
        player.dimension.spawnParticle("minecraft:villager_happy", {
          x: from.x + ((to.x - from.x) * t) / len,
          y: from.y + ((to.y - from.y) * t) / len,
          z: from.z + ((to.z - from.z) * t) / len,
        });
      } catch {}
    }
  }, 10);
}

/** Runs `step` for each element, `every` ticks apart. @template T @param {T[]} list @param {(x: T) => void} step */
function slowly(list, step, every = 3) {
  list.forEach((x, i) =>
    system.runTimeout(() => {
      try {
        step(x);
      } catch {}
    }, i * every + 1),
  );
}

/** What Klod does for an intent, or false when it cannot be done here. @param {Player} player @param {Entity} klod @param {string} intent @returns {Job} */
function klodJob(player, klod, intent) {
  const dim = player.dimension;
  switch (intent) {
    case "kill": {
      const mobs = monstersAround(player).slice(0, 6);
      if (!mobs.length) return false;
      return {
        key: "cmd_kill",
        run: () =>
          slowly(mobs, (m) => {
            if (!m.isValid) return;
            particle(m, "minecraft:critical_hit_emitter", 1);
            m.applyDamage(30, { cause: EntityDamageCause.entityAttack, damagingEntity: klod });
          }, 6),
      };
    }
    case "tunnel": {
      if (player.getGameMode() === GameMode.Adventure) return false;
      const plan = planTunnel(player);
      return plan.length ? { key: "cmd_tunnel", run: () => slowly(plan, (l) => breakBlock(dim, l), 4) } : false;
    }
    case "chop": {
      if (player.getGameMode() === GameMode.Adventure) return false;
      const logs = planTree(player);
      return logs.length ? { key: "cmd_chop", run: () => slowly(logs, (l) => breakBlock(dim, l), 3) } : false;
    }
    case "build": {
      if (player.getGameMode() === GameMode.Adventure) return false;
      const layers = planShelter(player);
      if (layers.flat().length < 12) return false;
      return {
        key: "cmd_build",
        run: () =>
          slowly(layers, (layer) => {
            for (const l of layer) {
              const b = dim.getBlock(l);
              if (replaceable(b)) b.setType("minecraft:oak_planks");
            }
            sound(klod, "dig.wood");
          }, 10),
      };
    }
    case "bridge": {
      if (player.getGameMode() === GameMode.Adventure) return false;
      const plan = planBridge(player);
      return plan.length ? { key: "cmd_bridge", run: () => slowly(plan, (l) => dim.getBlock(l)?.setType("minecraft:cobblestone"), 3) } : false;
    }
    case "torches": {
      if (player.getGameMode() === GameMode.Adventure) return false;
      const spots = planTorches(player);
      return spots.length ? { key: "cmd_torches", run: () => slowly(spots, (l) => dim.getBlock(l)?.setType("minecraft:torch"), 4) } : false;
    }
    case "food":
      return { key: "cmd_food", run: () => giveOrDrop(player, new ItemStack("minecraft:bread", 3)) };
    case "collect": {
      const items = itemsAround(player);
      return items.length ? { key: "cmd_collect", run: () => items.forEach((i) => i.isValid && i.teleport(player.location)) } : false;
    }
    case "find": {
      const ore = findOre(player);
      return ore ? { key: "cmd_found", run: () => trail(player, ore) } : { key: "cmd_notfound" };
    }
    case "heal":
      return { key: "heal", run: () => player.addEffect("regeneration", 160, { amplifier: 1 }) };
    case "time":
      return { key: "cmd_time" };
    default:
      return { key: "cmd_unknown" };
  }
}

/** Bobik's version of the same intent. @param {Player} player @param {Entity} bobik @param {string} intent @returns {Job} */
function bobikJob(player, bobik, intent) {
  const dim = player.dimension;
  switch (intent) {
    case "kill": {
      // he "deals with" the monsters: by healing them
      const mobs = monstersAround(player);
      if (!mobs.length) return false;
      return {
        key: "mobheal",
        run: () =>
          mobs.forEach((m) => {
            if (!m.isValid) return;
            m.addEffect("regeneration", 200, { amplifier: 1 });
            particle(m, "minecraft:heart_particle", 1.5);
          }),
      };
    }
    case "tunnel":
      return dig(player);
    case "build":
      return house(player);
    case "chop":
      return { key: "cmd_chop", run: () => giveOrDrop(player, new ItemStack("minecraft:stick", 1)) };
    case "bridge": {
      // a dirt pillar under the player instead of a bridge
      if (!canBuild(player)) return false;
      const p = floorLoc(player.location);
      for (let dy = 2; dy <= 5; dy++) if (!dim.getBlock({ x: p.x, y: p.y + dy, z: p.z })?.isAir) return false;
      return {
        key: "cmd_bridge",
        run: () =>
          slowly([0, 1, 2], () => {
            if (!player.isValid) return;
            const feet = dim.getBlock(floorLoc(player.location));
            if (!feet?.isAir) return;
            player.teleport({ x: player.location.x, y: player.location.y + 1.05, z: player.location.z });
            feet.setType("minecraft:dirt");
          }, 8),
      };
    }
    case "torches": {
      // exactly one torch, problem solved
      const p = floorLoc(player.location);
      const here = dim.getBlock(p);
      const below = dim.getBlock({ x: p.x, y: p.y - 1, z: p.z });
      if (!canBuild(player) || !here?.isAir || !below || below.isAir || below.isLiquid) return false;
      return { key: "cmd_torches", run: () => here.isValid && here.isAir && here.setType("minecraft:torch") };
    }
    case "food":
      return eat(player, bobik);
    case "collect": {
      // into his own pockets
      const items = itemsAround(player);
      const bag = container(bobik);
      if (!items.length || !bag) return false;
      return {
        key: "cmd_collect",
        run: () => {
          for (const i of items) {
            const stack = i.isValid ? i.getComponent("minecraft:item")?.itemStack : undefined;
            if (stack && !bag.addItem(stack)) i.remove();
          }
        },
      };
    }
    case "find":
      return { key: "diamonds", run: () => giveOrDrop(player, fakeDiamond(bobik, player)) };
    case "heal":
      return heal(player);
    case "time":
      return {
        key: "cmd_time",
        run: () => {
          const t = world.getTimeOfDay();
          world.setTimeOfDay(t > 12500 && t < 23500 ? 1000 : 13000);
        },
      };
    default:
      return false;
  }
}

/** Bobik gives back what he has in his pockets (and sometimes "forgets" one thing). @param {Player} player @param {Entity} bobik */
function giveBack(player, bobik) {
  const bag = container(bobik);
  const items = [];
  for (let i = 0; bag && i < bag.size; i++) if (bag.getItem(i)) items.push(i);
  if (!items.length) return say(player, bobik, "give_back_empty", { important: true });
  const keep = items.length > 1 && chance(0.2) ? items[rand(items.length)] : -1;
  for (const i of items) {
    if (i === keep) continue;
    giveOrDrop(player, bag.getItem(i));
    bag.setItem(i, undefined);
  }
  say(player, bobik, keep >= 0 ? "give_back_forgot" : "give_back", { important: true });
}

/**
 * Carries out a typed command.
 * @param {Player} player @param {Entity} who @param {string} text
 */
function command(player, who, text) {
  const intent = parseIntent(text);
  const { bobik, klod } = near(player);
  const isBobik = who.typeId === BOBIK;
  // the things both do the same way
  switch (intent) {
    case "bye":
      return dismiss(player, who);
    case "give_back":
      if (isBobik) return giveBack(player, who);
      if (bobik && container(bobik)?.emptySlotsCount !== container(bobik)?.size) {
        return say(player, who, "fix_vasyan", { important: true, then: () => returnStolen(player, bobik) });
      }
      return say(player, who, "cmd_cant", { important: true });
    case "quiet":
      who.setDynamicProperty(QUIET, system.currentTick + QUIET_TICKS);
      return say(player, who, "quiet", { important: true });
    case "follow":
    case "stay":
      return setStay(player, who, intent === "stay");
    case "argue":
      return argueFromMenu(player, who);
    case "joke":
      return say(player, who, "joke", { important: true });
    case "dance":
      return dance(player, who);
  }
  if (isBobik) {
    // he gets the order wrong on purpose every so often, and always when he has no idea
    const job = intent === "unknown" || chance(0.2) ? false : bobikJob(player, who, intent);
    if (!job) {
      if (intent !== "unknown" && chance(0.5)) return say(player, who, "cmd_cant", { important: true });
      return say(player, who, "cmd_misunderstood", { important: true, then: () => system.runTimeout(() => who.isValid && bobikHelp(player, who, klod), 20) });
    }
    say(player, who, job.key, { important: true, then: job.run });
    if (job.undo) pendingUndo.set(player.id, { undo: job.undo, at: system.currentTick });
    if (klod && !quiet(klod) && chance(0.5)) {
      if (job.undo) pendingUndo.delete(player.id);
      say(player, klod, job.undo ? "undo" : "roast", { important: true, then: job.undo });
    }
    return;
  }
  const job = klodJob(player, who, intent);
  if (!job) return say(player, who, "cmd_cant", { important: true });
  if (job.run && chance(0.35)) say(player, who, "cmd_ok", { important: true });
  say(player, who, job.key, { important: true, then: job.run });
  if (bobik && !quiet(bobik) && job.run && chance(0.3)) say(player, bobik, "reply_klod", { important: true });
}

/** The text box for a command. @param {Player} player @param {Entity} who */
async function askCommand(player, who) {
  const t = ui(who, player);
  const res = await new ModalFormData().title(t.command_title).textField(t.command_label, t.command_hint).show(player);
  if (res.canceled || !res.formValues || !who.isValid || !player.isValid) return;
  const text = String(res.formValues[0] ?? "").trim();
  if (text) command(player, who, text);
}

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
  player.setDynamicProperty(MAT, !matOn(player));
  say(player, who, matOn(player) ? "mat_on" : "mat_off", { important: true });
}

/** @param {Player} player @param {Entity} who */
function toggleLang(player, who) {
  player.setDynamicProperty(LANG, lang(player) === "uk" ? "ru" : "uk");
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

/** A real-looking diamond. @param {Entity} bobik @param {Player} player */
function fakeDiamond(bobik, player) {
  const it = new ItemStack("minecraft:dirt", 1);
  it.nameTag = ui(bobik, player).fake_diamond;
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
    return say(player, bobik, "gift_shiny", { important: true, then: () => giveOrDrop(player, fakeDiamond(bobik, player)) });
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
  const t = ui(bobik, player);
  const klod = near(player).klod;
  const stay = bobik.getDynamicProperty(STAY) === true;
  return menu(player, bobik, t.title, matOn(player) ? t.body_mat : t.body, [
    [t.command, () => askCommand(player, bobik)],
    [t.talk, () => bobikTalk(player, bobik)],
    [t.give_back, () => giveBack(player, bobik)],
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
    [matOn(player) ? t.mat_off : t.mat_on, () => toggleMat(player, bobik)],
    [t.lang, () => toggleLang(player, bobik)],
    [t.bye, () => dismiss(player, bobik)],
  ]);
}

/** @param {Player} player @param {Entity} bobik */
function bobikTalk(player, bobik) {
  const t = ui(bobik, player);
  const ask = (key, then) => () => say(player, bobik, key, { important: true, then });
  return menu(player, bobik, t.talk_title, matOn(player) ? t.talk_body_mat : t.talk_body, [
    [t.q_joke, ask("joke")],
    [t.q_advice, ask("advice")],
    [t.q_diamonds, ask("diamonds", () => giveOrDrop(player, fakeDiamond(bobik, player)))],
    [t.q_how, ask("how")],
    [t.q_stupid, ask("stupid", () => chance(0.4) && knock(player, bobik, 1.2))],
    [t.q_about, ask("about")],
    [t.argue, () => argueFromMenu(player, bobik)],
    [t.back, () => bobikMenu(player, bobik)],
  ]);
}

/** @param {Player} player @param {Entity} klod */
function klodMenu(player, klod) {
  const t = ui(klod, player);
  const stay = klod.getDynamicProperty(STAY) === true;
  return menu(player, klod, t.title, matOn(player) ? t.body_mat : t.body, [
    [t.command, () => askCommand(player, klod)],
    [t.talk, () => klodTalk(player, klod)],
    [
      t.help,
      () => {
        const bobik = near(player).bobik;
        say(player, klod, "help", { important: true });
        if (guard(player, klod)) return;
        const pending = pendingUndo.get(player.id);
        if (pending) {
          pendingUndo.delete(player.id);
          return say(player, klod, "undo", { important: true, then: pending.undo });
        }
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
    [matOn(player) ? t.mat_off : t.mat_on, () => toggleMat(player, klod)],
    [t.lang, () => toggleLang(player, klod)],
    [t.bye, () => dismiss(player, klod)],
  ]);
}

/** @param {Player} player @param {Entity} klod */
function klodTalk(player, klod) {
  const t = ui(klod, player);
  const ask = (key) => () => {
    say(player, klod, key, { important: true });
    // Bobik does not let an opinion about himself go unanswered
    const bobik = near(player).bobik;
    if (key === "vasyan" && bobik) say(player, bobik, "reply_klod", { important: true });
  };
  return menu(player, klod, t.talk_title, matOn(player) ? t.talk_body_mat : t.talk_body, [
    [t.q_joke, ask("joke")],
    [t.q_advice, ask("tip")],
    [t.q_how, ask("how")],
    [t.q_vasyan, ask("vasyan")],
    [t.q_smart, ask("smart")],
    [t.q_about, ask("about")],
    [t.argue, () => argueFromMenu(player, klod)],
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
  if (e.typeId === BOBIK && ev.beforeItemStack?.typeId === "minecraft:redstone") return gift(player, e);
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
  const players = ev.sourceEntity instanceof Player ? [ev.sourceEntity] : world.getPlayers();
  if (ev.id === "vasyan:toggle") world.setDynamicProperty(ENABLED, world.getDynamicProperty(ENABLED) === false);
  for (const player of players) {
    const { bobik, klod } = near(player, owned.get(player.id) ?? []);
    const who = bobik ?? klod;
    if (ev.id === "vasyan:mat") {
      player.setDynamicProperty(MAT, !matOn(player));
      if (who) say(player, who, matOn(player) ? "mat_on" : "mat_off", { important: true });
    } else {
      const t = VOICE.vasyan[lang(player)].ui;
      player.onScreenDisplay.setActionBar(world.getDynamicProperty(ENABLED) === false ? t.pranks_off : t.pranks_on);
    }
  }
});
