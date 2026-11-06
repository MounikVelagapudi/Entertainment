package .omdbMovie.data.repository

import .omdbMovie.data.remoteDataSource.OmdbMovieRemoteDataSource
import javax.inject.Inject

class OmdbMovieRepository @Inject constructor(
    private val remoteDataSource: OmdbMovieRemoteDataSource,
) {

}