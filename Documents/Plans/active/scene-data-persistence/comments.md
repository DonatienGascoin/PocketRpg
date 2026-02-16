# Persistence review :

-> PlayerStateTracker: Issue is that the class will need to be updated for every new component added. 
-> SceneManager Orchestration
Having game mechanics in the SceneManager
It makes the SceneManager too complex and it is not the right place for it. The SceneManager should only be responsible for managing scenes, not game mechanics.

Which mean, teleporting and spawn points should not belong to the scene manager.

Possible solution ? SceneManager hook on event ?

-> Phase 6 should not be deferred
Note: It is a very small change to DemoScene to do, manual one is enough

-> Phase 7 should not be deferred

Missing:
- Acceptance criteria
- Testing (unit tests, existing and new and manual)
- Documentation (claude docs and encyclopedia)



