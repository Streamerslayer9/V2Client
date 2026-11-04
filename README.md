# Vore Mod (mod id: gulp) - Forge 1.20.1

Forge 1.20.1 (47.x) mod: swallow mobs, carry them in a stomach, digest them for loot.

## Build
Easiest: push this folder to a GitHub repo. The workflow in .github/workflows/main.yml builds it
(Actions tab > newest run > Artifacts > gulp-mod).
Locally: install JDK 17 + Gradle 8.1.1, run `gradle build`. The jar lands in build/libs/gulp-0.1.0.jar.
For testing in a dev environment: `gradle runClient`.

## Install
Install Minecraft Forge for 1.20.1, then put gulp-0.1.0.jar in the mods folder. (No Fabric API needed.)

## Controls (rebindable)
- V = swallow the mob you're looking at (within 4 blocks). Looking UP makes the cooldown shorter.
- B = toggle Soft / Hard mode
- G = release the most recent mob. Sneak + G = release everything.
- N = open the stomach screen (see contents, release or digest each mob, spend perk points, settings)

## Mechanics
- Capacity is a volume number. A mob's size is its hitbox width x width x height (chicken ~0.1, villager ~0.7, cow ~1.1,
  horse ~3, iron golem ~5, modded mobs anywhere from 1 to hundreds). A mob fits if it is no bigger than your capacity and
  everything inside adds up to no more than your capacity. You can hold up to 100 creatures at once.
- Capacity = (1 + 0.75 x level + 0.08 x level^2) x (1 + 0.06 x Roomy Stomach rank). Every level gives more room than the
  last: 1.8 at level 1, 16.5 at 10, 48 at 20, 239 at 50, 859 at 99, and up to 2406 with Roomy Stomach 30/30.
- Levels go to 99. XP per level: 100, 280, 520 ... 3160 at level 10, then +40 per level (4080 at level 33, 6720 at
  level 98). Reaching level 33 takes about 630 zombies (with Gourmet), level 99 about 3000.
- Soft mode: mobs are held safely. A completely full stomach earns one level's worth of XP every 4 minutes, so the
  fuller you are the faster you level.
- Hard mode: mobs digest over time (13s for a cow, 22s for an iron golem, about 3 minutes for a size-500 monster).
  When the digestion bar is full they don't die instantly: for about 8 more seconds they take steady damage (their red
  health bar drains on the stomach screen), then they die. A digested mob is never added to the world (nobody sees it), and its loot and XP go straight into the
  digesting player's inventory (overflow lands at their feet). (DISSOLVE_SECONDS in StomachLogic.java.) Digesting gives 20 + 4 x the mob's max health in XP. Now and then (about 8% per mob per second)
  a mob struggles and hurts you: 2 HP plus 0.75 per 1.0 of mob volume, capped at 5 HP. Hard mode only; armor and Iron
  Stomach reduce it, and it never takes you below 2 hearts.
- Perk points: the game hands out exactly enough points by level 99 to buy every rank of every perk (121 points for the
  current 121 ranks), with none left over and none missing. Most levels give 1 point and about every eighth level
  gives 2 (see Stomach.cumulativePoints). If you add or change perks, the points adjust themselves. Older saves are
  topped up automatically to what their level should have earned.
  Spend them in N > Perks. There are two menus (switch with the Legacy / New look button, or Settings > Perk menu):
  the new look (every perk is a node around a hub with a progress ring, no connecting lines because nothing unlocks anything)
  and the legacy list. Click = 1 rank, Shift = 5, Ctrl = as many as you can afford:
  Roomy Stomach (30 ranks, +6% capacity each), Healing Stomach (15, soft mode: held mobs heal 1% of max HP per rank
  every 5s), Better Loot (10, +1 Looting level per 2 ranks on digested mobs, up to V), Quick Gulp (10, swallow
  cooldown -0.075s per rank anywhere and -0.15s per rank when looking up: 2s with no perk, 1.25s / 0.5s at 10 ranks),
  Iron Stomach (15, -5% struggle damage per rank), Gourmet (20, +5% XP from digesting per rank), Hearty Meal (10, heal
  0.5 HP per rank for every mob you digest), Iron Gut (3, more hunger per 32 items eaten, see Item digestion). Add more in Perk.java (append to the end).
- Aiming: mobs have a slightly bigger hitbox for swallowing, and aiming near a mob (about 12 degrees) still counts.
- The Ender Dragon and the Wither can never be swallowed.
- Item storage: look at a dropped item and press V to swallow the stack into your item storage; or open your inventory, point at
  an item and press V (rebindable: "Swallow Hovered Item (in Inventory)") to swallow that stack, holding Ctrl for just one. Only your
  own inventory and hotbar count, not armor, the offhand or the crafting grid. You can also press Storage on the
  stomach screen to open it as a chest-style screen (shift-click moves items in and out; a Back button beside the window returns to the stomach screen). It starts at 1 row (9 slots)
  and gains a row every 17 levels, up to 6 rows (54 slots) at level 85. It is separate from the mob volume, saved with
  the world, safe in both modes, and drops like an inventory if you die (unless keepInventory is on).
- Player boundaries (multiplayer): every player picks a role in Settings ("Role" button, cycles Pred / Prey / Both / Neither; saved
  on the server). Pred (the default) can swallow but can't be swallowed. Prey can be swallowed but can't swallow. Both can do both.
  Neither turns vore off completely: no swallowing, can't be swallowed, and the stomach menu and HUD are hidden (the N key opens
  Settings instead). Prey and Neither can't swallow anything, mobs and items included. Switching to a role that can't swallow lets
  out everything you were holding. Old saves: "Let players swallow me" ON became Both, OFF became Pred.
