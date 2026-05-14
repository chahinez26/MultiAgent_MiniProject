#!/bin/bash
echo "Compiling Part 2 - Multi-Criteria Mobile Agents..."
mkdir -p bin
javac -cp jade.jar -d bin src/agents/SellerAgent.java src/agents/MobileBuyerAgent.java src/launcher/Launcher.java
echo "Launching..."
java -cp jade.jar:bin launcher.Launcher
