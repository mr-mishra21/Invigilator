package app.invigilator.core.di

import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.auth.AuthRepositoryImpl
import app.invigilator.core.consent.ConsentRepository
import app.invigilator.core.consent.ConsentRepositoryImpl
import app.invigilator.core.linking.LinkingRepository
import app.invigilator.core.linking.LinkingRepositoryImpl
import app.invigilator.core.session.SessionStateRepository
import app.invigilator.core.session.SessionStateRepositoryImpl
import app.invigilator.core.user.UserRepository
import app.invigilator.core.user.UserRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindConsentRepository(impl: ConsentRepositoryImpl): ConsentRepository

    @Binds
    @Singleton
    abstract fun bindLinkingRepository(impl: LinkingRepositoryImpl): LinkingRepository

    @Binds
    @Singleton
    abstract fun bindSessionStateRepository(impl: SessionStateRepositoryImpl): SessionStateRepository

    companion object {

        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

        @Provides
        @Singleton
        fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance("asia-south1")
    }
}
