package com.example.eta.domain.model

import com.example.eta.domain.reward.Balancing
import com.example.eta.domain.reward.BalanceKey

/** The three kinds of task; what an hour of each pays is tuned under Balancing (step 58). */
enum class Category {
    FOKUS,
    NEBENBEI,
    ACHTSAM,
    ;

    val yieldMultiplier: Double
        get() = when (this) {
            FOKUS -> Balancing[BalanceKey.FOCUS]
            NEBENBEI -> Balancing[BalanceKey.SIDELINE]
            ACHTSAM -> Balancing[BalanceKey.MINDFUL]
        }
}
