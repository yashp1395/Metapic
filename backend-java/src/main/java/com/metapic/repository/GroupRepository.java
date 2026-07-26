package com.metapic.repository;

import com.metapic.model.Group;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends MongoRepository<Group, String> {

    Optional<Group> findByCode(String code);

    /** Find all groups owned by a photographer, newest first. */
    List<Group> findByPhotographerOrderByCreatedAtDesc(String photographerId);

    /** Find a group by code AND owning photographer (security check). */
    Optional<Group> findByCodeAndPhotographer(String code, String photographerId);

    boolean existsByCode(String code);
}
