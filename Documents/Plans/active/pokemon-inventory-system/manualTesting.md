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

4. Nurse NPC — heal zone

Create a new GameObject "Nurse":

┌───────────────────────────────────────────┬────────────────────────┐
│                 Component                 │         Config         │
├───────────────────────────────────────────┼────────────────────────┤
│ Transform                                 │ Place it somewhere     │
├───────────────────────────────────────────┼────────────────────────┤
│ SpriteRenderer                            │ Any NPC sprite         │
├───────────────────────────────────────────┼────────────────────────┤
│ HealZoneComponent (category: Interaction) │ TriggerZone auto-added │
└───────────────────────────────────────────┴────────────────────────┘

Result: OK, though it will need to be replaced/refactor once the dialogue reward is done

5. Item Pickup (optional)

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

6. Shopkeeper NPC — buy/sell items

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
6. Talk to Nurse → heals party
7. Pick up the potion → adds to inventory
8. Talk to Shopkeeper → logs shop inventory and player money to console