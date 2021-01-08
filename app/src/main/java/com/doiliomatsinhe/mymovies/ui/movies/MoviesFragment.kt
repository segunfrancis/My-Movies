package com.doiliomatsinhe.mymovies.ui.movies

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import com.doiliomatsinhe.mymovies.R
import com.doiliomatsinhe.mymovies.adapter.movie.MovieAdapter
import com.doiliomatsinhe.mymovies.adapter.movie.MovieClickListener
import com.doiliomatsinhe.mymovies.adapter.loadstate.LoadStateAdapter
import com.doiliomatsinhe.mymovies.data.Repository
import com.doiliomatsinhe.mymovies.databinding.FragmentMoviesBinding
import com.doiliomatsinhe.mymovies.ui.settings.SettingsActivity
import com.doiliomatsinhe.mymovies.utils.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MoviesFragment : Fragment() {

    private lateinit var binding: FragmentMoviesBinding
    private val viewModel: MoviesViewModel by viewModels()
    private lateinit var adapter: MovieAdapter

    @Inject
    lateinit var sharedPreference: SharedPreferences

    @Inject
    lateinit var repository: Repository


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentMoviesBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initComponents()

        fetchMovies()

    }

    private fun fetchMovies() {
        val category = sharedPreference.getString(CATEGORY_KEY, DEFAULT_CATEGORY)
        val language = sharedPreference.getString(LANGUAGE_KEY, DEFAULT_LANGUAGE)

        lifecycleScope.launch {
            viewModel.getMoviesList(category, language).collectLatest {
                adapter.submitData(it)
            }
        }
    }

    private fun initComponents() {
        setHasOptionsMenu(true)

        binding.lifecycleOwner = viewLifecycleOwner

        initAdapter()
        binding.buttonRetry.setOnClickListener { adapter.retry() }

    }

    private fun initAdapter() {
        adapter = MovieAdapter(
            MovieClickListener {
                findNavController().navigate(
                    MoviesFragmentDirections.actionMoviesFragmentToDetailsFragment(
                        it
                    )
                )
            }).apply {
            addLoadStateListener { loadState ->
                // If list has items. Show
                binding.movieList.isVisible = loadState.source.refresh is LoadState.NotLoading
                // If loading or refreshing show spinner
                binding.movieProgress.isVisible = loadState.source.refresh is LoadState.Loading
                // If initial load fails show Retry button and text
                binding.buttonRetry.isVisible = loadState.source.refresh is LoadState.Error
                binding.moviesError.isVisible = loadState.source.refresh is LoadState.Error
            }
        }

        binding.movieList.adapter = adapter.withLoadStateHeaderAndFooter(
            header = LoadStateAdapter { adapter.retry() },
            footer = LoadStateAdapter { adapter.retry() }
        )

        // RecyclerView
        binding.movieList.hasFixedSize()
        val layoutManager = GridLayoutManager(activity, resources.getInteger(R.integer.span_count))
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val viewType = adapter.getItemViewType(position)
                return if (viewType == LOADSTATE_VIEW_TYPE) 1
                else resources.getInteger(R.integer.span_count)
            }

        }
        binding.movieList.layoutManager = layoutManager
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_menu, menu)

        implementSearch(menu)

        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun implementSearch(menu: Menu) {
        val manager = activity?.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchItem = menu.findItem(R.id.ic_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setSearchableInfo(manager.getSearchableInfo(requireActivity().componentName))

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                //searchView.setQuery("", false)
                searchItem.collapseActionView()
                query?.let {
                    queryMovieList(it)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }

        })


        val expandListener = object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(p0: MenuItem?): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean {
                fetchMovies()
                return true
            }
        }

        val actionMenuItem = menu.findItem(R.id.ic_search)
        actionMenuItem.setOnActionExpandListener(expandListener)
    }

    private fun queryMovieList(movieQuery: String) {
        lifecycleScope.launch {
            viewModel.queryMovieList(movieQuery).collectLatest {
                adapter.submitData(it)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settingsActivity -> openSettings()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openSettings() {
        startActivity(Intent(activity, SettingsActivity::class.java))
    }
}