package com.jurisflow.modules.user;

import com.jurisflow.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String nome;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "google_sub", unique = true)
    private String googleSub;
}
