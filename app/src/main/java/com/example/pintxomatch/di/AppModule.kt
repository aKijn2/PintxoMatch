package com.example.pintxomatch.di

import com.example.pintxomatch.data.repository.auth.AuthRepository
import com.example.pintxomatch.data.repository.chat.ChatRepository
import com.example.pintxomatch.data.repository.gamification.GamificationGateway
import com.example.pintxomatch.data.repository.gamification.GamificationRepository
import com.example.pintxomatch.data.repository.pintxo.PintxoRepository
import com.example.pintxomatch.data.repository.user.UserRepository

object AppModule {
    fun provideAuthRepository() = AuthRepository

    fun provideChatRepository() = ChatRepository()

    fun provideGamificationRepository(): GamificationGateway = GamificationRepository()

    fun providePintxoRepository() = PintxoRepository()

    fun provideUserRepository() = UserRepository()
}