- Swallow requests: swallowing a player never happens straight away. The target gets [Accept] / [Decline] buttons in chat
  (/gulp accept and /gulp decline do the same) and has 30 seconds. Everything is checked again when they accept (roles, size,
  distance of 8 blocks). One request per target at a time, and after a decline the same player can't ask again for 60 seconds.
- Role in name tags: every player's name tag above their head also shows a coloured badge, e.g. "Streamerslayer9 [Prey]" (client side
  only: ClientRoleTags, via the NameFormat event; refreshed whenever the role list arrives). Colours: Pred red, Prey aqua, Both gold,
  Neither grey. Switch: "Role in name tags" in Settings > Toggles (showRoleTags in config/gulp-client.properties).
- Role tags in multiplayer: the server re-sends the role list every 5 seconds (RolesPacket.broadcast, also on join and on every role
  change) and the client rebuilds name tags once a second, so a badge never gets stuck missing. Debug: /gulproles (client side) lists every
  player this client sees and the role it knows for them.
- Role badges: a player's role shows in the coloured name tag above their head (see "Role in name tags"); there is no separate pop-up under the
  crosshair any more. Trying to swallow a Pred shows "This player is a predator and cannot be swallowed" (a Neither player shows a similar
  "opted out" message).
- Swallowing players (multiplayer): the mode (soft or hard) is fixed when the player is swallowed, and the request they accept says
  which one it is (switching mode before they accept cancels it). Swallowed players are spectators looking through the swallower's
  eyes. In both modes they are let out when the swallower dies, logs out, changes dimension or the server stops, and their game mode
  is remembered (even through a crash) and restored. A HUD at the top shows them who holds them and what their keys do.
  - Soft mode: they are only held, never harmed. Release (G) lets them out any time (or automatically after playerMaxHoldSeconds).
    The Struggle key (R) is a wall rub: the swallower gets food, saturation, Regeneration and Speed, at most once per
    playerWallRubCooldownSeconds.
  - Hard mode (needs playerHardVoreEnabled): a digestion timer runs (playerDigestSeconds, default 3 minutes). Release does not
    work. The Struggle key (R, once a second at most) rolls playerStruggleChancePercent (default 12%, falling to half as digestion
    nears 100%) to break free. When the timer ends they are let out weakened, or die if playerDigestionKills is true (default
    false); the swallower gets XP and food. The request warns about the death setting.
  - Dynamic messages: the swallower's action bar shows "[Name] squirms in your gut" on a failed struggle, "[Name] rubs your
    stomach walls", "[Name] broke free!", "[Name] slept soundly in your gut".
  - Potions: drinking a potion also gives its effects to everyone inside. Instant damage and Wither are never passed on, and other
    harmful effects only reach players held in hard mode.
  - Co-op sleep: players inside don't count towards "everyone must sleep" (they are spectators), so they never hold up the night. When
    the swallower sleeps through the night, they sleep through it too: their rest timer resets (no phantoms) and they get a message.
- Advanced items and equipment:
  - Enchant Stripper (perk, 3 ranks, unlocks at stomach level 50): the storage screen has a panel under Back with two item boxes
    (source on the left, target on the right) and two bars. The boxes are real slots of their own, separate from the storage grid: click
    an item into one like a normal slot (left click puts the stack, right click one item, shift-click sends it back to storage). Their
    contents are saved with the stomach. Strip takes the enchantments off the gear in the source box onto a plain book in the target box (one book is
    used up and becomes the enchanted book, so books can never be duplicated). Destroy deletes the enchantments for good and makes
    nothing (click twice to confirm). Transfer (soft mode only) moves the enchantments from the
    source box (gear or enchanted book) onto the item in the target box, skipping enchantments the item can't take or that conflict.
    Every enchantment loses (3 - rank) levels; an enchantment that would drop below level 1 is never destroyed (strip refuses if
    nothing would survive). Code: EnchantTools (clickBox, strip, transfer).
  - Mending Gut (perk, 5 ranks, unlocks at stomach level 60): every 5 seconds, damaged gear and tools in the item storage repair
    2 durability per rank. Runs while the storage screen is closed, in both modes (note hard mode also breaks gear down over time).
  - Shared storage: a swallowed player presses the stomach key (N) to see the swallower's item storage, read-only, refreshing every 2
    seconds. Server switch: sharedStorageViewEnabled.
  - Hazard damage: swallowing sharp or hazardous items from the inventory (V key) hurts, and so does moving one between your inventory
    and the stomach storage screen (shift-click, number-key swap, or picking it up on one side and putting it down on the other;
    rearranging inside the storage is free). Damage is hazardSelfDamage (default 2 HP), never below 2 hearts, none in creative. There
    is no prompt. Swords, axes, tridents, shears and arrows always count; edit data/gulp/tags/items/stomach_hazardous.json for the rest.
  - Nested weighting: shulker boxes and bundles weigh their contents (each item like a stack in a slot, times nestedWeightMultiplier,
    default 1.0, on top of the box itself), so the item belly grows with what is inside. Code: Stomach.weightOf.
- Instant digest toggle: the Digest button next to each mob on the stomach screen is OFF by default. Each player turns it on in
  Settings > Toggles ("Instant digest button"). It is saved with the stomach (older saves start with it off) and the server refuses the digest
  action while it is off, not just the button. Hard-mode digestion over time is not affected. Code: Stomach.instantDigest, action 17.
