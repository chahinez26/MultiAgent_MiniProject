"""
Part 3 — Partial Order Planner (POP)
=====================================
Based on the AIMA (Artificial Intelligence: A Modern Approach) algorithm.
Reference: https://github.com/aimacode/aima-python/blob/master/planning_partial_order_planner.ipynb

A Partial Order Plan (POP) is a set of:
  - Steps   : actions to be executed
  - Ordering constraints : Si < Sj  (Si must come before Sj)
  - Causal links : Si --[p]--> Sj  (Si achieves precondition p of Sj)
  - Open preconditions : preconditions not yet supported

The POP algorithm :
  1. If no open preconditions → SUCCESS, return plan
  2. Pick an open precondition <p, Sj>
  3. Choose an action Si that achieves p
  4. Add causal link Si --[p]--> Sj
  5. Add ordering constraint Si < Sj
  6. If Si is new, add it to plan steps and its preconditions to open set
  7. For every step Sk that might THREATEN the causal link, resolve conflict
  8. Recurse
"""

from copy import deepcopy
from itertools import count


# ══════════════════════════════════════════════════════════════════════════════
#  Data Structures
# ══════════════════════════════════════════════════════════════════════════════

class Action:
    """Represents a planning action (operator)."""
    _counter = count(1)

    def __init__(self, name, preconditions, add_effects, del_effects):
        self.name         = name
        self.precond      = set(preconditions)   # literals that must hold
        self.add_effects  = set(add_effects)     # literals made true
        self.del_effects  = set(del_effects)     # literals made false
        self.step_id      = next(Action._counter)

    def __repr__(self):
        return f"Action({self.name})"

    def clone(self):
        """Return a fresh clone with a new step_id (for use as a new plan step)."""
        a = Action(self.name, self.precond, self.add_effects, self.del_effects)
        return a


class CausalLink:
    """Si --[condition]--> Sj"""
    def __init__(self, producer, condition, consumer):
        self.producer  = producer    # Action that establishes condition
        self.condition = condition   # The literal being established
        self.consumer  = consumer    # Action that needs condition

    def __repr__(self):
        return f"({self.producer.name} --[{self.condition}]--> {self.consumer.name})"


class PartialOrderPlan:
    """A partial order plan (POP)."""

    def __init__(self, initial_state, goal_state):
        # Special boundary actions
        self.start = Action("Start", [], list(initial_state), [])
        self.finish = Action("Finish", list(goal_state), [], [])

        self.steps   = [self.start, self.finish]
        self.orderings  = {(self.start, self.finish)}  # start < finish
        self.causal_links = []
        self.open_preconditions = [(p, self.finish) for p in goal_state]

    def add_step(self, action):
        self.steps.append(action)
        # New step must come after Start and before Finish
        self.orderings.add((self.start, action))
        self.orderings.add((action, self.finish))

    def add_ordering(self, before, after):
        self.orderings.add((before, after))

    def add_causal_link(self, link: CausalLink):
        self.causal_links.append(link)

    def is_ordered(self, si, sj):
        """True if si must come before sj (transitively)."""
        # BFS
        visited = set()
        queue = [si]
        while queue:
            current = queue.pop()
            if current == sj:
                return True
            if current in visited:
                continue
            visited.add(current)
            for (a, b) in self.orderings:
                if a == current and b not in visited:
                    queue.append(b)
        return False

    def threatens(self, sk, link: CausalLink):
        """
        Sk threatens causal link Si--[p]-->Sj if:
        - Sk deletes p (Sk has p in del_effects)
        - Sk ≠ Si and Sk ≠ Sj
        - Sk is not ordered after Sj or before Si
        """
        if sk == link.producer or sk == link.consumer:
            return False
        if link.condition not in sk.del_effects:
            return False
        # Check ordering already resolves threat
        if self.is_ordered(sk, link.producer) or self.is_ordered(link.consumer, sk):
            return False
        return True

    def topological_sort(self):
        """Return a total order consistent with the ordering constraints."""
        from collections import defaultdict, deque
        in_degree = defaultdict(int)
        graph = defaultdict(list)
        for (a, b) in self.orderings:
            graph[a].append(b)
            in_degree[b] += 1
        for s in self.steps:
            if s not in in_degree:
                in_degree[s] = 0
        queue = deque([s for s in self.steps if in_degree[s] == 0])
        order = []
        while queue:
            node = queue.popleft()
            order.append(node)
            for neighbor in graph[node]:
                in_degree[neighbor] -= 1
                if in_degree[neighbor] == 0:
                    queue.append(neighbor)
        return order


