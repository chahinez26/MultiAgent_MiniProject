"""
Part 4 — Centralized Planning for Distributed Plans
=====================================================
Based on Chapter 3 of Multi-Agent Systems (MAS).

CONCEPT
-------
A central planner decomposes a global goal into sub-goals, assigns them
to individual agents, and coordinates their execution.

Architecture:
  - CentralPlanner: breaks down goals, allocates tasks to agents
  - Agent          : executes assigned sub-plans
  - Environment    : shared world state

Algorithm:
  1. CentralPlanner receives the global goal G.
  2. Planner builds a global plan P using STRIPS/AIMA planning.
  3. Planner partitions P into agent-specific sub-plans {P1, P2, ..., Pk}
     based on which agent can perform each action.
  4. Planner resolves inter-agent dependencies (synchronisation points).
  5. Each agent executes its sub-plan; global state is updated.

"""

from dataclasses import dataclass, field
from typing import List, Dict, Set, Tuple, Optional
from copy import deepcopy
from collections import defaultdict
import random


# ══════════════════════════════════════════════════════════════════════════════
#  World Model
# ══════════════════════════════════════════════════════════════════════════════

@dataclass
class Action:
    """A STRIPS action."""
    name:       str
    agent:      str        # which agent can perform this
    precond:    Set[str]   # required world state literals
    add_eff:    Set[str]   # literals added to world state
    del_eff:    Set[str]   # literals removed from world state

    def applicable(self, state: Set[str]) -> bool:
        return self.precond.issubset(state)

    def apply(self, state: Set[str]) -> Set[str]:
        return (state - self.del_eff) | self.add_eff

    def __repr__(self):
        return f"[{self.agent}] {self.name}"


@dataclass
class SyncPoint:
    """Synchronisation constraint between two agents."""
    before_agent:  str
    before_action: str
    after_agent:   str
    after_action:  str

    def __repr__(self):
        return (f"SYNC: {self.before_agent}.{self.before_action} "
                f"→ {self.after_agent}.{self.after_action}")


@dataclass
class SubPlan:
    """A plan fragment assigned to one agent."""
    agent:    str
    actions:  List[Action] = field(default_factory=list)


# ══════════════════════════════════════════════════════════════════════════════
#  Simple Forward STRIPS Planner (Breadth-First)
# ══════════════════════════════════════════════════════════════════════════════

def strips_plan(initial: Set[str], goal: Set[str],
                actions: List[Action]) -> Optional[List[Action]]:
    """
    BFS forward planner.
    Returns a sequence of actions from initial to goal, or None.
    """
    from collections import deque

    queue = deque([(frozenset(initial), [])])
    visited = {frozenset(initial)}

    while queue:
        state, plan = queue.popleft()

        if goal.issubset(state):
            return plan

        if len(plan) > 15:   # depth limit
            continue

        for action in actions:
            if action.applicable(state):
                new_state = action.apply(set(state))
                fs = frozenset(new_state)
                if fs not in visited:
                    visited.add(fs)
                    queue.append((fs, plan + [action]))

    return None


# ══════════════════════════════════════════════════════════════════════════════
#  Central Planner
# ══════════════════════════════════════════════════════════════════════════════

