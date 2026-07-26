package com.metapic.repository;

import com.metapic.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByName(String name);

    Optional<User> findByEmail(String email);

    /** Mirrors Node's User.findOne({ $or: [{name}, {email}] }) */
    Optional<User> findByNameOrEmail(String name, String email);

    boolean existsByName(String name);

    boolean existsByEmail(String email);
}
