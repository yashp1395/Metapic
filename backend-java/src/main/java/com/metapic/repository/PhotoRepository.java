package com.metapic.repository;

import com.metapic.model.Photo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PhotoRepository extends MongoRepository<Photo, String> {

    /** Delete all photos belonging to a group. */
    void deleteAllByGroup(String groupId);

    /** Find all photos belonging to a group. */
    List<Photo> findAllByGroup(String groupId);
}
