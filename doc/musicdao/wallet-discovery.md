# MusicCommunity – Payout Node Discovery

In the `MusicCommunity`, nodes can operate in two roles:
- **Payout Node**: A special node responsible for handling contributions.
- **Regular Node**: A normal peer looking for the payout node.

## How Payout Node Discovery Works

To enable discovery of payout nodes over IPv8, we use the `extraBytes` field in `IntroductionRequest` and `IntroductionResponse` payloads.

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

## Summary
This mechanism ensures that regular nodes efficiently discover and communicate with the payout node without relying on centralized infrastructure, maintaining the decentralized nature of the network.
