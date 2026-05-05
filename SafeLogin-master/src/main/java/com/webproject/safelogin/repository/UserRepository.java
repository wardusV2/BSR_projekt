package com.webproject.safelogin.repository;

import com.webproject.safelogin.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    User findByEmail(String email);
    List<User> findBySubscribers_Id(Integer subscriberId);

}