- Right-click to eat: on the stomach storage screen, right-clicking an item in the storage (cursor empty, not the Enchant Stripper
  boxes) eats that exact item instead of only the first slot; holding right-click keeps eating one every 2 ticks after a short pause, and
  shift + right-click eats the whole stack (up to 64). It goes through the same rules as the Eat buttons (breakable gear is broken down,
  everything else counts toward Iron Gut). Client switch: "Right-click to eat" in Settings > Toggles (rightClickEat in config/gulp-client.properties);
  off gives the normal right-click back (picking up half a stack). The server action is 18 (StomachItems.digestSlot).
  The Eat 1 / Eat 64 buttons (first slot) now keep an item there: when the first slot is eaten empty (or was already empty), every item
  moves forward so the next one is in the first slot, in the same order, so spamming Eat 64 eats the whole storage
  (StomachItems.digestFirstSlot / shiftForward). Right-click eating does not move anything.
- Stomach contents are saved with the world. Dying releases everything.

## Item digestion
- Gear deconstruction (hard mode only): tools, weapons, armor and other damageable items in item storage are broken down, one every 3 seconds,
  into the ingredients of their crafting recipe (iron chestplate -> 8 iron ingots). Worn gear gives back less (about half when nearly broken).
  Gear with no crafting recipe (netherite, some modded items) is kept. Enchanted gear is kept unless deconstructEnchanted is true.
  Switch: itemDigestionEnabled.
- Trash (hard mode only): items in the tag gulp:stomach_trash dissolve (cobblestone, dirt, gravel, seeds ...). Edit
  data/gulp/tags/items/stomach_trash.json or add to it from a datapack. Switch: trashDissolveEnabled. Nothing else is ever destroyed.
- Eat buttons (item storage screen, top right): "Eat 1" and "Eat 64" always say "Eat", whatever the item is. They act on the item in the
  FIRST slot, in either mode. Gear that can be broken down (see above) is broken down into its parts instead of eaten, in either mode
  (it does not count toward Iron Gut). Every other item (food, blocks, junk, gear with no recipe) is digested by the
  Iron Gut perk below. Things that hold items (shulker boxes,
  bundles) and enchanted gear (unless deconstructEnchanted) are refused. The server checks that the storage screen is really open (action 10 in StomachLogic).
- Iron Gut / Universal Digestion (perk, 3 ranks, rank 0 is free and always active): every 32 items eaten with the buttons (any kind) restores
  hunger. Rank 0 = 0.5 hunger bars (1 point) per 32 items, rank 1 = 1.0, rank 2 = 1.5, rank 3 = 2.0 (4 points). The running count is saved in the
  stomach (Stomach.digestProgress, NBT "DigestProgress"), so it survives closing the screen, logging out and dying. "Eat 64" can pay out twice.
  Payouts restore hunger only (no saturation) and can't overfill the bar. The storage screen shows "Digestion Progress: X / 32 Items" under the
  window with a hover tooltip, and the perk wheel shows Current / Next payout. Server switch: universalDigestionEnabled. Code: StomachItems.digestFirstSlot.
- Nothing automatic happens while any container screen is open. Code: StomachItems.java.

## Visible belly
The belly of every player model swells forward as their stomach fills (using the player's own skin).
Other players see it too. Settings > Belly style switches between Classic (whole torso) and Stomach only.
Settings > Toggles > Action messages turns the short action-bar messages (Gulp! Swallowed Cow, Too big for your stomach, mode
changes, struggle warnings ...) on or off. Level-up messages are a separate setting. Messages telling a swallowed player
they were swallowed and how to get out are always shown.
Tweak MAX_EXTRA_DEPTH / MAX_EXTRA_WIDTH in BellyLayer.java for how far it sticks out, and
BELLY_TOP / BELLY_HEIGHT in BellyModel.java for the stomach-only shape.
Custom player model mods can read the fullness (0..1) from ClientState.bellyAmount(playerUuid).

## Sounds
The sounds are in src/main/resources/assets/gulp/sounds/ (.ogg) and wired up in sounds.json:
- Swallow: the 12 gulp*_*.ogg files (random each time) - Spit out: dropping1-3 (random) - Digestion (mob digests on its own): digestion1-3 (random)
- Digest button (stomach screen): digestion_button - Screen music (loops quietly, as background ambience, on the stomach,
  perks and settings screens): screen_loop. Its volume is VOLUME in ScreenLoopSound.java (0.0 - 1.0).
Settings > Sounds has a "Sounds: Custom / Minecraft" switch (Minecraft = the vanilla stand-in sounds), an ON/OFF
button and a 0% - 200% volume slider for each sound (100% is the middle; drag to hear a preview). Every player's
choice is their own (saved in config/gulp-client.properties).
Minecraft can't play a file louder than it was recorded, so each effect also has a "_loud" twin (+6 dB, exactly
double the amplitude) that the slider switches to above 100%. If you replace a sound, make its _loud twin too
(gain it by 6 dB through a soft limiter) and keep the same file name plus "_loud".
The Minecraft stand-in sounds can't go above 100%. The screen music is quieter by design: its 100% is BASE_VOLUME in
ScreenLoopSound.java.
To replace a sound, convert it to .ogg (mono for in-world sounds) and keep the same file name.

## Art hooks
- HUD is drawn with plain rectangles in GulpHud.java; perk icons are in PerksScreen.iconFor().

