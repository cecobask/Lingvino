package bask.lingvino.fragments

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.StrictMode
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import bask.lingvino.R
import bask.lingvino.models.WordOfTheDay
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.createBalloon
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class WordOfTheDayFragment : Fragment() {

    private lateinit var databaseRef: DatabaseReference
    private lateinit var WOTDDB: DatabaseReference
    private lateinit var mediaPlayer: MediaPlayer // For playing word pronunciation.
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var sharedPref: SharedPreferences
    private lateinit var date: String

    private lateinit var wotdDateTV: TextView
    private lateinit var wotdTV: TextView
    private lateinit var wotdPronounceFAB: FloatingActionButton
    private lateinit var wotdRandomFAB: FloatingActionButton
    private lateinit var wotdTransliterationTV: TextView
    private lateinit var wotdTypeTV: TextView
    private lateinit var wotdDefinitionTV: TextView
    private lateinit var wotdExampleTV: TextView
    private lateinit var wotdLoveFAB: FloatingActionButton
    private lateinit var wotdListFAB: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var balloon: Balloon

    companion object {
        private const val argKey = "date"
        fun newInstance(date: String): WordOfTheDayFragment {
            val args = Bundle().apply { putString(argKey, date) }
            return WordOfTheDayFragment().apply { arguments = args }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        arguments?.getString("date").let { date = it!! }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.wordoftheday, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fix for error caused by: android.os.NetworkOnMainThreadException.
        val policy: StrictMode.ThreadPolicy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Change toolbar title.
        (activity as? AppCompatActivity)?.supportActionBar?.title =
            resources.getString(R.string.wotd)

        wotdDateTV = view.findViewById(R.id.wotdDateTV)
        wotdTV = view.findViewById(R.id.wotdTV)
        wotdPronounceFAB = view.findViewById(R.id.wotdPronounceFAB)
        wotdRandomFAB = view.findViewById(R.id.wotdRandomFAB)
        wotdTransliterationTV = view.findViewById(R.id.wotdTransliterationTV)
        wotdTypeTV = view.findViewById(R.id.wotdTypeTV)
        wotdDefinitionTV = view.findViewById(R.id.wotdDefinitionTV)
        wotdExampleTV = view.findViewById(R.id.wotdExampleTV)
        wotdLoveFAB = view.findViewById(R.id.wotdLoveFAB)
        wotdListFAB = view.findViewById(R.id.wotdListFAB)
        progressBar = view.findViewById(R.id.progressBar)

        // Create balloon tooltip for later use.
        balloon = createBalloon(context!!) {
            setArrowOrientation(ArrowOrientation.LEFT)
            setWidthRatio(0.6f)
            setHeight(70)
            setCornerRadius(10f)
            setAlpha(1f)
            setText("Tap for translation")
            setTextSize(18f)
            setTextTypeface(Typeface.BOLD)
            setTextColorResource(R.color.white)
            setBackgroundColorResource(R.color.colorPrimaryDark)
            setBalloonAnimation(BalloonAnimation.CIRCULAR)
            setDismissWhenTouchOutside(true)
            setDismissWhenClicked(true)
            setIconDrawable(ContextCompat.getDrawable(context!!, R.drawable.ic_tap))
            setShowTime(1)
        }

        // Show progress bar until Firebase retrieves data from the database.
        progressBar.visibility = View.VISIBLE

        firebaseAuth = FirebaseAuth.getInstance()
        databaseRef = FirebaseDatabase.getInstance().reference
        WOTDDB = databaseRef.child("wordOfTheDay").child("past")


        // Query Firebase Database for word of the day.
        getWord(date)

        wotdRandomFAB.setOnClickListener {
            getRandomWord()
        }
    }

    private fun getRandomWord() {
        // Show progress bar until Firebase retrieves data from the database.
        progressBar.visibility = View.VISIBLE
        WOTDDB.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
                // Hide progress bar.
                progressBar.visibility = View.GONE
                Timber.tag("random").e(p0.toException())
            }

            override fun onDataChange(p0: DataSnapshot) {
                // Select a random word from the list of previously selected WOTD.
                val dates = mutableListOf<String>()
                p0.children.forEach { dates.add(it.key!!) }
                fragmentManager!!
                    .beginTransaction()
                    .replace(
                        R.id.fragmentContainer,
                        newInstance(dates.random())
                    )
                    .addToBackStack(null)
                    .commit()

                // Hide progress bar.
                progressBar.visibility = View.GONE
            }

        })
    }

    private fun getWord(date: String) {
        sharedPref = activity!!.getSharedPreferences("learnBulgarian", 0)

        // Check if the user had previously visited this fragment.
        val returningUser: Boolean =
            if (!sharedPref.getBoolean("returningUser", false)) {
                // The user is visiting the fragment for the first time.
                sharedPref
                    .edit()
                    .putBoolean("returningUser", true)
                    .apply()
                false
            } else true

        val currentUser = firebaseAuth.currentUser

        WOTDDB.child(date).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(p0: DatabaseError) {
                Timber.tag("wordOfTheDay").d(p0.toException())
            }

            override fun onDataChange(p0: DataSnapshot) {
                // Selected word of the day.
                val currentWOTD = p0.getValue(WordOfTheDay::class.java)!!

                // Set values for all widgets.
                wotdDateTV.text = LocalDate.now()
                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                wotdTV.text = currentWOTD.word
                wotdTransliterationTV.text = currentWOTD.wordTransliteration
                wotdTypeTV.text = currentWOTD.wordType
                wotdDefinitionTV.text = currentWOTD.wordDefinition
                wotdPronounceFAB.visibility = View.VISIBLE
                wotdRandomFAB.visibility = View.VISIBLE
                highlightWord(currentWOTD.exampleSentenceBG, currentWOTD.word, wotdExampleTV)

                // Show balloon tooltip to attract attention to the switchable
                // English/Bulgarian translation.
                if (!returningUser) balloon.showAlignRight(wotdExampleTV)

                // Create MediaPlayer instance that uses URL from Google Cloud Storage.
                // to play word pronunciation.
                mediaPlayer =
                    MediaPlayer.create(context, Uri.parse(currentWOTD.pronunciationURL))
                        .apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                        }


                // Play word pronunciation on click of pronounce FAB.
                wotdPronounceFAB.setOnClickListener { mediaPlayer.start() }

                // Switch between English and Bulgarian example sentences.
                wotdExampleTV.setOnClickListener {
                    if (wotdExampleTV.text.toString() == currentWOTD.exampleSentenceBG) {
                        highlightWord(
                            currentWOTD.exampleSentenceEN,
                            currentWOTD.wordEng,
                            wotdExampleTV
                        )
                    } else {
                        highlightWord(
                            currentWOTD.exampleSentenceBG,
                            currentWOTD.word,
                            wotdExampleTV
                        )
                    }
                }

                // Reference to current user's collection of favourite words.
                val favWordRef = databaseRef.child("users")
                    .child(currentUser!!.uid)
                    .child("favWords")

                // Query user's favourite words collection
                // to check if today's word of the day exists there.
                favWordRef.addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError) {
                        Timber.tag("favWord").d(p0.toException())
                    }

                    override fun onDataChange(p0: DataSnapshot) {
                        // Variable used to determine what action wotdLoveFAB will do.
                        var loveFABAction: String?

                        // Hide progress bar.
                        progressBar.visibility = View.GONE

                        // Show buttons.
                        wotdLoveFAB.visibility = View.VISIBLE
                        wotdListFAB.visibility = View.VISIBLE

                        if (p0.hasChild(date)) {
                            // Word exists in user's collection.
                            // Set action to remove the word from the db if the button is clicked.
                            // Set colour to red.
                            loveFABAction = "hate"
                            wotdLoveFAB.backgroundTintList = ColorStateList.valueOf(Color.RED)
                        } else {
                            // Word doesn't exists in user's collection.
                            // Set action to add the word to the db if the button is clicked.
                            // Set colour to default.
                            loveFABAction = "love"
                            if (isAdded) wotdLoveFAB.backgroundTintList =
                                ColorStateList.valueOf(
                                    ResourcesCompat
                                        .getColor(resources, R.color.colorPrimary, null)
                                )
                        }

                        // Determine what action to take on click of the button (add/remove from db).
                        wotdLoveFAB.setOnClickListener {
                            if (loveFABAction == "love") {
                                loveFABAction = "hate"
                                // Add the word to the db.
                                // Set button colour to red.
                                favWordRef.child(date).setValue(currentWOTD)
                                wotdLoveFAB.backgroundTintList =
                                    ColorStateList.valueOf(Color.RED)
                            } else {
                                loveFABAction = "love"
                                // Remove the word from the db.
                                // Reset button colour.
                                favWordRef.child(date).removeValue()
                                wotdLoveFAB.backgroundTintList =
                                    ColorStateList.valueOf(
                                        ResourcesCompat
                                            .getColor(resources, R.color.colorPrimary, null)
                                    )
                            }
                        }

                        // Open user's favourite words collection in a new Fragment.
                        wotdListFAB.setOnClickListener {
                            fragmentManager!!
                                .beginTransaction()
                                .replace(
                                    R.id.fragmentContainer,
                                    WordOfTheDayFavouritesFragment.newInstance()
                                )
                                .addToBackStack(null)
                                .commit()
                        }

                        wotdListFAB.isEnabled = p0.exists()
                    }

                })
            }
        })
    }

    // Highlights every occurrence of a word in text.
    private fun highlightWord(text: String, word: String, textView: TextView) {
        val replaceWordWith = "<font color='red'>$word</font>"
        val modifiedText = text.replace(word, replaceWordWith, true)
        textView.text = HtmlCompat.fromHtml(modifiedText, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}