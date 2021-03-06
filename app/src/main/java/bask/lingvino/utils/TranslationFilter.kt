package bask.lingvino.utils

import android.widget.Filter
import androidx.fragment.app.FragmentActivity
import bask.lingvino.adapters.TranslationsAdapter
import bask.lingvino.fragments.TranslatorFavouritesFragment
import bask.lingvino.models.Translation


class TranslationFilter(private val adapter: TranslationsAdapter,
                        private val originalTranslations: ArrayList<Translation>,
                        private val activity: FragmentActivity
) : Filter() {

    private var translationsFiltered: ArrayList<Translation> = originalTranslations

    override fun performFiltering(prefix: CharSequence?): FilterResults {
        // Initialise an object that holds the query results.
        val results = FilterResults()

        if (prefix.isNullOrEmpty()) {
            // Returns the original list as the prefix is empty.
            results.values = translationsFiltered
            results.count = translationsFiltered.size
        } else {
            // List of objects that match the criteria.
            val filteredTranslations = translationsFiltered.filter {
                // Compare query with attributes 'input' and 'translation', ignoring case.
                it.input.contains(prefix.toString(), true) ||
                        it.translation.contains(prefix.toString(), true)
            }
            results.values = filteredTranslations
            results.count = filteredTranslations.size
        }
        return results
    }

    @Suppress("unchecked_cast")
    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
        adapter.translations = results?.values as ArrayList<Translation>
        adapter.notifyDataSetChanged()

        val fragment = activity.supportFragmentManager.findFragmentByTag(
            "TranslatorFavouritesFragment"
        ) as TranslatorFavouritesFragment
        fragment.noResultsVisible(
            results.count == 0
        ) // If no results, show  a feedback message to the user.
    }

    fun filterBySourceAndTarget(source: String, target: String) {
        val results = FilterResults()
        val filteredTranslations: List<Translation>
        if (source == "All languages" && target == "All languages") {
            results.values = originalTranslations
            results.count = originalTranslations.size
            translationsFiltered = originalTranslations
        } else if (source == "All languages") {
            filteredTranslations = originalTranslations.filter {
                it.targetLang == target
            }
            results.values = filteredTranslations
            results.count = filteredTranslations.size
            translationsFiltered = filteredTranslations as ArrayList<Translation>
        } else if (target == "All languages") {
            filteredTranslations = originalTranslations.filter {
                it.sourceLang == source
            }
            results.values = filteredTranslations
            results.count = filteredTranslations.size
            translationsFiltered = filteredTranslations as ArrayList<Translation>
        } else {
            filteredTranslations = originalTranslations.filter {
                it.sourceLang == source && it.targetLang == target
            }
            results.values = filteredTranslations
            results.count = filteredTranslations.size
            translationsFiltered = filteredTranslations as ArrayList<Translation>
        }

        publishResults("", results)
    }
}