# ══════════════════════════════════════════════════════════════════════════════
#  POP Solver
# ══════════════════════════════════════════════════════════════════════════════

def pop_solve(plan: PartialOrderPlan, action_library: list, depth=0):
    """
    Recursive POP solver.
    Returns a completed PartialOrderPlan or None if no solution exists.
    """
    indent = "  " * depth

    # ── Base case: no open preconditions ────────────────────────────────────
    if not plan.open_preconditions:
        print(f"{indent}✓ Plan complete!")
        return plan

    # ── Pick an open precondition (agenda) ──────────────────────────────────
    precond, consumer = plan.open_preconditions[0]
    remaining_open = plan.open_preconditions[1:]

    print(f"{indent}Open precondition: '{precond}' needed by {consumer.name}")

    # ── Try each possible provider (existing steps + new actions) ───────────
    candidates = list(plan.steps) + action_library

    for provider in candidates:
        if precond not in provider.add_effects:
            continue

        print(f"{indent}  Trying to satisfy '{precond}' with {provider.name}")

        new_plan = deepcopy(plan)
        new_plan.open_preconditions = deepcopy(remaining_open)

        # Find the cloned provider in the new plan
        if provider in plan.steps:
            # Existing step (find by name in new plan)
            new_provider = next(s for s in new_plan.steps if s.name == provider.name)
        else:
            # New action — clone and add
            new_provider = provider.clone()
            new_plan.add_step(new_provider)
            # All preconditions of the new provider become open
            for p in new_provider.precond:
                new_plan.open_preconditions.append((p, new_provider))

        # Find consumer in new plan
        new_consumer = next(s for s in new_plan.steps if s.name == consumer.name)

        # Add causal link
        link = CausalLink(new_provider, precond, new_consumer)
        new_plan.add_causal_link(link)

        # Add ordering
        if new_provider != new_consumer:
            new_plan.add_ordering(new_provider, new_consumer)

        # ── Resolve threats ──────────────────────────────────────────────
        plan_valid = True
        for sk in new_plan.steps:
            if new_plan.threatens(sk, link):
                print(f"{indent}  ⚠ Threat detected: {sk.name} threatens {link}")
                # Try demotion: sk before producer
                resolved = False

                # Demotion: sk < producer
                if not new_plan.is_ordered(new_provider, sk):  # no cycle
                    demo_plan = deepcopy(new_plan)
                    sk_demo = next(s for s in demo_plan.steps if s.name == sk.name)
                    prov_demo = next(s for s in demo_plan.steps if s.name == new_provider.name)
                    demo_plan.add_ordering(sk_demo, prov_demo)
                    print(f"{indent}  Demotion: {sk.name} < {new_provider.name}")
                    result = pop_solve(demo_plan, action_library, depth+1)
                    if result:
                        return result
                    resolved = True

                # Promotion: consumer < sk
                if not new_plan.is_ordered(sk, new_consumer):  # no cycle
                    prom_plan = deepcopy(new_plan)
                    sk_prom = next(s for s in prom_plan.steps if s.name == sk.name)
                    cons_prom = next(s for s in prom_plan.steps if s.name == new_consumer.name)
                    prom_plan.add_ordering(cons_prom, sk_prom)
                    print(f"{indent}  Promotion: {new_consumer.name} < {sk.name}")
                    result = pop_solve(prom_plan, action_library, depth+1)
                    if result:
                        return result
                    resolved = True

                if not resolved:
                    plan_valid = False
                    break

        if not plan_valid:
            continue

        result = pop_solve(new_plan, action_library, depth+1)
        if result:
            return result

    print(f"{indent}✗ No solution found for '{precond}'")
    return None


# ══════════════════════════════════════════════════════════════════════════════
#  Classic AIMA Example: Sussman Anomaly
# ══════════════════════════════════════════════════════════════════════════════

