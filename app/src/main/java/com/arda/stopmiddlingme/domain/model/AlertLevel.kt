package com.arda.stopmiddlingme.domain.model

enum class AlertLevel(val score: Int) {
    SAFE(0),
    SUSPECT(1),
    WARNING(2),
    CRITIQUE(3);

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
