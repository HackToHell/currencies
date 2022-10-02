package de.salomax.currencies.view.timeline

import android.annotation.SuppressLint
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.robinhood.spark.SparkView
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.util.dpToPx
import de.salomax.currencies.util.toHumanReadableNumber
import de.salomax.currencies.view.BaseActivity
import de.salomax.currencies.viewmodel.timeline.TimelineViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class TimelineActivity : BaseActivity() {

    //
    private val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    private lateinit var timelineModel: TimelineViewModel

    // views
    private var menuItemToggle: MenuItem? = null

    private lateinit var refreshIndicator: LinearProgressIndicator
    private lateinit var timelineChart: SparkView
    private lateinit var textProvider: TextView
    private lateinit var textRateDifference: TextView
    private lateinit var divider: View

    private lateinit var textPastRateDate: TextView
    private lateinit var textPastRateSymbol: TextView
    private lateinit var textPastRateValue: TextView

    private lateinit var textCurrentRateDate: TextView
    private lateinit var textCurrentRateSymbol: TextView
    private lateinit var textCurrentRateValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // general layout
        setContentView(R.layout.activity_timeline)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        // what currencies to convert
        val currencyFrom =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getSerializableExtra("ARG_FROM", Currency::class.java) ?: Currency.EUR
            else
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("ARG_FROM")?.let { it as Currency } ?: Currency.EUR

        val currencyTo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getSerializableExtra("ARG_TO", Currency::class.java) ?: Currency.USD
            else
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("ARG_TO")?.let { it as Currency } ?: Currency.USD

        // model
        this.timelineModel = ViewModelProvider(
            this,
            TimelineViewModel.Factory(this.application, currencyFrom, currencyTo)
        )[TimelineViewModel::class.java]

        // views
        findViews()

        // configure timeline view
        initChartView()

        // listeners & stuff
        setListeners()

        // heavy lifting
        observe()

        // foldable devices
        prepareFoldableLayoutChanges()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.timeline, menu)
        menuItemToggle = menu.findItem(R.id.toggle)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.toggle -> {
                timelineModel.toggleCurrencies()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun findViews() {
        this.refreshIndicator = findViewById(R.id.refreshIndicator)
        this.timelineChart = findViewById(R.id.timeline_chart)
        this.textProvider = findViewById(R.id.textProvider)
        this.textRateDifference = findViewById(R.id.text_rate_difference_percent)
        this.divider = findViewById(R.id.divider)

        this.textPastRateDate = findViewById(R.id.text_date_past)
        this.textPastRateSymbol = findViewById(R.id.text_symbol_past)
        this.textPastRateValue = findViewById(R.id.text_rate_past)

        this.textCurrentRateDate = findViewById(R.id.text_date_current)
        this.textCurrentRateSymbol = findViewById(R.id.text_symbol_current)
        this.textCurrentRateValue = findViewById(R.id.text_rate_current)
    }

    private fun initChartView() {
        timelineChart.apply {
            // dashed baseline
            baseLinePaint = baseLinePaint.apply {
                strokeWidth = 1f.dpToPx()
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(1f.dpToPx(), 4f.dpToPx()), 0f)
            }
            // scrub (tooltip)
            scrubListener = SparkView.OnScrubListener { data ->
                data as Map.Entry<*, *>?
                timelineModel.setPastDate(data?.key as LocalDate?)
            }
            // adapter
            adapter = ChartAdapter()
        }
    }

    private fun setListeners() {
        findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.toggleButton)
            .addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked)
                    when (checkedId) {
                        R.id.button_week -> timelineModel.setTimePeriod(TimelineViewModel.Period.WEEK)
                        R.id.button_month -> timelineModel.setTimePeriod(TimelineViewModel.Period.MONTH)
                        R.id.button_year -> timelineModel.setTimePeriod(TimelineViewModel.Period.YEAR)
                    }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun observe() {
        // title
        timelineModel.getTitle().observe(this) {
            title = it
        }

        // error
        timelineModel.getError().observe(this) {
            findViewById<TextView>(R.id.error).apply {
                visibility = View.VISIBLE
                text = it
            }
            // disable toggle button, when there was an error
            menuItemToggle?.isEnabled = it == null
        }

        // progress bar
        timelineModel.isUpdating().observe(this) { isRefreshing ->
            refreshIndicator.visibility = if (isRefreshing) View.VISIBLE else View.GONE
            // disable toggle button, when data is updating
            menuItemToggle?.isEnabled = isRefreshing.not()
        }

        // populate the chart
        timelineModel.getRates().observe(this) {
            (timelineChart.adapter as ChartAdapter).entries = it?.entries?.toList()
        }

        // provider info
        timelineModel.getProvider().observe(this) {
            textProvider.text = if (it != null)
                HtmlCompat.fromHtml(
                    getString(R.string.data_provider, it),
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                )
            else
                null
        }

        // difference in percent
        timelineModel.getRatesDifferencePercent().observe(this) {
            textRateDifference.text = it?.toHumanReadableNumber(this, 2, true, "%")
            if (it != null) {
                textRateDifference.setTextColor(
                    MaterialColors.getColor(
                        this,
                        if (it < 0) R.attr.colorError
                        else R.attr.colorPrimary,
                        null
                    )
                )
            }
        }

        // past rate
        timelineModel.getRatePast().observe(this) {
            val rate = it?.value
            if (rate != null) {
                textPastRateDate.text = it.key.format(formatter)
                textPastRateSymbol.text = rate.currency.symbol()
                textPastRateValue.text = rate.value.toHumanReadableNumber(this, decimalPlaces = 3)
                // only show the divider if this row is populated
                // highest chance of it populated is with this "past rate" data
                divider.visibility = View.VISIBLE
            } else {
                divider.visibility = View.GONE
            }
        }

        // current rate
        timelineModel.getRateCurrent().observe(this) {
            val rate = it?.value
            if (rate != null) {
                textCurrentRateDate.text = it.key.format(formatter)
                textCurrentRateSymbol.text = rate.currency.symbol()
                textCurrentRateValue.text = rate.value.toHumanReadableNumber(this, decimalPlaces = 3)
            }
        }

        // average rate
        timelineModel.getRatesAverage().observe(this) {
            populateStat(
                findViewById(R.id.stats_row_1),
                getString(R.string.rate_average),
                it?.currency?.symbol(),
                it?.value,
                null
            )
        }

        // min rate
        timelineModel.getRatesMin().observe(this) {
            val rate = it.first
            populateStat(
                findViewById(R.id.stats_row_2),
                getString(R.string.rate_min),
                rate?.currency?.symbol(),
                rate?.value,
                it.second
            )
        }

        // max rate
        timelineModel.getRatesMax().observe(this) {
            val rate = it.first
            populateStat(
                findViewById(R.id.stats_row_3),
                getString(R.string.rate_max),
                rate?.currency?.symbol(),
                rate?.value,
                it.second
            )
        }

    }

    private fun populateStat(parent: View, title: String?, symbol: String?, value: Float?, date: LocalDate?) {
        // hide entire row when there's no data
        parent.visibility = if (symbol == null) View.GONE else View.VISIBLE
        // hide dotted line when there's no date
        parent.findViewById<View>(R.id.dotted_line).visibility = if (date == null) View.GONE else View.VISIBLE

        parent.findViewById<TextView>(R.id.text).text = title
        parent.findViewById<TextView>(R.id.text2).text = symbol
        parent.findViewById<TextView>(R.id.text3).text = value?.toHumanReadableNumber(this, 3)
        parent.findViewById<TextView>(R.id.text4).text = date?.format(formatter)
    }

    private fun prepareFoldableLayoutChanges() {
        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@TimelineActivity)
                    .windowLayoutInfo(this@TimelineActivity)
                    .collect { newLayoutInfo ->
                        newLayoutInfo.displayFeatures.filterIsInstance(FoldingFeature::class.java)
                            .firstOrNull ()?.let { foldingFeature ->
                                // portrait
                                if (foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL) {
                                    if (foldingFeature.state == FoldingFeature.State.HALF_OPENED)
                                        findViewById<LinearLayout>(R.id.timeline_root).orientation = LinearLayout.HORIZONTAL
                                    else
                                        findViewById<LinearLayout>(R.id.timeline_root).orientation = LinearLayout.VERTICAL
                                }
                                // landscape
                                else {
                                    if (foldingFeature.state == FoldingFeature.State.FLAT || foldingFeature.state == FoldingFeature.State.HALF_OPENED)
                                        findViewById<LinearLayout>(R.id.timeline_root).orientation = LinearLayout.VERTICAL
                                    else
                                        findViewById<LinearLayout>(R.id.timeline_root).orientation = LinearLayout.HORIZONTAL
                                }
                            }
                    }
            }
        }
    }

}