class CentralPlanner:
    """
    Computes a global plan and decomposes it into per-agent sub-plans
    with explicit synchronisation points.
    """

    def __init__(self, agents: List[str], actions: List[Action]):
        self.agents  = agents
        self.actions = actions

    def plan_and_decompose(self, initial: Set[str], goal: Set[str]):
        print("╔══════════════════════════════════════════════════════╗")
        print("║          CENTRAL PLANNER — Decomposition             ║")
        print("╚══════════════════════════════════════════════════════╝")
        print(f"Initial state : {sorted(initial)}")
        print(f"Goal          : {sorted(goal)}")
        print(f"Agents        : {self.agents}\n")

        # ── Step 1: Compute global plan ──────────────────────────────────
        global_plan = strips_plan(initial, goal, self.actions)
        if not global_plan:
            print("✗ No global plan found!")
            return None, None

        print("── Global Plan ─────────────────────────────────────────")
        for i, a in enumerate(global_plan):
            print(f"  {i+1}. {a}")

        # ── Step 2: Partition by agent ───────────────────────────────────
        sub_plans: Dict[str, SubPlan] = {ag: SubPlan(ag) for ag in self.agents}
        for action in global_plan:
            sub_plans[action.agent].actions.append(action)

        print("\n── Sub-plans by Agent ──────────────────────────────────")
        for ag, sp in sub_plans.items():
            if sp.actions:
                print(f"  {ag}:")
                for a in sp.actions:
                    print(f"    • {a.name}")

        # ── Step 3: Identify synchronisation points ──────────────────────
        syncs = self._find_sync_points(global_plan, sub_plans)

        print("\n── Synchronisation Points ──────────────────────────────")
        if syncs:
            for s in syncs:
                print(f"  {s}")
        else:
            print("  (none — agents are independent)")

        return sub_plans, syncs

    def _find_sync_points(self, global_plan: List[Action],
                          sub_plans: Dict[str, SubPlan]) -> List[SyncPoint]:
        """
        A sync point is needed when action Ai (by agent A) produces a
        literal required by action Bj (by agent B ≠ A), where Ai comes
        before Bj in the global plan.
        """
        syncs = []
        for i, producer in enumerate(global_plan):
            for j, consumer in enumerate(global_plan):
                if j <= i:
                    continue
                if producer.agent == consumer.agent:
                    continue
                # Check if producer establishes something consumer needs
                shared = producer.add_eff & consumer.precond
                if shared:
                    syncs.append(SyncPoint(
                        before_agent=producer.agent,
                        before_action=producer.name,
                        after_agent=consumer.agent,
                        after_action=consumer.name
                    ))
        return syncs


# ══════════════════════════════════════════════════════════════════════════════
#  Agent Executor
# ══════════════════════════════════════════════════════════════════════════════

class AgentExecutor:
    """Simulates an agent executing its sub-plan in a shared world."""

    def __init__(self, name: str, sub_plan: SubPlan):
        self.name     = name
        self.sub_plan = sub_plan

    def execute(self, world_state: Set[str],
                sync_barrier: Dict[str, bool]) -> Set[str]:
        print(f"\n  [{self.name}] Starting execution...")
        for action in self.sub_plan.actions:
            # Wait for sync if this action depends on another agent
            print(f"  [{self.name}] Executing: {action.name}")
            if not action.applicable(world_state):
                print(f"  [{self.name}] ⚠ Preconditions not met! "
                      f"Missing: {action.precond - world_state}")
                print(f"  [{self.name}] Waiting for sync...")
                # In a real system this would block; here we just report
            else:
                world_state = action.apply(world_state)
                print(f"  [{self.name}] ✓ State after: {sorted(world_state)}")
            sync_barrier[action.name] = True
        print(f"  [{self.name}] Sub-plan complete.")
        return world_state


# ══════════════════════════════════════════════════════════════════════════════
#  Example 1: Logistics — Two trucks, three cities
# ══════════════════════════════════════════════════════════════════════════════

def logistics_example():
    """
    World: Truck1 in CityA, Truck2 in CityB.
    Package1 at CityA → must reach CityC.
    Package2 at CityB → must reach CityC.
    Trucks can drive between adjacent cities.
    """
    print("\n" + "═"*60)
    print("  EXAMPLE 1: Logistics Domain")
    print("═"*60)

    initial = {
        "At(Truck1,CityA)", "At(Truck2,CityB)",
        "At(Pkg1,CityA)",   "At(Pkg2,CityB)",
        "Road(CityA,CityC)", "Road(CityB,CityC)",
    }
    goal = {"At(Pkg1,CityC)", "At(Pkg2,CityC)"}

    actions = [
        # Truck1 actions
        Action("Load(Pkg1,Truck1,CityA)", "Truck1",
               {"At(Pkg1,CityA)", "At(Truck1,CityA)"},
               {"In(Pkg1,Truck1)"}, {"At(Pkg1,CityA)"}),
        Action("Drive(Truck1,CityA,CityC)", "Truck1",
               {"At(Truck1,CityA)", "Road(CityA,CityC)"},
               {"At(Truck1,CityC)"}, {"At(Truck1,CityA)"}),
        Action("Unload(Pkg1,Truck1,CityC)", "Truck1",
               {"In(Pkg1,Truck1)", "At(Truck1,CityC)"},
               {"At(Pkg1,CityC)"}, {"In(Pkg1,Truck1)"}),

        # Truck2 actions
        Action("Load(Pkg2,Truck2,CityB)", "Truck2",
               {"At(Pkg2,CityB)", "At(Truck2,CityB)"},
               {"In(Pkg2,Truck2)"}, {"At(Pkg2,CityB)"}),
        Action("Drive(Truck2,CityB,CityC)", "Truck2",
               {"At(Truck2,CityB)", "Road(CityB,CityC)"},
               {"At(Truck2,CityC)"}, {"At(Truck2,CityB)"}),
        Action("Unload(Pkg2,Truck2,CityC)", "Truck2",
               {"In(Pkg2,Truck2)", "At(Truck2,CityC)"},
               {"At(Pkg2,CityC)"}, {"In(Pkg2,Truck2)"}),
    ]

    agents = ["Truck1", "Truck2"]
    planner = CentralPlanner(agents, actions)
    sub_plans, syncs = planner.plan_and_decompose(initial, goal)

    if sub_plans:
        print("\n── Distributed Execution ───────────────────────────────")
        world = set(initial)
        sync_barrier = {}

        # Execute sequentially (in reality agents run in parallel)
        for ag in agents:
            if sub_plans[ag].actions:
                executor = AgentExecutor(ag, sub_plans[ag])
                world = executor.execute(world, sync_barrier)

        print(f"\n  Final world state: {sorted(world)}")
        achieved = goal.issubset(world)
        print(f"  Goal achieved: {'✓ YES' if achieved else '✗ NO'}")


