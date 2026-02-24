package io.hypersistence.utils.spring.domain;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Vlad Mihalcea
 */
@Entity
@Table(
    name = "users"
)
public class User {

    @Id
    private Long id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @ElementCollection
    private Set<Role> roles = new HashSet<>();

    public Long getId() {
        return id;
    }

    public User setId(Long id) {
        this.id = id;
        return this;
    }

    public String getFirstName() {
        return firstName;
    }

    public User setFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public String getLastName() {
        return lastName;
    }

    public User setLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public User addRole(Role role) {
        roles.add(role);
        return this;
    }

    public enum Role {
        ADMIN,
        GUEST
    }
}
