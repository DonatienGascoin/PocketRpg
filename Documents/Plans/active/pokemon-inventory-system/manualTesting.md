Demo Scene Setup — Step by Step

1. Player Entity (already exists in DemoScene)

Select Player in the hierarchy, add these 3 components:

┌──────────────────────────┬──────────┬─────────────┐
│        Component         │ Category │   Config    │
├──────────────────────────┼──────────┼─────────────┤
│ PlayerPartyComponent     │ Player   │ None needed │
├──────────────────────────┼──────────┼─────────────┤
│ PokemonStorageComponent  │ Player   │ None needed │
├──────────────────────────┼──────────┼─────────────┤
│ PlayerInventoryComponent │ Player   │ None needed │
└──────────────────────────┴──────────┴─────────────┘

2. Professor NPC — gives starter Pokemon + items

Create a new GameObject "Professor":

┌─────────────────────────────────────┬────────────────────────────────────────────────────┐
│              Component              │                       Config                       │
├─────────────────────────────────────┼────────────────────────────────────────────────────┤
│ Transform                           │ Place near the player spawn                        │
├─────────────────────────────────────┼────────────────────────────────────────────────────┤
│ SpriteRenderer                      │ Any NPC sprite                                     │
├─────────────────────────────────────┼────────────────────────────────────────────────────┤
│ TriggerZone                         │ 1x1, playerOnly                                    │
├─────────────────────────────────────┼────────────────────────────────────────────────────┤
│ DebugStarterGiver (category: Debug) │ speciesId: pikachu, level: 5, giveStarterKit: true │
└─────────────────────────────────────┴────────────────────────────────────────────────────┘

Console on interact:
[Debug] Gave pikachu Lv.5 to player (party size: 1)
[Debug] Gave starter kit: 5 Potions, 5 Poke Balls, 3000 money

Result: OK

3. Rival Trainer NPC — battle with full logging

Create a new GameObject "Rival":

┌──────────────────────────────────────┬──────────────────────────────────────────────────────────────────────────────────────┐
│              Component               │                                        Config                                        │
├──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ Transform                            │ Place somewhere walkable                                                             │
├──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ SpriteRenderer                       │ Any NPC sprite                                                                       │
├──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ PersistentId                         │ ID: trainer_rival                                                                    │
├──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ TriggerZone                          │ 1x1, playerOnly                                                                      │
├──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ TrainerComponent (category: Pokemon) │ trainerName: Blue, partySpecs: [{speciesId: charmander, level: 8}], defeatMoney: 500 │
├──────────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────┤
│ DebugBattleTrigger (category: Debug) │ No config needed                                                                     │
└──────────────────────────────────────┴──────────────────────────────────────────────────────────────────────────────────────┘

Result : OK

Expected console on interact:
=== TRAINER BATTLE: vs Blue ===
Player party (1):
- pikachu Lv.5  HP: 20/20
Blue's party (1):
- charmander Lv.8  HP: 24/24
Prize money: 500
Awarded 500 money to player
Blue defeated!
Transitioning to Battle scene...
Then the Battle scene loads, BattlePlayer logs your full party details, and pressing INTERACT returns you to the overworld at your saved position.

4. Dialogue Rewards — give items, give Pokemon, heal party

These tests use the DialogueEventListener component with the new GIVE_ITEM, GIVE_POKEMON, and HEAL_PARTY reactions. Each test requires a dialogue that fires a custom event.

4a. Give Items via Dialogue

Create a new GameObject "ItemReward_Pokeballs":

┌─────────────────────────────────────────┬──────────────────────────────────────┐
│               Component                 │               Config                 │
├─────────────────────────────────────────┼──────────────────────────────────────┤
│ DialogueEventListener (category: Dialogue) │ eventName: GIVE_STARTER_POKEBALLS │
│                                         │ reaction: GIVE_ITEM                  │
│                                         │ itemId: pokeball                     │
│                                         │ quantity: 5                          │
└─────────────────────────────────────────┴──────────────────────────────────────┘

Set up an NPC with a DialogueInteractable whose dialogue has a line with onCompleteEvent = "GIVE_STARTER_POKEBALLS".

Expected: Interact with NPC → dialogue plays → line completes → 5 Poke Balls added to inventory.
Verify: Check console for no warnings. Check inventory has 5 pokeball entries.

4b. Give Pokemon via Dialogue

Create a new GameObject "PokemonReward_Starter":

┌─────────────────────────────────────────┬──────────────────────────────────────┐
│               Component                 │               Config                 │
├─────────────────────────────────────────┼──────────────────────────────────────┤
│ DialogueEventListener (category: Dialogue) │ eventName: GIVE_STARTER_POKEMON  │
│                                         │ reaction: GIVE_POKEMON               │
│                                         │ speciesId: bulbasaur                 │
│                                         │ level: 5                             │
└─────────────────────────────────────────┴──────────────────────────────────────┘

