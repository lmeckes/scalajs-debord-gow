<img src="gow-client/resources/logo.svg" alt="Le Jeu de la Guerre" width="120" align="right"/>

[![Scala CI](https://github.com/meckesl/scalajs-debord-gow/actions/workflows/scala.yml/badge.svg)](https://github.com/meckesl/scalajs-debord-gow/actions/workflows/scala.yml)

## Usage

Run client and server tests
```sh
sbt client/test server/test
```

Generates client Javascript files ready to be run in the browser (optimized)
```sh
sbt client/fullOptJS
```

`gow-client/index.html` can then be opened directly from disk (`file://`) to play local games; online play needs the server.

Run server (serves the game and the online lobby on http://localhost:8080/)
```sh
sbt server/run
```

The server listens on all interfaces; set `PORT` (and optionally `HOST`) to change the bind address.

### Online play

Open the page, pick a nickname and either:
- **Quick match**: you are paired with the next player who also clicks Quick match.
- **Private game**: you get a code and a link to share with a friend.
- **Play locally**: both sides on the same screen.

The server referees every move. If you reload or lose connection you rejoin your game automatically;
a player who stays away for 2 minutes forfeits. Nicknames, lobby and games live in memory only.

## Screenshot

![Optional Screenshot](gow-client/resources/screenshot.png)


## Game of War

A Game of War is a book by Guy Debord and Alice Becker-Ho that illustrates a game devised by Debord by giving a detailed account of one of their table-top conflicts1. Originally published in French as Le Jeu de la Guerre in 1987, the book provides insights into Debord’s strategic thinking and approach to war.

In this unique game, Debord aimed to represent the essential movements of war on a relatively simple game board. Here are some key points about A Game of War:

Game Basics:
The game is somewhat similar to chess, as it revolves around war and strategy.
Two players take turns moving troops across the board.
Each player can move up to 5 troops per turn and is allowed 1 attack.
Attacking involves summing up the offensive power of all pieces within range of an enemy target square and subtracting the total defensive power of the opponent. If the result is greater than 2, the attack succeeds.
The game board includes natural obstructions that simulate different terrains on a battlefield.
Winning Conditions:
The winner can achieve victory by either defeating all the opponent’s troops or destroying their arsenals.
Limitations:
Debord acknowledged that there were three factors preventing the game from being 100% accurate:
Climate conditions and the cycles of day and night.
The influence of troop morale.
Uncertainty about the exact positions and movements of the enemy.
Legacy and Reception:
Although the game and the book did not receive widespread success, copies are still available today.
London-based group Class Wargames has reproduced A Game of War and taken it on a campaign around the globe, playing it in various locations1.
Debord’s reflections on war and strategy through this game offer a unique perspective, and it remains an intriguing piece of intellectual exploration2. If you’re interested in strategic games, A Game of War might be worth exploring further!