## Minecraft Comes Alive (MCA Reborn)
Optional: if MCA is installed, villagers react to what you do (nothing changes if it isn't). MCA is reached by name, not as
a build dependency, so the mod builds and runs the same either way. If MCA ever changes in a way that breaks this, the
reactions switch themselves off with one line in the log.
- Hearts and mood: swallowing a villager changes how it feels about you (MCA hearts and mood, with MCA's own particles).
  Hard mode: about -40 hearts (the villager ends up "depressed"); soft mode: about -10. Nearby villagers who see it react too
  (up to 12 within 16 blocks), and non-guard witnesses panic and run from you for a few seconds.
- Villagers who enjoy it: by MCA personality. Peppy, Confident, Athletic and Odd villagers enjoy being carried safely:
  +15 hearts and a happy mood on a soft swallow, and +5 up to +25 more when you let them out (the longer the ride, the
  more). Sensitive, Shy, Gloomy and Grumpy villagers hate it most (-25 soft, -50 hard). Everyone else dislikes it a little.
  Nobody likes hard mode, but fans mind it far less (-10).
- MCA has no "scared" or "angry" mood (its moods are depressed, sad, unhappy, passive, fine, happy, overjoyed), so fear shows as
  the lowest mood plus the panic behaviour.
- The belly and MCA's outfit: when MCA gives a player its villager look (its "villager" player model), the belly is drawn
  with MCA's own skin texture (with the villager's skin tone) and clothing texture, in MCA's order, so the outfit grows with
  the belly instead of the normal Minecraft skin poking through it. Players who aren't using MCA's villager look get the
  normal belly. (McaSkins.java; MCA's clothing sits further out than its skin, so the clothing pass uses slightly bigger meshes.)
- Digesting a villager also triggers MCA's own death rules (witnesses lose hearts, family grieves, a headstone may appear).
- Predator status: swallowing a villager adds to it (hard 15, soft 3; digesting adds about 25; fans count for less) and it
  fades by 1 every 20 seconds. It is shown on the stomach screen. At 30 or more, villagers who are afraid of you refuse to
  talk, trade or marry you ("X is too afraid of you to talk"), except villagers who enjoy it. Any villager whose hearts for
  you are -30 or lower also refuses while the status lasts. When it fades, everyone talks to you again.
- Server settings (gulp-server.toml): mcaIntegrationEnabled, mcaReactionMultiplier (0 = no reactions), mcaLockoutEnabled,
  mcaPredatorLockThreshold, mcaHeartsLockThreshold, mcaPredatorDecaySeconds, mcaVillagersPanic., mcaMemoryEnabled,
  mcaAffectionEnabled, mcaAffectionHearts, mcaRequestCooldownSeconds.
- Debug: /gulp villagerinfo (anyone) prints the nearest MCA villager's personality and hearts and every reason they are not asking to be carried.
- Villager traits (pred and prey villagers only): every pred or prey villager is either shy or confident, taken from their MCA
  personality (Shy, Sensitive, Gloomy, Grumpy = shy; Confident, Peppy, Athletic, Odd = confident; anyone else gets one from their
  UUID, so it never changes). Normal villagers have no trait. It shows on the MCA villager screen ("Type: Pred (shy)") and in
  /gulp villagerinfo. Confident preds offer comfort sooner (under 60% HP), need 80% of mcaAffectionHearts, ask again sooner and
  heal 15% faster. Shy preds offer later (under 40% HP), need 125% of the hearts, ask again later and hold you 15% longer. Each
  trait has its own lines for the comfort offer, the start and the end of the rest. Shy prey villagers ask to be carried rarely and
  need twice the hearts; confident prey ask often. Code: VillagerTrait.java, McaIntegration.traitOf. The network protocol is now "5".
- Villager perks (the Perks button on the MCA villager screen): pred villagers get one perk point for every level after the first
  (9 by level 10, exactly enough for every rank of the three perks) and spend them with the + buttons. Soothing Touch (3 ranks):
  +0.5 HP healed per second per rank. Long Embrace (3 ranks): +12 seconds of holding per rank. Warm Belly (3 ranks): feeds the
  player while held, `rank` hunger every 2 seconds. The level, XP, points and ranks are saved on each villager itself (their own
  data in that world's save), so every villager in every world has its own. The server checks every purchase (action 21). Code:
  VillagerPerk.java, VillagerHolding (rankOf, pointsFree, buyPerk). The network protocol is now "6".
- Trade prices and low affection: when you trade with an adult MCA villager, prices follow their hearts with you (VillagerTrade.java).
  Above mcaTradeDiscountStartHearts (default 40) you get a discount that grows to mcaTradeMaxDiscountPercent (20%) at
  mcaTradeDiscountFullHearts (100). Below mcaLowAffectionHearts (default -10) you pay extra, growing to mcaTradeMaxSurchargePercent
  (25%) 40 hearts lower. The villager says a line about it (shy and confident ones differently, at most once every 2 minutes).
  Pred villagers who don't like a hurt player (hearts at or below mcaLowAffectionHearts) say so instead of offering comfort.
  /gulp villagerinfo shows the current price change. Switch: mcaTradeEnabled. This works through vanilla's trade "special price",
  so MCA's own price rules (if any) still apply on top.
- Child protection (always on, even if mcaIntegrationEnabled is false, as long as MCA is installed): baby villagers and every MCA villager
  who is not an adult (baby, toddler, child, teen) can never be swallowed or digested. If one is somehow already inside (an old save), they
  are let out on the next tick. Vanilla baby villagers are protected too. Code: McaIntegration.isProtectedChild / isChildEntry.
- Affection: a villager with at least mcaAffectionHearts (default 60) hearts, who isn't afraid of you (not a "fearful" personality),
  may ask to be carried when they are near you (a check about every 5 seconds, then mcaRequestCooldownSeconds before they ask again).
  The chat message has [Accept] / [Decline] buttons (/gulp carry accept|decline). Only in soft mode, and only with a role that can
  swallow. An invited villager is marked Safe: they gain hearts instead of losing them, they can never be digested or dissolved (the
  Digest button refuses), and they don't make you "a predator". While inside, a villager with enough hearts says something every
  1 to 2 minutes (different lines for fans, neutral and fearful personalities, plus one for regulars).
- Memory: swallow, release, long rides and witnesses are written to the villager's own saved data and, best effort, into MCA's
  LongTermMemory as "<id>.<player uuid>" (ids: gulp.swallowed, gulp.swallowed.soft, gulp.swallowed.hard, gulp.invited,
  gulp.released, gulp.long_ride, gulp.witnessed.swallow, gulp.witnessed.digest, plus the milestones gulp.swallowed.many (3 rides), gulp.swallowed.lots (10) and gulp.invited.many (3 invited rides); none expire). Switch: mcaMemoryEnabled.
- Swallow talk menu ("Let's talk about the stomach"): data/gulp/dialogues/main.json merges one answer into MCA's own Talk menu
  (with "silent": true to match main and "constraints": "adult", so babies, toddlers, children and teens never get it). It opens a
  hub (gulp.talk.json) with five pages and 29 topics (plus the navigation buttons), each answer picking its reply from what the villager remembers
  (gulp.* memories: hard / soft / invited ride, many / lots of rides, long ride, witnessed a swallow or digestion), hearts, mood and
  personality group. Works without the Conversations add-on (the personality lines then fall back to the plain ones).
  Pages: memories (ride, first time, long ride, "do I carry you too much", saw, forgive), feelings (scared, trust, would you again,
  warm, heartbeat, mood today), play (guess what's in my stomach, jokes, fortune, dare, compliment, hide and seek: these pick a random
  reply every time), gossip (what people say, famous, fear, advice, strange news), ask (how to carry safely, want a ride, feel safe,
  promise, sorry, thanks). A few warm replies give 1 to 2 hearts through MCA's own "positive" action (with fatigue on).
  The text lives in lang/en_us.json as dialogue.gulp.* (generated from a script; each reply has 3 variants). To add more:
  add a topic to a page's answers array and its lang keys, using only the condition keys already used there.
- MCA: Conversations: data/gulp/dialogues/conversations.cat.events.json adds a "Remember the stomach ride?" topic to the Events page of the
  Conversations hub. The villager answers differently depending on those memories (survived hard mode, soft ride, saw a digestion, saw a
  swallow, or nothing). It uses only MCA's own "memory" condition, so without the add-on it is simply unused. The text is in lang/en_us.json
  (dialogue.conversations.gulp.*, three variants each).
- Class lookup now also finds MCA 7.7.1+ classes (net.conczin.mca) as well as net.mca and the forge. prefix.

## Custom Player Models (CPM) belly sliders
Optional: if Custom Player Models is installed, your model's belly can follow how full you are. (Nothing changes without CPM.)
It runs on your own client, so it works even if the server doesn't have CPM; CPM then shares it with other players the way it
shares any animation.
- For the model's author: in the CPM editor, add animations to the model as value layers (so they can sit anywhere from
  "empty" to "full") and give them these names:
    belly        how full you are overall (creatures, plus your item storage counting half as much)
    belly_mobs   how full of creatures you are (their volume compared to your capacity)
    belly_items  how full your item storage is
  Use any of them, or all three. Each is set between 0 and the maximum the model allows for that animation (the mod reads the
  maximum from the model, so any range works). An animation the model doesn't have is skipped. Values ease toward their
  target, so the belly grows and shrinks smoothly.
- Names can be changed in config/gulp-client.properties (cpmBellyName, cpmMobsName, cpmItemsName). Settings > Toggles > CPM belly
  sliders switches it off; Settings > Belly style > "Off (my model has one)" turns off the built-in bulge. If your own model
  has a "belly" animation, the built-in bulge steps aside for you automatically.
- Capital letters don't matter: CPM itself treats "Belly" and "belly" as different names, so the mod tries each name in a few
  spellings (belly, Belly, BELLY, Belly_Mobs, "Belly Mobs" ...) and also a few common alternatives (stomach, stomach_scale,
  belly_scale, tummy; stomach_mobs; stomach_items). CPM can't list a model's animations, so a slider with a completely
  different name still needs its name put in the config. The mod writes "found a CPM belly slider named ..." to the log when it finds one.
- How it's built: CPM finds the @CPMPlugin class (CpmPlugin) by itself. The few CPM signatures used are compiled against
  stand-ins in src/cpmApi (never put in the jar), so the mod builds without CPM and the real CPM classes are used at runtime.

## Swallowing system (Flamevore)
- Only matters when the Flamevore mod is installed. FlamevoreCompat finds it by the word "flamevore" in a mod's id or display
  name and writes what it found (or the list of installed mod ids) to latest.log. Without Flamevore nothing changes and Gulp does
  all the swallowing.
- With Flamevore, each player picks who does the swallowing on first join (SwallowChoiceScreen), and can switch later in
  Settings > Swallowing. It is saved per player on the server (Stomach.swallowSystem, action 19).
- Flamevore chosen: Gulp's mob and player eating (V), the Gulp belly growth and MCA carry offers are off. Item swallowing,
  stomach level, perks and item storage still work. Switching to Flamevore lets out anything Gulp was holding.
- Gulp chosen (or nothing chosen yet): Flamevore's eat key (X by default) is switched off by FlamevoreKeyBlock, which uses up
  its presses at the start of each client tick. The key it finds is logged as "[Gulp] Flamevore key" in latest.log.
- Until a player has chosen, V opens the choice screen. We never edit Flamevore: we only read what it does and react to it.
- The network protocol version is now "2", so a client and server on different Gulp versions refuse to connect.

## Settings screen
- Settings has Belly style, Role, Swallowing, Perk menu, Sounds... and Toggles... (SettingsScreen.java).
- Settings > Toggles (TogglesScreen.java) holds every ON/OFF switch: Level-up messages, Action messages, Role in name tags,
  Right-click to eat, CPM belly sliders, Instant digest button (server-saved, see below) and Hover help tips.
- Hover help tips (helpTooltips in config/gulp-client.properties, default ON) hides the explanatory hover text on Gulp's buttons and
  screens (Eat 1 / Eat 64, Strip / Transfer / Destroy, the Digestion Progress payout, the empty Enchant Stripper boxes, and the
  Settings help). Real item tooltips are never hidden. With it OFF, the "Needs the Enchant Stripper perk" hint on greyed-out buttons is
  hidden too. Code: GulpConfig.helpTooltips, ClientScreenEvents.tip().

## Multiplayer
- Everyone (server and every player) needs the mod installed. It is Forge 1.20.1 and works on dedicated servers.
- Each player has their own stomach, saved with the world. Other players see your belly and hear your sounds.
- Settings (belly style, sounds, volumes, perk menu and everything under Toggles) are per player and saved on
  that player's computer.
- Pets are protected: you can't swallow another player's tamed animal or horse.
- Dead and spectating players can't use the stomach; if you die, everything inside is let out.
- Server admins can edit serverconfig/gulp-server.toml (in the world folder) to: turn hard mode / digesting off
  (hardModeEnabled), scale struggle damage (struggleDamageMultiplier, 0 = never hurts), and scale XP (xpMultiplier), turn item storage off (itemStorageEnabled), turn player swallowing off
  (playerSwallowingEnabled) and set the longest a player can be held (playerMaxHoldSeconds).

## Mod info
Name: Vore Mod. Description: This Mod adds Vore to your world with progression, perks and capacity to carry mobs
wherever you want. The icon (src/main/resources/logo.png, also pack.png) is a pixel-art drumstick, the hunger icon.
Replace logo.png with your own 256x256 art any time.

- Villager storage (pred villagers): the Perks screen of an adult pred MCA villager has a Storage button (action 22,
  VillagerStorage.java) that opens a chest-style screen of that villager's own item storage, saved on the villager. It has
  1 row at level 1-2, 2 rows at 3-5, 3 rows at 6-8 and 4 rows (36 slots) at level 9-10. The villager must not be cold to you
  (hearts at or above mcaLowAffectionHearts). Everyone who opens the same villager shares one container, and the items drop
  where the villager dies. Switch: mcaVillagerStorageEnabled.

- Villager temper in dialogue and reactions: the first time you talk to an adult pred or prey villager they get a memory
  of their kind (gulp.kind.pred.shy / pred.confident / prey.shy / prey.confident). The new "Tell me about yourself" page
  in the Gulp talk menu (gulp.talk.nature.json) reads it, so a shy pred, a confident prey and so on each answer differently
  (normal villagers get a default answer). Witness reactions use the same temper: pred and prey villagers who see a swallow
  react as shy (frightened) or confident (a fan) villagers, preds are less shocked, preys feel more for the victim, and a
  confident pred does not panic. Normal villagers keep using their MCA personality.

- Villager stomach capacity and perk slots: a pred villager holds 1 player at once, 2 from level 5, plus 1 per rank of the new
  Roomy Belly perk (never more than 6). Only a few different perks fit at once (perk slots: 1 at levels 1-3, 2 at 4-7, 3 from
  level 8); a villager who already has more perks than slots keeps them, they just can't learn a new one. There are now 5
  villager perks (14 ranks) for 9 points at level 10, so every villager is a choice. New perk Second Wind (active): when a comfort
  ends well the player gets Regeneration for 5 seconds per rank. The Perks screen shows slots and capacity. Network protocol is now 7.
- Cosy cuddles (mcaCuddleEnabled): a pred villager who really likes you may offer a cuddle in their tummy even when you are not
  hurt. Needs hearts (1.5 times the comfort amount the first time, the plain amount once they have held or carried you before,
  scaled by shy/confident), no bad history (a villager who saw you digest someone never offers), a player who is Prey or Both, and
  a free spot in their tummy. Shy preds ask less often, at most one offer per player every 10 minutes, the player must press
  Accept (/gulp cuddle accept|decline) and can climb out any time. A cuddle lasts 33 seconds at level 1 up to a minute at level 10.

- Perks for every kind of adult villager (VillagerPerk.forType): preds keep their five (Soothing Touch, Long Embrace, Warm Belly, Roomy
  Belly, Second Wind); prey villagers get Eager Rider (asks more often, needs fewer hearts), Snug Passenger (+3 hearts per rank after
  every safe ride) and Parting Gift (XP for the player after a 30 s+ ride, once per 10 minutes per villager); normal villagers get
  Haggler (5% off trades per rank), Chatty (+1 XP per rank when you talk) and Warm Welcome (+1 heart per rank when you talk).
  Same slots and points for everyone. Network protocol is now 8.
- The Perks button hides on MCA's Talk and Interact pages (they have a Back button) and each change of page is logged once in
  latest.log as "[Gulp] MCA villager screen is now: ...", so a wrong guess can be fixed from the log.

- Comfort offers have no waiting any more: a hurt player (below 50% HP, 60% for confident preds, 40% for shy ones) is asked straight
  away by any pred villager within 6 blocks who doesn't dislike them (hearts above mcaLowAffectionHearts; a villager at or below it
  refuses and says so). The only pause is mcaComfortDeclinePauseSeconds (default 30) after you press Decline or climb out early.
- Villager data (level, XP, perks, item storage, memories of players) is now saved in the world under each villager's own UUID
  (VillagerData.java, saved as gulp_villagers) instead of in the entity's persistent data, because MCA villagers could share that
  data and levelling one levelled all of them. Villagers start fresh after this update (the old data was not copied, because it
  was the shared kind). Take items out of villager storage before updating a world you care about.

- Fixed: the Perks screen sent the perk number in 2 bits, which only fits 4 perks. With 11 perks, clicking Second Wind bought Soothing
  Touch (on the villager with the next entity id!) and the prey and normal perks were refused. It is now 4 bits (action 21 = entity
  id << 4 | perk number, up to 16 perks).
- Storage: a villager who dislikes you refuses to open their storage with their own line (VillagerTrait.storageRefuseLine) instead
  of the comfort snub.
- Trade: the villager is now taken from the trading menu itself (found by field type), with the old lookup as a fallback, and
  latest.log says why prices were normal ("[Gulp] Trade with X: N hearts is in the normal price range ..." or "could not tell
  which villager"). Discounts start above mcaTradeDiscountStartHearts (default 40), surcharges below mcaLowAffectionHearts (-10).

- Russian doll: when a player swallows a villager who is holding a player (comfort or cuddle), the player inside comes along. The
  stomach needs room for the villager AND everyone inside (the size is added up, and the swallower's role must be able to eat and
  playerSwallowingEnabled must be on). The player inside stays held by the villager and looks through the swallower's eyes. The
  healing / cuddle is paused while the villager is swallowed (no healing, no feeding, the timer stops) and carries on from the same point when the villager is let out. The stomach screen shows the villager as
  "Name (Steve player)". If such a villager is digested (hard mode or the Digest button), the player inside is not harmed: they are passed on into the swallower's stomach as an ordinary soft held player (never hard mode, since they only agreed to be held by the villager), with a message to both. They can press Release (G) there as usual.
  The player inside can always press Release (G) to climb out of both and appear beside the swallower, and their room is given back to the stomach and the swallower is told. When the swallower lets the
  villager out, the player stays in the villager, who carries on holding them in the world. If the swallower logs out, dies, or the
  server restarts, the player is let out safely. Villagers also say a line about it (VillagerTrait.swallowedWhileHoldingLine).
  Switch: nestedSwallowEnabled; with it off the held player simply tumbles out beside the villager when it is swallowed.
- Children and teens: the villager screen shows no Perks button, no Type box and no level bar for them. The info packet now says
  whether the villager is an adult (network protocol 9).

## Climb In, outcomes by villager kind, and asking a villager to become a pred

- **Climb In** (Perks screen, any adult villager, role Prey or Both, soft only): you force yourself into the villager's tummy whether
  they are a pred, a prey or a normal villager. Settings: mcaForceSwallowEnabled, mcaForceSwallowCooldownSeconds (default 120). You can
  always get out with Release (G).
- **What happens** depends on the base type and the temper (shy / confident; normal villagers use their MCA personality, in between =
  easygoing). Code: ForcedOutcome.of. Losses are halved with a villager who likes you (mcaAffectionHearts) and 1.5x with one who is
  cold to you (mcaLowAffectionHearts).

  | Villager | Hearts | They put up with you | Healing | Extra |
  |---|---|---|---|---|
  | Pred, confident | +4 | normal cuddle | full | normal cuddle rewards |
  | Pred, shy | +1 | normal cuddle | full | normal cuddle rewards |
  | Prey, confident | -8 | 20 s | half | squirming costs hearts |
  | Prey, shy | -12 | 10 s | none | runs off in a panic afterwards |
  | Normal, confident | -5 | 25 s | half | |
  | Normal, shy | -9 | 12 s | none | runs off in a panic afterwards |
  | Normal, easygoing | -6 | 20 s | half | |

  Every outcome has its own lines when you climb in, while you are inside (every ~20 s), when they let you out and when you climb out
  early. Preds also have new idle lines while they hold you in comfort and cuddles.
- **Ask to be a Pred** (Perks screen, prey and normal adult villagers): if the villager has enough hearts with you they agree and
  become a pred for good (mcaConvertEnabled, mcaConvertHearts default 80; shy x1.25, confident x0.8). They get the pred perk list; the
  perks they had are forgotten and the points come back. Otherwise they refuse in their own voice and the chat tells you how many
  hearts you need. The change is saved under the villager (VillagerData key gulp_kind).
- New actions: 24 Climb In, 25 Ask to be a Pred (arg = villager entity id).

## Angry preds, and the menu closing when you are swallowed

- When a villager swallows you (Climb In, comfort, cuddle or an angry grab) the MCA villager menu and the Perks screen now close by
  themselves; before, they stayed open on top of the villager's-eye view.
- **Angry grab:** a pred villager (not prey or normal ones) whose hearts for a player are at or below mcaLowAffectionHearts (shy
  preds: twice as low) may swallow that player without asking, if the player's role is Prey or Both and they are within 5 blocks.
  Checked every 5 seconds; confident preds do it more readily. It gives no healing. The Release key does not work:
  the player has to struggle (Struggle key), with the same chance as struggling out of a player (playerStruggleChance, a little lower
  for a higher-level villager), and if they are still inside after mcaHostileHoldSeconds (45) the villager digests them (see below). The HUD shows "[ANGRY]" with the
  chance. Not creative or spectator players. Settings: mcaHostileSwallowEnabled, mcaHostileCooldownSeconds (300, per player and per
  villager), mcaHostileHoldSeconds.
- New voice lines for it (VillagerTrait.hostile*): the grab, idle lines while they hold you, a failed struggle, breaking free, being
  let go on their own, and refusing your Release key; each in a shy, a confident and an easygoing version.
- **Digested for not fighting back** (mcaHostileKills, default on): when the time runs out and the player is still inside, the villager
  digests them. The player dies, their whole inventory (main, armour, off-hand) goes into the villager's storage (what does not fit
  drops at the villager; with villager storage turned off everything drops there), and the villager says a line like "well, now it's
  my stuff". Warnings in chat at half time and 10 seconds before the end; the HUD says "Break free in time, or they will digest you!".
  Only if the player's role still allows being eaten; totems of undying don't help. With the setting off the villager just lets go.

## More items break down, and stomach items on death with grave mods

- **Deconstruction** (hard mode and the Eat buttons) now also covers non-damageable items listed in the tag `gulp:stomach_deconstruct`:
  anvils (3 iron blocks + 4 iron ingots; chipped and damaged ones give less), lodestone (8 chiseled stone bricks + a netherite ingot),
  cauldron, hopper, bucket, compass, clock and minecart. Items with no recipe in vanilla have built-in parts: saddle (5 leather) and
  horse armor (7 leather / iron ingots / gold ingots / diamonds), and netherite tools and armor (made at a smithing table in vanilla), which break into the diamond piece + a netherite ingot (the diamond piece can then be broken down again). Add more items to the tag with a datapack
  (data/gulp/tags/items/stomach_deconstruct.json).
- **Death and grave mods:** the stored stomach items are first moved into the player's inventory when they die (before grave mods look
  at it), so they go wherever the rest of the player's things go; what does not fit is added to the death drops. Before, they were
  dropped on the ground separately, which grave mods did not collect. With keepInventory on nothing changes: they stay in the stomach.
