# TrustChain Super App 

This repository contains our (Class of 2025 - Spotify 1 Team 3) implementation on top of MusicDao, an IPv8 app where users can share and discover tracks on the trustchain.

The main features that we have implemented are:

- **Artists Payouts Though Payout Nodes**: Users can listen to songs and make contributions to artists based on the time they have listened to each artist. The app collects donations and distributes them to artists through a payout node.
- **Music Seeding**: We seed a dataset of the most seeded Creative Commons songs from [pandacd.io](https://pandacd.io).

## Artists Payouts Though Payout Nodes

### Overview

To allow users to contribute directly to the artists they listen to, we implemented a donation system that collects user contributions and distributes them in batch payments via a semi-trusted payout node. This approach reduces transaction fees and simplifies tax handling, while leveraging TrustChain to ensure transparent, verifiable records of all contributions. It enables scalable and decentralized artist support without relying on traditional streaming platforms.

### Flows

#### Making a contribution

To make a contribution, the user must be connected to an active payout node (the app automatically searches for an IPv8 peer acting in that role), have listened to songs since his last contribution and also have the wanted amount of BTC in his wallet.The app tracks all the songs the user has listened to since their last contribution, and when the user decides to donate and on contribution the amount is then proportionally split among the artists based on listening time. The funds are sent to the payout node’s wallet, which later batches multiple contributions and forwards the payments to the artists. Users can view their full contribution history within the app, with completed (already paid out) contributions clearly marked in green.

[NOTE: try to put all screenshots together]
[TODO: add a screenshot of empty contribute screen]
[TODO: add a screenshot of create contribution screen]
[TODO: add a screenshot of one contribution]
[TODO: add a screenshot of many contributions, some verified]



#### Managing a payout node

If no active payout node is found (this can be checked in the app’s left-side drawer) the user can start acting as a node by tapping the "Payout node: searching..." label four times and accepting the prompt. Once enabled, the app must be fully restarted to ensure proper connectivity with the other peers.

Users managing a node can open the node management interface via the "Payout node: 0.0.0.0:0" option in the drawer. This interface allows them to review all incoming contributions, view the full history of payouts, examine how contributions are split among artists, and manually advance payouts through their lifecycle.

A payout progresses through three stages:

- Collecting: This is the default stage where new contributions with verified BTC transactions are automatically added. Only one payout can be in this stage at a time.

- Awaiting Confirmation: Once moved to this stage, the payout is frozen—no new contributions are accepted. The app notifies users and shares which contributions are included and how funds will be distributed, allowing for potential fault claims (not yet implemented).

- Submitted: In this final stage, the BTC transaction distributing the funds is executed, and users are notified, including the transaction ID (txid).

Additionally, users managing the node can view all Unverified Contributions (the contributions where the BTC transaction has not yet been confirmed, as they may arrive instantly or take a few minutes).

[TODO: add screenshots: drawer, menu, many payouts, one payout, unverified, wallet]

### Implementation

#### Music listen recording

To allow contributions to be split proportionally, the app records the number of minutes each artist has been listened to. Listening activity is updated whenever the player's playback state changes. This data is persisted locally and kept until the user makes their next contribution.

#### Payout node finding [[payout discovery docs]](./doc/musicdao/wallet-discovery.md)

To enable discovery of payout nodes over IPv8, we use the `extraBytes` field in `IntroductionRequest` and `IntroductionResponse` payloads.

There are two types of peers in the community:
- Regular peer: Listens to music, makes contributions, and potentially publish music.
- Payout node peer: Also handles contribution collection and payout processing.

We have introduced the following extraBytes values to identify the type of peer:
| Flag | Value | Description |
|------|-------|-------------|
| IS_PAYOUT_NODE | 0x01 | Indicates that the peer is a payout node and includes its Bitcoin address. |
| IS_LOOKING_FOR_PAYOUT_NODE | 0x02 | Indicates that the peer is looking for a payout node. |
| KNOWS_PAYOUT_NODE | 0x03 | Indicates that the peer knows the payout node and includes its peer address. |

We override IPv8 methods to implement peer discovery:
- When sending an introduction request (implemented in walkTo() and getNewIntroduction()):
  * If the peer is a payout node, it sets extraBytes to 0x01 (IS_PAYOUT_NODE) + its BTC address.
  * If the peer is looking for a payout node, it sets extraBytes to 0x02 (IS_LOOKING_FOR_PAYOUT_NODE).
- When receiving an introduction request (implemented in onPacket()):
  * If extraBytes contain 0x01 (IS_PAYOUT_NODE), the peer stores the payout node's peer address and BTC address.
  * If extraBytes contain 0x02 (IS_LOOKING_FOR_PAYOUT_NODE), and the peer knows a payout node, it responds with 0x03 (KNOWS_PAYOUT_NODE) and the known node’s peer address. If the peer is a payout node, it sends 0x01 (IS_PAYOUT_NODE) + its BTC address instead.
- When receiving an introduction response (implemented in onPacket()):
  * If extraBytes contain 0x01 (IS_PAYOUT_NODE), the peer stores the payout node's peer address and BTC address.
  * If extraBytes contain 0x03 (KNOWS_PAYOUT_NODE), the peer can walk to the payout node’s peer address.

#### Making a contribution

The contribution process consists of the following steps:
- make a Bitcoin transaction to the payout node's wallet address with the desired donation amount
- create a Contribution block with the following data:
  * the txid of the contribution
  * the list of artists and the percentage of time listened to each one of them
- send a Contribution IPv8 message to the payout node with the Contribution block

#### Node protocol

Each payout follows a three-phase protocol (at any given time, there is exactly one payout in the `COLLECTING` phase):
- phase 1: COLLECTING - collect contributions
  * accept IPv8 Contribution messages and persist them in the node database
  * listen for incoming Bitcoin transactions to the payout node's wallet and if any match the txid of a Contribution block, mark the contribution as verified and update the state for the payout
- phase 2: AWAITING_FOR_CONFIRMATION - announce payout and allow verification (we haven't implemented a mechanism to submit fault claims)
  * compile all verified contributions that are to be used in the payout in a torrent 
  * generate a torrent containing the full contribution data
  * publish and gossip a `PayoutUpdateStatusBlock` TrustChain block that with payout metadata (including the torrent magnet link), the txids of first 100 contributions (due to size limits) and the amount to send to each artist
- phase 3: SUBMITTED - make the batch btc transaction to all artists
  * make the btc transaction
  * publish and gossip a `PayoutUpdateStatusBlock` TrustChain block, but this time including the txid, so users can verify that the payout was successful and executed correctly

The logic for payout node operation is located in the /core/node directory.

### Assumptions
- The verification of the payout node is out of the scope of this project, we assume that there is a secure way to announce the payout node, so that users can verify it and trust that it won't misbehave. 

### Future improvements

- Introduce a DAO mechanism to govern the payout node (e.g., determine the node's wallet address, enable replacement in case of misbehavior via fault claims). An easy solution could be to have a shared DAO wallet that makes a transaction to the payout node's wallet address, so that the payout node can be replaced by another one if it misbehaves.
- Investigate ways to fix the torrents cold starts, so the payouts data can be easily verified by the users.
- Although the solution is scalable, a large number of users could result in a high volume of BTC transactions, leading to increased network fees. Future iterations should explore alternatives like Lightning channels, ideally with fewer liquidity and complexity constraints.

### Alternative considered solutions

We have also considered various alternative solutions, such as decentralized TrustChain contributions pool, using TrustChain to gossip Contributions instead of direct ipv8 messages, using Lightning network and similar solutions. However, they are not suitable for the application in our opinion and the reasons can be found in the comments of our GitHub issue: https://github.com/Tribler/tribler/issues/8575#issuecomment-2909480203 https://github.com/Tribler/tribler/issues/8575#issuecomment-2922223146


## Music Seeding [[seeding docs]](./doc/musicdao/music-seeding.md) 

### Overview
To expand the song library in the app, we seed a dataset of the most seeded Creative Commons songs from https://pandacd.io.


### Implementation
Seeding is currently implemented by downloading and processing a torrent that contains metadata for a large collection of songs. This metadata includes the song title, artist name, and magnet link for each song. The torrent itself is generated by scraping https://pandacd.io/ with https://github.com/brian2509/pandacd-scrape.

Upon app startup, the torrent is automatically downloaded and then seeded. Once the metadata is retrieved, it is parsed and added to the database as though it had originated from the TrustChain network. Then the songs can be handled as any other song orginating from TrustChain blocks.

Since many artists listed on PandaCD do not include a Bitcoin address, we generate deterministic addresses based on the artist's name. This allows us to simulate reward distribution on the Bitcoin testnet based on user listening activity.

The torrent magnet is hardcoded in `res/values/strings.xml` with name 'bootstrap_cc_music_metadata'.

### Potential improvements
- Ensure that all Creative Commons music artists have their actual Bitcoin wallet addresses for authentic contributions.
- Implement a decentralized mechanism to publish and update the metadata torrent. For example, a trusted DAO could post torrent magnets to the TrustChain, allowing large volumes of songs to be added to the app efficiently, without relying on individual TrustChain gossip messages for each song.

### Issues
- There must be at least one seeder of the metadata torrent to start the entire process, which might be a problem if there are no users within the app and it is not being seeded externaly.
- If there is a small amount of peers, it will be difficult to obtain the metadata of the magnet link (cold start issues), so the seeding might take long time. 

## Build Instructions

### Clone
Clone the repository **including the submodule** with the following command:
```
git clone --recurse-submodules <URL>
```

If you have already cloned the repository and forgot to include the `--recurse-submodules` flag, you can initialize the submodule with the following command:
```
git submodule update --init --recursive
```
You can also update the submodule with this command.

### Build
If you want to build an APK, run the following command:
```
./gradlew :app:assembleDebug
```
The resulting APK will be stored in `app/build/outputs/apk/debug/app-debug.apk`.

### Install
You can also build and automatically install the app on all connected Android devices with a single command:
```
./gradlew :app:installDebug
```
*Note: It is required to have an Android device connected with USB debugging enabled before running this command.*

### Check
Run the Gradle check task to verify that the project is correctly set up and that tests pass:
```
./gradlew check
```
*Note: this task is also run on the CI, so ensure that it passes before making a PR.*  

### Tests
Run unit tests:
```
./gradlew test
```

Run instrumented tests:
```
./gradlew connectedAndroidTest
```

### Code style
[Ktlint](https://ktlint.github.io/) is used to enforce a consistent code style across the whole project. It is recommended to install the [ktlint plugin](https://plugins.jetbrains.com/plugin/15057-ktlint) for your IDE to get real-time feedback.

Check code style:
```
./gradlew ktlintCheck
```

Apply linter:
```
./gradlew ktlintFormat
```