def sussman_anomaly():
    """
    Sussman Anomaly — classic planning benchmark.

    Initial state : On(C,A), On(A,Table), On(B,Table), Clear(C), Clear(B)
    Goal state    : On(A,B), On(B,C)

    Actions:
      Move(x,y,z): Move block x from y to z
        Pre   : On(x,y), Clear(x), Clear(z)
        Add   : On(x,z), Clear(y)
        Del   : On(x,y), Clear(z)

      MoveToTable(x,y): Move block x from y to Table
        Pre   : On(x,y), Clear(x)
        Add   : On(x,Table), Clear(y)
        Del   : On(x,y)
    """
    print("═" * 60)
    print("  SUSSMAN ANOMALY — Partial Order Planning")
    print("═" * 60)

    initial = {"On(C,A)", "On(A,Table)", "On(B,Table)", "Clear(C)", "Clear(B)"}
    goal    = {"On(A,B)", "On(B,C)"}

    print(f"\nInitial state : {initial}")
    print(f"Goal state    : {goal}\n")

    # Action library
    actions = [
        Action("Move(A,B,C)",      ["On(A,B)", "Clear(A)", "Clear(C)"], ["On(A,C)", "Clear(B)"], ["On(A,B)", "Clear(C)"]),
        Action("Move(B,Table,C)",  ["On(B,Table)", "Clear(B)", "Clear(C)"], ["On(B,C)", "Clear(Table)"], ["On(B,Table)", "Clear(C)"]),
        Action("Move(A,C,B)",      ["On(A,C)", "Clear(A)", "Clear(B)"], ["On(A,B)", "Clear(C)"], ["On(A,C)", "Clear(B)"]),
        Action("Move(A,Table,B)",  ["On(A,Table)", "Clear(A)", "Clear(B)"], ["On(A,B)", "Clear(Table)"], ["On(A,Table)", "Clear(B)"]),
        Action("MoveToTable(C,A)", ["On(C,A)", "Clear(C)"], ["On(C,Table)", "Clear(A)"], ["On(C,A)"]),
        Action("MoveToTable(A,B)", ["On(A,B)", "Clear(A)"], ["On(A,Table)", "Clear(B)"], ["On(A,B)"]),
        Action("MoveToTable(B,C)", ["On(B,C)", "Clear(B)"], ["On(B,Table)", "Clear(C)"], ["On(B,C)"]),
    ]

    plan = PartialOrderPlan(initial, goal)
    result = pop_solve(plan, actions)

    if result:
        print("\n─── SOLUTION ───────────────────────────────")
        order = result.topological_sort()
        print("Total order (one valid linearisation):")
        for i, step in enumerate(order):
            print(f"  {i}. {step.name}")
        print("\nCausal links:")
        for link in result.causal_links:
            print(f"  {link}")
        print("\nOrdering constraints:")
        for (a, b) in sorted(result.orderings, key=lambda x: x[0].name):
            print(f"  {a.name} < {b.name}")
    else:
        print("No plan found!")

    return result


# ══════════════════════════════════════════════════════════════════════════════
#  Second example: Spare Tyre Problem
# ══════════════════════════════════════════════════════════════════════════════

def spare_tyre_problem():
    """
    Spare Tyre Problem (AIMA classic).

    Initial : Flat(Tyre), Intact(Spare), At(Flat, Axle), At(Spare, Boot)
    Goal    : At(Spare, Axle)

    Actions:
      Remove(Spare, Boot)  : At(Spare,Boot) → At(Spare,Ground)
      Remove(Flat, Axle)   : At(Flat,Axle)  → At(Flat,Ground), Clear(Axle)
      PutOn(Spare, Axle)   : At(Spare,Ground), Clear(Axle) → At(Spare,Axle)
    """
    print("\n" + "═" * 60)
    print("  SPARE TYRE PROBLEM — Partial Order Planning")
    print("═" * 60)

    initial = {"At(Flat,Axle)", "At(Spare,Boot)", "Intact(Spare)"}
    goal    = {"At(Spare,Axle)"}

    print(f"\nInitial state : {initial}")
    print(f"Goal state    : {goal}\n")

    actions = [
        Action("Remove(Spare,Boot)", ["At(Spare,Boot)"],
               ["At(Spare,Ground)"], ["At(Spare,Boot)"]),
        Action("Remove(Flat,Axle)", ["At(Flat,Axle)"],
               ["At(Flat,Ground)", "Clear(Axle)"], ["At(Flat,Axle)"]),
        Action("PutOn(Spare,Axle)", ["At(Spare,Ground)", "Clear(Axle)"],
               ["At(Spare,Axle)"], ["At(Spare,Ground)", "Clear(Axle)"]),
    ]

    plan = PartialOrderPlan(initial, goal)
    result = pop_solve(plan, actions)

    if result:
        print("\n─── SOLUTION ───────────────────────────────")
        order = result.topological_sort()
        for i, step in enumerate(order):
            print(f"  {i}. {step.name}")
        print("\nCausal links:")
        for link in result.causal_links:
            print(f"  {link}")
    return result


# ══════════════════════════════════════════════════════════════════════════════
#  Entry Point
# ══════════════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    sussman_anomaly()
    spare_tyre_problem()
