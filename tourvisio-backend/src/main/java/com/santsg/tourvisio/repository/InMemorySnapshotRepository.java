package com.santsg.tourvisio.repository;

import com.santsg.tourvisio.entity.InMemorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InMemorySnapshotRepository extends JpaRepository<InMemorySnapshot, String> {
}
