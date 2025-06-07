package nl.tudelft.trustchain.musicdao.core.server.persistence.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity
data class PayoutEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
) {}
