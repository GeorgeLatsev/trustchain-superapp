# Music Dao App

### Key New Functionalities
- [seeding](music-seeding.md) 
- making contributions to artists
- [payout node discovery](wallet-discovery.md)


### Flow of the app
Once a person enters the app, it immediately starts looking for a payout node. If a node cannot be found within a reasonable time, the user can manually become a payout node by rapidly tapping 4 times on "payout node: searching...".

In the **Home** page, users can discover the songs that have been seeded or added by other users. Each user can download these songs and start listening to them. Once downloaded, the songs are available for playback.

In the **Creator** tab a user can check what time they have listened to each user. Thise times would be used when the user decideds to make a donation.

In the **Contribute** tab users can chose to make a dontaion. The user would have to specify the amount of bitcoin they want to send and the app would internally devide this amount among the artists that that've listened to proportionally to the played time. The money are then transfered to a **payout node** which collects donations from users.

The payout node can decide when to execute the tranasaction to all the artists. There are 3 stages:
- COLLECTING -> waiting to collect money (green)
- AWAITING_FOR_CONFIRMATION -> stage the transaction (yellow)
- SUBMITTED -> commit (grey)

If a submitted transaction fails for some reason it automatically changes it status again to AWAITING_FOR_CONFIRMATION

Once a transaction is successfuly commited all the artist fro the users have been payed

The users recieve an update of their donations whenever there is a change in status and this can be seen in the **Contribute** screen allong with all its previous donations.

In the **Creator** tab in the wallet each user can also check the bitcoin transactions they have made from most recent to last, red for sending money green for recieving money.
