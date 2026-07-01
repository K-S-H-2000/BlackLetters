package com.example.BlackLetters_spring_boot.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "budgets",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_budget_user_category_month",
            columnNames = {"user_id", "category_id", "budget_month"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "budget_id")
    private Long budgetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "budget_month", nullable = false)
    private LocalDate budgetMonth;

    @Column(name = "amount", nullable = false)
    private Integer amount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public Budget(User user, Category category, LocalDate budgetMonth, Integer amount) {
        this.user = user;
        this.category = category;
        this.budgetMonth = budgetMonth;
        this.amount = amount != null ? amount : 0;
    }

    public void updateAmount(Integer amount) {
        this.amount = amount;
    }
}
