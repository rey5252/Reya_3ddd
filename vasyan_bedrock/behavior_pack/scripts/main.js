// Vasyan, the dumbest neural network: a companion that follows its owner and "helps" every so often.
// Stable Script API only (@minecraft/server 2.0.0, @minecraft/server-ui 2.0.0), Bedrock 1.21.90+.
import { world, system, GameMode, Player } from "@minecraft/server";
import { ActionFormData } from "@minecraft/server-ui";

/** @typedef {import("@minecraft/server").Entity} Entity */
/** @typedef {import("@minecraft/server").Block} Block */
/** @typedef {import("@minecraft/server").ItemStack} ItemStack */

const VASYAN = "vasyan:vasyan";
const CHIP = "vasyan:ai_chip";
const DIMENSIONS = ["minecraft:overworld", "minecraft:nether", "minecraft:the_end"];

// entity dynamic properties
const OWNER = "vasyan:owner"; // id of the player Vasyan belongs to
const NEXT = "vasyan:next"; // tick of the next "help"
const QUIET = "vasyan:quiet"; // tick until which Vasyan promised to keep quiet
// world dynamic property: false turns all the pranks off (/scriptevent vasyan:toggle)
const ENABLED = "vasyan:enabled";

const MIN_DELAY = 15 * 20;
const MAX_DELAY = 40 * 20;
const QUIET_TICKS = 5 * 60 * 20;
const LEASH = 24; // further than this from the owner and Vasyan just appears next to them

// how many numbered variants each chat line has in texts/*.lang (vasyan.<key>.<n>)
const LINES = {
  hello: 3, here: 2, stranger: 2, dig: 3, house: 3, steal: 3, sort: 3, eat: 3, heal: 3, shortcut: 3,
  push: 3, web: 3, friend: 3, friend_zombie: 2, idle: 6, mobheal: 3, ouch: 3, quiet: 2, quiet_broke: 2,
  give_back: 2, give_back_forgot: 2, give_back_empty: 2, help: 2, bye: 2,
};

// blocks Vasyan must never break, even while "digging"
const PROTECTED = /bedrock|barrier|command_block|structure_block|jigsaw|portal|end_gateway|reinforced_deepslate|spawner|vault|chest|barrel|shulker_box|_bed$|^minecraft:bed$|hopper|furnace|smoker|dispenser|dropper|crafter|beacon|respawn_anchor|light_block/;

const rand = (n) => Math.floor(Math.random() * n);
const chance = (p) => Math.random() < p;
const floorLoc = (l) => ({ x: Math.floor(l.x), y: Math.floor(l.y), z: Math.floor(l.z) });

/** @param {Player} player @param {string} key */
function say(player, key) {
  player.sendMessage({
    rawtext: [{ translate: "vasyan.prefix" }, { text: " " }, { translate: `vasyan.${key}.${1 + rand(LINES[key] ?? 1)}` }],
  });
}

/** @param {Entity} entity @param {string} id */
function sound(entity, id) {
  try {
    entity.dimension.playSound(id, entity.location);
  } catch {}
}

/** @param {Entity} entity */
function container(entity) {
  return entity.getComponent("minecraft:inventory")?.container;
}

function nextDelay() {
  return MIN_DELAY + rand(MAX_DELAY - MIN_DELAY);
}

/** Every Vasyan in the world, grouped by owner id. @returns {Map<string, Entity[]>} */
function vasyansByOwner() {
  const map = new Map();
  for (const id of DIMENSIONS) {
    for (const v of world.getDimension(id).getEntities({ type: VASYAN })) {
      const owner = v.getDynamicProperty(OWNER);
      if (typeof owner !== "string") continue;
      if (!map.has(owner)) map.set(owner, []);
      map.get(owner).push(v);
    }
  }
  return map;
}

/** Makes `player` Vasyan's owner: vanilla taming (so follow_owner works) plus our own owner record. @param {Entity} v @param {Player} player */
function adopt(v, player) {
  try {
    v.getComponent("minecraft:tameable")?.tame(player);
  } catch {}
  v.triggerEvent("vasyan:on_tame");
  v.setDynamicProperty(OWNER, player.id);
  v.setDynamicProperty(NEXT, system.currentTick + nextDelay());
  if (!v.nameTag) v.nameTag = "Васян";
}

