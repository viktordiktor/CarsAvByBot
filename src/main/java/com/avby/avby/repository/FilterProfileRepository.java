package com.avby.avby.repository;

import com.avby.avby.domain.FilterProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FilterProfileRepository extends JpaRepository<FilterProfile, Long> {

    List<FilterProfile> findAllByOrderByIdAsc();

    List<FilterProfile> findByEnabledTrueOrderByIdAsc();
}
