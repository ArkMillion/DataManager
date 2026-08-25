package cn.arkmillion.examples;

import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.annotation.Index;
import cn.arkmillion.core.annotation.Table;

import java.time.LocalDateTime;

@Table(name = "sys_user", comment = "用户表")
public class User {

    @Id(strategy = cn.arkmillion.core.enums.GenerationType.AUTO)
    @cn.arkmillion.core.annotation.AutoIncrement
    private Long id;

    @Column(name = "user_name", length = 64, nullable = false, comment = "用户名")
    private String username;

    @Column(name = "email", length = 128, nullable = false)
    @Index(type = cn.arkmillion.core.enums.IndexType.UNIQUE, name = "uk_email")
    private String email;

    @Column(name = "status", type = cn.arkmillion.core.enums.DataType.TINYINT, defaultValue = "1")
    private Integer status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String username, String email) {
        this.username = username;
        this.email = email;
        this.createdAt = LocalDateTime.now();
        this.status = 1;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
