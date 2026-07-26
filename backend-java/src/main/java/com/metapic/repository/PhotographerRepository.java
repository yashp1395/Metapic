package com.metapic.repository;

import com.metapic.model.Photographer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PhotographerRepository extends MongoRepository<Photographer, String> {

    Optional<Photographer> findByEmail(String email);

    boolean existsByEmail(String email);
}
