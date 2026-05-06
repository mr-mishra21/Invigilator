package app.invigilator.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.auth.AuthRepositoryImpl
import app.invigilator.core.consent.ConsentRepository
import app.invigilator.core.consent.ConsentRepositoryImpl
import app.invigilator.core.intervention.AppLanguageRepository
import app.invigilator.core.intervention.AppLanguageRepositoryImpl
import app.invigilator.core.linking.LinkingRepository
import app.invigilator.core.linking.LinkingRepositoryImpl
import app.invigilator.core.session.SessionStateRepository
import app.invigilator.core.session.SessionStateRepositoryImpl
import app.invigilator.core.session.SessionStatsRepository
import app.invigilator.core.session.SessionStatsRepositoryImpl
import app.invigilator.core.session.SessionSummaryRepository
import app.invigilator.core.session.SessionSummaryRepositoryImpl
import app.invigilator.core.user.UserRepository
import app.invigilator.core.user.UserRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Binds
    @Singleton
    abstract fun bindSessionStatsRepository(
        impl: SessionStatsRepositoryImpl
    ): SessionStatsRepository

    @Binds
    @Singleton
    abstract fun bindSessionSummaryRepository(
        impl: SessionSummaryRepositoryImpl
    ): SessionSummaryRepository

    @Binds
    @Singleton
    abstract fun bindAppLanguageRepository(
        impl: AppLanguageRepositoryImpl
    ): AppLanguageRepository

    companion object {

        @Provides
        @Singleton
        fun providePreferencesDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("invigilator_prefs") }
        )

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
