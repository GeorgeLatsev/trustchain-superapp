## Seeding Creative Commons music

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