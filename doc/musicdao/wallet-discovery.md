# MusicCommunity – Payout Node Discovery

In the `MusicCommunity`, nodes can operate in two roles:
- **Payout Node**: A special node responsible for handling contributions.
- **Regular Node**: A normal peer looking for the payout node.

## How Payout Node Discovery Works

1. **Role Announcement via Introduction Requests**  
   Each node announces its role during the IPv8 introduction process:
    - **Payout Node** sets the extraBytes to be `IS_PAYOUT_NODE`(0x01) + its Bitcoin address.
    - **Regular Nodes** sets the extraBytes to be `IS_LOOKING_FOR_PAYOUT_NODE`(0x02) to indicate they are searching for a payout node. 
Once they find it their next introduction requests dont include the extra bytes.

2. **Discovery Flow**  
   When a regular node receives an introduction request:
    - If it **doesn't know the payout node**, and in the extraBytes it finds `IS_PAYOUT_NODE`(0x01), it stores which peer is the payout node and it's Bitcoin address.
    - If it **knows the payout node** (previously discovered), and recieves a request with extraBytes containing `IS_LOOKING_FOR_PAYOUT_NODE`(0x02), it forwards this information to the requester (sends an introduction req impersonating the payout node).

    When a payout node receives an ipv4 message:
    - If the payout node hasn't discovered the sender before it send them a introduction request with extraBytes set to `IS_PAYOUT_NODE`(0x01) + its Bitcoin address.



3. **Caching**  
   Regular nodes cache the payout node's Bitcoin address once discovered to avoid redundant searches.


## Key Components
- **`walkTo()`**: Sends introduction requests with role info following the random walk discovery.
- **`onPacket()`**: Handles incoming introductions and manages discovery logic.

## Summary
This mechanism ensures that regular nodes efficiently discover and communicate with the payout node without relying on centralized infrastructure, maintaining the decentralized nature of the network.