- **Grave mod compatibility, broader:** the stomach items now move into the player's inventory at the lethal hit already (LivingDamageEvent,
  highest priority, skipped when a totem saves the player) and again at the death event, so they are in the inventory whenever a grave mod
  reads it. Whatever does not fit is added to the death drops (highest priority) for mods that collect the drops. Works with any grave
  mod that collects the player's inventory or drops the normal way. With keepInventory on nothing changes.

## CPM belly animation resetting (Flamevore)

- When a player's swallowing system is **Flamevore**, Gulp no longer sets any animation on their Custom Player Model. Before, Gulp could
  find the same belly animation Flamevore drives (the names belly, stomach ... are tried), set it to flat while Gulp's own stomach was
  empty, and re-send it every 10 seconds, so the belly went flat, played once, and went flat again.
- In Gulp mode: an animation that is never started is no longer sent a 0 (nothing to switch off); plain on/off belly animations are on
  whenever anything is in the stomach instead of flipping at 50% (each flip restarted it); and the 10-second re-send only happens for real
  sliders above zero.

## Perks button on married villagers

- The Perks button was only created when the MCA villager menu had a gap in its right-hand button column (above Family Tree). A married
  villager's menu has a different set of buttons, so there was no gap and the button never appeared. It is now always created and shown on
  every page of the menu that has no Back button (the main page), whoever the villager is.