// ---------------------------------------------------------------------------------------------
// "Help": each one returns the chat line key on success, or false if it could not happen here.

/** @param {Block | undefined} block */
function canBreak(block) {
  if (!block || block.isAir || block.isLiquid) return false;
  if (PROTECTED.test(block.typeId)) return false;
  return !block.getComponent("minecraft:inventory");
}

/** Digs the block under the player's feet (only if there is solid ground one block lower). @param {Player} player */
function dig(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const below = dim.getBlock({ x: p.x, y: p.y - 1, z: p.z });
  const floor = dim.getBlock({ x: p.x, y: p.y - 2, z: p.z });
  if (!canBreak(below) || !floor || floor.isAir || floor.isLiquid) return false;
  dim.runCommand(`setblock ${p.x} ${p.y - 1} ${p.z} air destroy`);
  return "dig";
}

/** Builds a "house": dirt walls on the four sides of the player and a roof, wherever there is air. @param {Player} player */
function house(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const spots = [{ x: p.x, y: p.y + 2, z: p.z }];
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    spots.push({ x: p.x + dx, y: p.y, z: p.z + dz }, { x: p.x + dx, y: p.y + 1, z: p.z + dz });
  }
  let placed = 0;
  for (const s of spots) {
    const b = dim.getBlock(s);
    if (b?.isAir) {
      b.setType("minecraft:dirt");
      placed++;
    }
  }
  return placed >= 3 ? "house" : false;
}

/** "I'll carry it for you": takes the held item (or a random hotbar item) into Vasyan's bag. @param {Player} player @param {Entity} v */
function steal(player, v) {
  const inv = container(player);
  const bag = container(v);
  if (!inv || !bag || bag.emptySlotsCount === 0) return false;
  let slot = player.selectedSlotIndex;
  if (!inv.getItem(slot)) {
    const full = [];
    for (let i = 0; i < 9; i++) if (inv.getItem(i)) full.push(i);
    if (!full.length) return false;
    slot = full[rand(full.length)];
  }
  const item = inv.getItem(slot);
  if (!item || item.typeId === CHIP) return false;
  inv.setItem(slot, undefined);
  const left = bag.addItem(item);
  if (left) inv.setItem(slot, left);
  return "steal";
}

/** "Sorted your inventory": swaps two random slots. @param {Player} player */
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
    return "sort";
  }
  return false;
}

/** Eats one piece of the player's food. @param {Player} player @param {Entity} v */
function eat(player, v) {
  const inv = container(player);
  if (!inv) return false;
  const food = [];
  for (let i = 0; i < inv.size; i++) {
    const it = inv.getItem(i);
    if (it?.getComponent("minecraft:food")) food.push(i);
  }
  if (!food.length) return false;
  const slot = food[rand(food.length)];
  const it = inv.getItem(slot);
  if (it.amount > 1) {
    it.amount -= 1;
    inv.setItem(slot, it);
  } else {
    inv.setItem(slot, undefined);
  }
  sound(v, "random.burp");
  return "eat";
}

/** "Vitamins": a little regeneration with a lot of side effects. @param {Player} player */
function heal(player) {
  player.addEffect("nausea", 200, { amplifier: 0 });
  player.addEffect("slowness", 100, { amplifier: 1 });
  player.addEffect("regeneration", 60, { amplifier: 0 });
  return "heal";
}

/** "A shortcut": throws the player 8-15 blocks away onto the surface (overworld only). @param {Player} player */
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
  player.teleport({ x: x + 0.5, y: top.location.y + 1, z: z + 0.5 });
  sound(player, "mob.endermen.portal");
  return "shortcut";
}

/** "Was pushing you out of danger": knocks the player away from Vasyan. @param {Player} player @param {Entity} v */
function push(player, v) {
  let dx = player.location.x - v.location.x;
  let dz = player.location.z - v.location.z;
  const len = Math.hypot(dx, dz);
  if (len < 0.1) {
    const a = Math.random() * Math.PI * 2;
    dx = Math.cos(a);
    dz = Math.sin(a);
  } else {
    dx /= len;
    dz /= len;
  }
  player.applyKnockback({ x: dx * 1.6, z: dz * 1.6 }, 0.5);
  return "push";
}

