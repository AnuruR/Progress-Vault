package com.anuru.progressvault.di

import com.anuru.progressvault.domain.skills.RuleBasedSkillReviewer
import com.anuru.progressvault.domain.skills.SkillReviewer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SkillsModule {
    /** The one place to swap in a future AiSkillReviewer (see SkillReviewer docs). */
    @Binds
    abstract fun bindSkillReviewer(impl: RuleBasedSkillReviewer): SkillReviewer
}