## Four more places to hold prey: Breast, Cock, Unbirth, Tail

- **Where it goes:** the stomach screen has a tab per place (Stomach, Breast, Cock, Unbirth, Tail). The tab you pick is where the next thing you
  swallow goes (mobs, villagers and players), and its tab shows what is in it. The HUD shows the place's name, its soft / hard setting and its fill.
  Each place holds its own prey and has its own room (as big as the stomach); the stomach is still what makes the belly grow. Older saves
  are all in the stomach. Setting: multiOrgansEnabled (default on; off = everything goes to the stomach).
- **Independent soft / hard:** every place has its own switch (the Soft mode / Hard mode button on the screen changes the tab you are on, the
  hard-mode key changes the place you swallow into), so the stomach can be soft while the cock is hard. Prey in a hard place is digested
  as before (mobs); a player keeps the mode they were swallowed under. The server's hardModeEnabled still has the last word.
  Stored in Stomach.hard (stomach) and Stomach.hardOrgans, per entry "Organ" (Organ.java).
- **Tail:** prey spend tailTransitSeconds (30) in the tail, shown as a blue bar, and then move to the stomach. If the stomach has no room they
  wait in the tail and you are told.
- **Unbirth:** when prey struggles you get nausea for 1 to unbirthNauseaMaxSeconds (2) seconds. Mobs squirm now and then, even in soft mode;
  a swallowed player doing Struggle (R) causes it too (soft wall rub or hard struggle). The nausea goes to the one carrying them.
- Network protocol is now 10: everyone has to update the mod together (server and clients).
