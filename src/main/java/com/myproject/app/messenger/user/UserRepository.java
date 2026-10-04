package com.myproject.app.messenger.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("select u from User u where lower(u.username) in :usernames")
    List<User> findAllByLowerUsernameIn(Collection<String> usernames);

    @Query("""
            select u from User u
            where (lower(u.username) like concat(lower(:q), '%') or lower(u.displayName) like concat('%', lower(:q), '%'))
              and u.id <> :exclude
            order by u.username""")
    List<User> search(String q, UUID exclude, Pageable page);
}
