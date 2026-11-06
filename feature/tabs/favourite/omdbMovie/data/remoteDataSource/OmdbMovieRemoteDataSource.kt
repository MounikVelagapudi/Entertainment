package .omdbMovie.data.remoteDataSource

import .omdbMovie.data.api.OmdbMovieApiService
import javax.inject.Inject

class OmdbMovieRemoteDataSource @Inject constructor(
    private val apiService: OmdbMovieApiService
) {

}
