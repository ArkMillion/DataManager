package cn.arkmillion.examples;

import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.annotation.Table;

import java.time.LocalDateTime;

@Table(name = "sys_order", comment = "订单表")
public class Order {

    @Id(strategy = cn.arkmillion.core.enums.GenerationType.AUTO)
    @cn.arkmillion.core.annotation.AutoIncrement
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private java.math.BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Order() {
    }

    public Order(Long userId, double amount) {
        this.userId = userId;
        this.amount = java.math.BigDecimal.valueOf(amount);
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(java.math.BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
