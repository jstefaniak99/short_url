package com.example.project.repository;

import com.example.project.model.ShortUrlEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShortUrlRepository extends CassandraRepository<ShortUrlEntity, String> {
}