/** "Anti-theft trap": a cobweb at the player's feet that disappears after 8 seconds. @param {Player} player */
function web(player) {
  const p = floorLoc(player.location);
  const dim = player.dimension;
  const b = dim.getBlock(p);
  if (!b?.isAir) return false;
  b.setType("minecraft:web");
  system.runTimeout(() => {
    try {
      const w = dim.getBlock(p);
      if (w?.typeId === "minecraft:web") w.setType("minecraft:air");
    } catch {}
  }, 160);
  return "web";
}

/** "Brought a friend": usually a harmless animal, sometimes a zombie. @param {Player} player @param {Entity} v */
function friend(player, v) {
  const zombie = chance(0.15);
  const id = zombie ? "minecraft:zombie" : ["minecraft:chicken", "minecraft:rabbit", "minecraft:pig"][rand(3)];
  v.dimension.spawnEntity(id, v.location);
  return zombie ? "friend_zombie" : "friend";
}

const HELP = [
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
  { weight: 3, run: () => "idle" },
];
const HELP_TOTAL = HELP.reduce((s, h) => s + h.weight, 0);

function pickHelp() {
  let r = rand(HELP_TOTAL);
  for (const h of HELP) {
    if ((r -= h.weight) < 0) return h;
  }
  return HELP[0];
}

/** Tries a few random "helps" until one of them works here. @param {Player} player @param {Entity} v */
function help(player, v) {
  for (let tries = 0; tries < 5; tries++) {
    try {
      const key = pickHelp().run(player, v);
      if (key) {
        say(player, key);
        return;
      }
    } catch {
      // unloaded chunk, world border etc. - just try something else
    }
  }
}

// ---------------------------------------------------------------------------------------------

system.runInterval(() => {
  const now = system.currentTick;
  const enabled = world.getDynamicProperty(ENABLED) !== false;
  const owned = vasyansByOwner();
  for (const player of world.getPlayers()) {
    const list = owned.get(player.id);
    if (!list) continue;
    for (const v of list) {
      // keep up with the owner even across dimensions
      const far =
        v.dimension.id !== player.dimension.id ||
        Math.hypot(v.location.x - player.location.x, v.location.y - player.location.y, v.location.z - player.location.z) > LEASH;
      if (far) {
        v.teleport(player.location, { dimension: player.dimension });
        continue;
      }
      if (!enabled || player.getGameMode() === GameMode.Spectator) continue;
      const next = v.getDynamicProperty(NEXT);
      if (typeof next !== "number" || next > now + MAX_DELAY) {
        v.setDynamicProperty(NEXT, now + nextDelay());
        continue;
      }
      if (now < next) continue;
      v.setDynamicProperty(NEXT, now + nextDelay());
      const quiet = v.getDynamicProperty(QUIET);
      if (typeof quiet === "number" && now < quiet) {
        if (!chance(0.1)) continue;
        say(player, "quiet_broke");
      }
      help(player, v);
    }
  }
}, 20);

// Hitting a mob near Vasyan: sometimes he feels sorry for the mob and heals it.
const lastMobHeal = new Map();
world.afterEvents.entityHurt.subscribe((ev) => {
  const attacker = ev.damageSource.damagingEntity;
  if (!(attacker instanceof Player)) return;
  const target = ev.hurtEntity;
  if (target.typeId === VASYAN) {
    if (target.getDynamicProperty(OWNER) === attacker.id && chance(0.5)) say(attacker, "ouch");
    return;
  }
  if (target.typeId === "minecraft:player" || world.getDynamicProperty(ENABLED) === false) return;
  const now = system.currentTick;
  if (now - (lastMobHeal.get(attacker.id) ?? -1000) < 200 || !chance(0.3)) return;
  const near = attacker.dimension.getEntities({ type: VASYAN, location: attacker.location, maxDistance: 16 });
  if (!near.some((v) => v.getDynamicProperty(OWNER) === attacker.id)) return;
  lastMobHeal.set(attacker.id, now);
  target.addEffect("regeneration", 100, { amplifier: 1 });
  say(attacker, "mobheal");
});

// Talking to Vasyan: the first player to do it becomes the owner, the owner gets the menu.
world.afterEvents.playerInteractWithEntity.subscribe((ev) => {
  const v = ev.target;
  const player = ev.player;
  if (v.typeId !== VASYAN) return;
  const owner = v.getDynamicProperty(OWNER);
  if (typeof owner !== "string") {
    adopt(v, player);
    say(player, "hello");
    return;
  }
  if (owner !== player.id) {
    say(player, "stranger");
    return;
  }
  if (ev.itemStack?.typeId === "minecraft:redstone") return;
  system.run(() => openMenu(player, v));
});

