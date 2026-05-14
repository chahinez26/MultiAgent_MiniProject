
# Mini-Project: Multi-Agent Systems & Planning

**USTHB — M1 SII — S2 | Agents Technology | 2025-2026**

---

## Project Structure

```
MultiAgent_MiniProject/
├── Part1_Auction/                       
│   ├── src/agents/SellerAgent.java
│   ├── src/agents/BuyerAgent.java
│   ├── src/launcher/AuctionLauncher.java
│   └── run.sh
│
├── Part2_MultiCriteria_MobileAgents/     
│   ├── src/agents/MobileBuyerAgent.java
│   ├── src/agents/SellerAgent2.java
|   ├── src/launcher/Launcher.java│
│   └── run.sh
├── Part3_AIMA_Planning/                
│   ├── partial_order_planner.py
│   └── planning_partial_order_planner.ipynb // from github : https://github.com/aimacode/aima-python/blob/master/planning_partial_order_planner.ipynb 
│
├── Part4_CentralizedPlanning/           
│   └── centralized_planning.py
│
├── README.md
├── TA-Mini-Project 25-26.pdf
└── Rapport_MiniProject_MAS.docx
```

---

## Quick Start

### Parts 1 & 2 (Java/JADE)

> Requires Java JDK 8+ and `jade.jar`Download jade.jar from: https://jade.tilab.com/

```bash
# Part 1
cd Part1_Auction && ./run.sh

# Part 2
cd Part2_MultiCriteria_MobileAgents && ./run.sh
```

### Parts 3 & 4 (Python)

> Requires Python 3.7+, no external packages needed

```bash
# Part 3
python Part3_AIMA_Planning/partial_order_planner.py

# Part 4
python Part4_CentralizedPlanning/centralized_planning.py
```

---

## Summary

<!-- gpdoc-table:{"alignment":"left","tableBorder":true} -->
| Part | Topic | Technology |
| :--- | :--- | :--- |
| 1 | English Auction (1 seller, N buyers) | Java + JADE |
| 2 | Multi-criteria decision (evaluation function) + Mobile Agents | Java + JADE |
| 3 | Partial Order Planner (AIMA) | Python |
| 4 | Centralized Planning for Distributed Plans | Python |