Set up an NPC with a DialogueInteractable whose dialogue has a line with onCompleteEvent = "GIVE_STARTER_POKEMON".

Expected: Interact with NPC → dialogue plays → line completes → Bulbasaur Lv.5 added to party.
Verify: Party inspector shows new Bulbasaur. OT name matches player name.

4c. Give Pokemon with Full Party

Fill the party to 6 members first (use DebugStarterGiver or repeat 4b with different events), then trigger another GIVE_POKEMON event.

Expected: Pokemon goes to PC storage via depositToFirstAvailable().
Verify: Party stays at 6. PC storage inspector shows the new Pokemon in the first available box.

4d. Replay Guard — No Duplicate Rewards

After completing 4a or 4b, leave the scene and return (or save/reload).

Expected: The event is marked as fired in DialogueEventStore. On scene reload, onStart() calls onDialogueEvent() but the replay guard early-returns without adding duplicate items/Pokemon.
Verify: Inventory count and party size unchanged after reload.

4e. Heal Party via Dialogue

Create a new GameObject "HealReward":

┌─────────────────────────────────────────┬──────────────────────────────────┐
│               Component                 │             Config               │
├─────────────────────────────────────────┼──────────────────────────────────┤
│ DialogueEventListener (category: Dialogue) │ eventName: HEAL_PARTY         │
│                                         │ reaction: HEAL_PARTY             │
└─────────────────────────────────────────┴──────────────────────────────────┘

Set up an NPC (e.g. Nurse) with a DialogueInteractable whose dialogue fires the "HEAL_PARTY" event.

Expected: Interact → dialogue plays → event fires → all party Pokemon fully healed (HP, status, PP).
Verify: Console shows "Healed N Pokemon in party". Party inspector shows full HP on all members.
Note: Unlike GIVE_ITEM/GIVE_POKEMON, HEAL_PARTY has no replay guard — healing is idempotent and safe to repeat.

5\. Item Pickup (optional)

Create a new GameObject "Potion Pickup":

┌────────────────────────────────────┬─────────────────────────────┐
│             Component              │           Config            │
├────────────────────────────────────┼─────────────────────────────┤
│ Transform                          │ Place on the ground         │
├────────────────────────────────────┼─────────────────────────────┤
│ SpriteRenderer                     │ Item sprite                 │
├────────────────────────────────────┼─────────────────────────────┤
│ PersistentId                       │ ID: pickup_potion_1         │
├────────────────────────────────────┼─────────────────────────────┤
│ TriggerZone                        │ 1x1, playerOnly             │
├────────────────────────────────────┼─────────────────────────────┤
│ ItemPickup (category: Interaction) │ itemId: potion, quantity: 3 │
└────────────────────────────────────┴─────────────────────────────┘

6\. Shopkeeper NPC — buy/sell items

Create a new GameObject "Shopkeeper":

┌──────────────────────────────────────┬──────────────────────────────────────┐
│              Component               │                Config                │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ Transform                            │ Place somewhere walkable             │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ SpriteRenderer                       │ Any NPC sprite                       │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ ShopComponent (category: Interaction)│ shopId: viridian_pokemart            │
└──────────────────────────────────────┴──────────────────────────────────────┘

Note: TriggerZone is auto-added via @RequiredComponent on InteractableComponent.

Console on interact:
=== SHOP: Viridian City Pokemart ===
Player money: 3000
  Potion - 200 money (stock: unlimited)
  Poke Ball - 200 money (stock: unlimited)
  Antidote - 100 money (stock: unlimited)
  Repel - 350 money (stock: 10)

The shop data is loaded from gameData/assets/data/shops/shops.shops.json.
Edit the JSON to change shop contents; hot-reload is supported.

Suggested Play Order

1. Talk to Professor → get Pikachu + starter kit (gives 3000 money)
2. Talk to Professor again → get a second Pokemon (try bulbasaur — change speciesId in inspector)
3. Walk to Rival → battle triggers, logs both parties, transitions to Battle scene
4. Press INTERACT in Battle scene → returns to overworld at saved position
5. Talk to Rival again → nothing happens (defeated, persisted via ISaveable)
6. Talk to Nurse (dialogue reward) → heals party via HEAL_PARTY event
7. Talk to NPC that gives items (dialogue reward) → Poke Balls added to inventory via GIVE_ITEM event
8. Talk to NPC that gives Pokemon (dialogue reward) → Bulbasaur added to party via GIVE_POKEMON event
9. Leave scene and return → verify no duplicate rewards (replay guard)
10. Pick up the potion → adds to inventory
11. Talk to Shopkeeper → logs shop inventory and player money to console