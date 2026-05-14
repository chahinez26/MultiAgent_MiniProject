#!/bin/bash
echo "Compiling Part 1 - Auction MAS..."
mkdir -p bin
javac -cp jade.jar -d bin src/agents/SellerAgent.java src/agents/BuyerAgent.java src/launcher/AuctionLauncher.java
echo "Launching..."
java -cp jade.jar:bin launcher.AuctionLauncher
