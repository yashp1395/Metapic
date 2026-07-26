package com.metapic.dto;

import java.util.List;

/** POST /api/photographer/group/{code}/delete-photos */
public record DeletePhotosRequest(List<String> photoIds, Boolean deleteAll) {}