/** @param {Player} player @param {ItemStack} item */
function giveOrDrop(player, item) {
  const left = container(player)?.addItem(item) ?? item;
  if (left) player.dimension.spawnItem(left, player.location);
}

/** @param {Player} player @param {Entity} v */
async function openMenu(player, v) {
  const form = new ActionFormData()
    .title({ translate: "vasyan.form.title" })
    .body({ translate: "vasyan.form.body" })
    .button({ translate: "vasyan.form.give_back" })
    .button({ translate: "vasyan.form.help" })
    .button({ translate: "vasyan.form.quiet" })
    .button({ translate: "vasyan.form.bye" });
  const res = await form.show(player);
  if (res.canceled || !v.isValid || !player.isValid) return;
  const bag = container(v);
  switch (res.selection) {
    case 0: {
      const items = [];
      for (let i = 0; bag && i < bag.size; i++) {
        const it = bag.getItem(i);
        if (it) items.push(i);
      }
      if (!items.length) {
        say(player, "give_back_empty");
        break;
      }
      // sometimes he "forgets" one thing for later
      const keep = items.length > 1 && chance(0.2) ? items[rand(items.length)] : -1;
      for (const i of items) {
        if (i === keep) continue;
        giveOrDrop(player, bag.getItem(i));
        bag.setItem(i, undefined);
      }
      say(player, keep >= 0 ? "give_back_forgot" : "give_back");
      break;
    }
    case 1:
      say(player, "help");
      system.runTimeout(() => {
        if (v.isValid && player.isValid) help(player, v);
      }, 30);
      v.setDynamicProperty(NEXT, system.currentTick + nextDelay());
      break;
    case 2:
      v.setDynamicProperty(QUIET, system.currentTick + QUIET_TICKS);
      say(player, "quiet");
      break;
    case 3: {
      for (let i = 0; bag && i < bag.size; i++) {
        const it = bag.getItem(i);
        if (it) v.dimension.spawnItem(it, v.location);
      }
      bag?.clearAll();
      say(player, "bye");
      v.remove();
      break;
    }
  }
}

// The AI chip: summons your Vasyan (or calls him back if you already have one).
const lastChipUse = new Map();
/** @param {Player} player */
function useChip(player) {
  const now = system.currentTick;
  if (now - (lastChipUse.get(player.id) ?? -100) < 10) return;
  lastChipUse.set(player.id, now);
  const mine = vasyansByOwner().get(player.id);
  if (mine?.length) {
    mine[0].teleport(player.location, { dimension: player.dimension });
    say(player, "here");
    return;
  }
  const v = player.dimension.spawnEntity(VASYAN, player.location);
  system.runTimeout(() => {
    if (!v.isValid || !player.isValid) return;
    adopt(v, player);
    say(player, "hello");
  }, 2);
  if (player.getGameMode() !== GameMode.Creative) {
    const inv = container(player);
    const it = inv?.getItem(player.selectedSlotIndex);
    if (it?.typeId === CHIP) {
      if (it.amount > 1) {
        it.amount -= 1;
        inv.setItem(player.selectedSlotIndex, it);
      } else {
        inv.setItem(player.selectedSlotIndex, undefined);
      }
    }
  }
}

world.afterEvents.itemUse.subscribe((ev) => {
  if (ev.itemStack?.typeId === CHIP) useChip(ev.source);
});
world.afterEvents.playerInteractWithBlock.subscribe((ev) => {
  if (ev.isFirstEvent && ev.itemStack?.typeId === CHIP) useChip(ev.player);
});

// /scriptevent vasyan:toggle - turns all the "help" on or off for the whole world.
system.afterEvents.scriptEventReceive.subscribe((ev) => {
  if (ev.id !== "vasyan:toggle") return;
  const on = world.getDynamicProperty(ENABLED) === false;
  world.setDynamicProperty(ENABLED, on);
  world.sendMessage({ rawtext: [{ translate: "vasyan.prefix" }, { text: " " }, { translate: on ? "vasyan.toggle.on" : "vasyan.toggle.off" }] });
});
