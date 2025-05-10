package com.example.cleanup.repository;

import com.example.cleanup.model.ShortUrlEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShortUrlRepository extends CassandraRepository<ShortUrlEntity, String> {

    @Query("SELECT * FROM short_url_entity WHERE creation_time < ?0 ALLOW FILTERING")
    List<ShortUrlEntity> findAllWithCreationTimeBefore(long timestamp);

    @Query("SELECT * FROM short_url_entity WHERE last_access_time < ?0 ALLOW FILTERING")
    List<ShortUrlEntity> findAllWithLastAccessTimeBefore(long timestamp);


}