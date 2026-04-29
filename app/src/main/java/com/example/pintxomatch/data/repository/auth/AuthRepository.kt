package com.example.pintxomatch.data.repository.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

object AuthRepository {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val currentUserId: String?
        get() = auth.currentUser?.uid

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    fun signOut() {
        auth.signOut()
    }
    
    // Auth login/register methods remain task-based or handled in UI 
    // until we fully implement ViewModel with coroutine await() 
    // to avoid adding new library dependencies mid-refactor.
}
