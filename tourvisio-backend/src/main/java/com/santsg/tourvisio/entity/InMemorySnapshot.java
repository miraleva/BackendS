package com.santsg.tourvisio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "in_memory_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InMemorySnapshot {

    @Id
    @Column(name = "snapshot_key", length = 100)
    private String snapshotKey;

    @Column(name = "snapshot_data", columnDefinition = "TEXT")
    private String snapshotData;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