# ══════════════════════════════════════════════════════════════════════════════
#  Example 2: Rescue Mission (Chapter 3 MAS style)
# ══════════════════════════════════════════════════════════════════════════════

def rescue_mission_example():
    """
    Scenario from Chapter 3 of MAS textbook:
    Robot1 and Robot2 must cooperate to rescue a victim.

    Robot1 can: Navigate, OpenDoor
    Robot2 can: Navigate, CarryVictim

    Goal: Victim at SafeZone
    """
    print("\n" + "═"*60)
    print("  EXAMPLE 2: Rescue Mission (MAS Chapter 3 style)")
    print("═"*60)

    initial = {
        "At(Robot1,Entrance)", "At(Robot2,Entrance)",
        "At(Victim,Room2)",
        "DoorClosed(Room2)",
    }
    goal = {"At(Victim,SafeZone)"}

    actions = [
        # Robot1 navigates and opens door
        Action("Navigate1_to_Room2", "Robot1",
               {"At(Robot1,Entrance)"},
               {"At(Robot1,Room2)"}, {"At(Robot1,Entrance)"}),
        Action("OpenDoor(Room2)", "Robot1",
               {"At(Robot1,Room2)", "DoorClosed(Room2)"},
               {"DoorOpen(Room2)"}, {"DoorClosed(Room2)"}),

        # Robot2 goes to room, picks victim, carries to safe zone
        Action("Navigate2_to_Room2", "Robot2",
               {"At(Robot2,Entrance)", "DoorOpen(Room2)"},
               {"At(Robot2,Room2)"}, {"At(Robot2,Entrance)"}),
        Action("PickupVictim", "Robot2",
               {"At(Robot2,Room2)", "At(Victim,Room2)"},
               {"Carrying(Robot2,Victim)"}, {"At(Victim,Room2)"}),
        Action("CarryToSafeZone", "Robot2",
               {"Carrying(Robot2,Victim)", "At(Robot2,Room2)"},
               {"At(Victim,SafeZone)", "At(Robot2,SafeZone)"},
               {"Carrying(Robot2,Victim)", "At(Robot2,Room2)"}),
    ]

    agents = ["Robot1", "Robot2"]
    planner = CentralPlanner(agents, actions)
    sub_plans, syncs = planner.plan_and_decompose(initial, goal)

    if sub_plans:
        print("\n── Distributed Execution ───────────────────────────────")
        world = set(initial)
        sync_barrier = {}

        for ag in agents:
            if sub_plans[ag].actions:
                executor = AgentExecutor(ag, sub_plans[ag])
                world = executor.execute(world, sync_barrier)

        print(f"\n  Final world state: {sorted(world)}")
        achieved = goal.issubset(world)
        print(f"  Goal achieved: {'✓ YES' if achieved else '✗ NO'}")


# ══════════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    logistics_example()
    rescue_mission_example()
