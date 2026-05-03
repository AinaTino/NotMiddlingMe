package com.arda.stopmiddlingme.domain.model

enum class AlertLevel {
    SAFE,
    SUSPECT,
    WARNING,
    CRITIQUE;

    companion object {
        fun fromScore(score: Int, hasStandalone: Boolean): AlertLevel {
            if (hasStandalone && score >= 4) return CRITIQUE
            return when (score) {
                in 0..3  -> SAFE
                in 4..6  -> SUSPECT
                in 7..9  -> WARNING
                else     -> CRITIQUE
            }
        }
    }
}
