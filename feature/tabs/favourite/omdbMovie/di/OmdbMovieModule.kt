package .omdbMovie.di
            
import .omdbMovie.data.api.OmdbMovieApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OmdbMovieModule {

    @Singleton
    @Provides
    fun provideApiService(retrofit: Retrofit): OmdbMovieApiService {
        return retrofit.create(OmdbMovieApiService::class.java)
    }
    
}
