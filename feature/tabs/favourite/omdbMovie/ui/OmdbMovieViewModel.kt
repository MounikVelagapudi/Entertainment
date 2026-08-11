package .omdbMovie.ui
                    

import .omdbMovie.data.repository.OmdbMovieRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OmdbMovieViewModel @Inject constructor(private val repository: OmdbMovieRepository) {

}
            