package com.sundbybergsit.cromfortune.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sundbybergsit.cromfortune.CromFortuneApp
import com.sundbybergsit.cromfortune.R
import com.sundbybergsit.cromfortune.StockDataRetrievalCoroutineWorker
import com.sundbybergsit.cromfortune.algorithm.BuyStockCommand
import com.sundbybergsit.cromfortune.crom.CromFortuneV1RecommendationAlgorithm
import com.sundbybergsit.cromfortune.currencies.CurrencyRateRepository
import com.sundbybergsit.cromfortune.domain.StockOrder
import com.sundbybergsit.cromfortune.domain.StockOrderRepository
import com.sundbybergsit.cromfortune.stocks.StockOrderRepositoryImpl
import com.sundbybergsit.cromfortune.ui.home.view.NameAndValueAdapterItem
import com.sundbybergsit.cromfortune.ui.home.view.StockRemoveClickListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class HomeViewModel : ViewModel(), StockRemoveClickListener {

    companion object {

        const val TAG: String = "HomeViewModel"

    }

    private val _cromStocksViewState = MutableLiveData<ViewState>(ViewState.Loading)
    private val _personalStocksViewState = MutableLiveData<ViewState>(ViewState.Loading)
    private val _dialogViewState = MutableLiveData<DialogViewState>()

    val cromStocksViewState: LiveData<ViewState> = _cromStocksViewState
    val personalStocksViewState: LiveData<ViewState> = _personalStocksViewState
    val dialogViewState: LiveData<DialogViewState> = _dialogViewState
    private var showAll = false

    private val cromStockAggregate: (MutableList<StockOrder>, Context) -> StockOrderAggregate = { sortedStockOrders, context ->
        var stockOrderAggregate: StockOrderAggregate? = null
        val cromSortedStockOrders: MutableList<StockOrder> = mutableListOf()
        for (stockOrder in sortedStockOrders) {
            if (stockOrderAggregate == null) {
                val stockName = com.sundbybergsit.cromfortune.domain.StockPrice.SYMBOLS.find { pair -> pair.first == stockOrder.name }!!.second
                stockOrderAggregate = StockOrderAggregate(
                        (CurrencyRateRepository.currencyRates.value as CurrencyRateRepository.ViewState.VALUES)
                                .currencyRates.find { currencyRate -> currencyRate.iso4217CurrencySymbol == stockOrder.currency }!!.rateInSek,
                        "$stockName (${stockOrder.name})", stockOrder.name,
                        Currency.getInstance(stockOrder.currency))
                cromSortedStockOrders.add(stockOrder)
                stockOrderAggregate.aggregate(stockOrder)
            } else {
                val recommendation = CromFortuneV1RecommendationAlgorithm(context)
                        .getRecommendation(
                            com.sundbybergsit.cromfortune.domain.StockPrice(
                                stockOrder.name,
                                stockOrderAggregate.currency, stockOrder.pricePerStock
                            ),
                                stockOrderAggregate.rateInSek, StockDataRetrievalCoroutineWorker.COMMISSION_FEE,
                                cromSortedStockOrders.toSet(), stockOrder.dateInMillis)
                when (recommendation?.command) {
                    is BuyStockCommand -> {
                        val buyOrder = StockOrder(
                            "Buy", stockOrderAggregate.currency.toString(),
                            stockOrder.dateInMillis, stockOrder.name, stockOrder.pricePerStock,
                            StockDataRetrievalCoroutineWorker.COMMISSION_FEE, recommendation.command.quantity()
                        )
                        cromSortedStockOrders.add(buyOrder)
                        stockOrderAggregate.aggregate(buyOrder)
                    }
                    is com.sundbybergsit.cromfortune.algorithm.SellStockCommand -> {
                        val sellOrder = StockOrder(
                            "Sell", stockOrderAggregate.currency.toString(),
                            stockOrder.dateInMillis, stockOrder.name, stockOrder.pricePerStock,
                            StockDataRetrievalCoroutineWorker.COMMISSION_FEE, recommendation.command.quantity()
                        )
                        cromSortedStockOrders.add(sellOrder)
                        stockOrderAggregate.aggregate(sellOrder)
                    }
                    else -> {
                        // Do nothing
                    }
                }
            }
        }
        stockOrderAggregate!!
    }

    private val personalStockAggregate: (MutableList<StockOrder>, Context) -> StockOrderAggregate = { sortedStockOrders, _ ->
        var stockOrderAggregate: StockOrderAggregate? = null
        for (stockOrder in sortedStockOrders) {
            if (stockOrderAggregate == null) {
                val stockName = com.sundbybergsit.cromfortune.domain.StockPrice.SYMBOLS.find { pair -> pair.first == stockOrder.name }!!.second
                stockOrderAggregate = StockOrderAggregate(
                        (CurrencyRateRepository.currencyRates.value as CurrencyRateRepository.ViewState.VALUES)
                                .currencyRates.find { currencyRate -> currencyRate.iso4217CurrencySymbol == stockOrder.currency }!!.rateInSek,
                        "$stockName (${stockOrder.name})", stockOrder.name,
                        Currency.getInstance(stockOrder.currency))
                stockOrderAggregate.aggregate(stockOrder)
            } else {
                stockOrderAggregate.aggregate(stockOrder)
            }
        }
        stockOrderAggregate!!
    }

    @SuppressLint("ApplySharedPref")
    override fun onClickRemove(context: Context, stockName: String) {
        _dialogViewState.postValue(DialogViewState.ShowDeleteDialog(stockName))
    }

    fun refresh(context: Context) {
        val stockOrderRepository: StockOrderRepository = StockOrderRepositoryImpl(context)
        if (stockOrderRepository.isEmpty()) {
            _cromStocksViewState.postValue(ViewState.HasNoStocks(R.string.home_no_stocks))
            _personalStocksViewState.postValue(ViewState.HasNoStocks(R.string.home_no_stocks))
        } else {
            viewModelScope.launch {
                _cromStocksViewState.postValue(ViewState.HasStocks(R.string.home_stocks,
                        StockAggregateAdapterItemUtil.convertToAdapterItems(
                            list = stocks(context = context,
                                    lambda = cromStockAggregate)
                        )))
                _personalStocksViewState.postValue(ViewState.HasStocks(R.string.home_stocks,
                        StockAggregateAdapterItemUtil.convertToAdapterItems(list = stocks(context = context,
                                lambda = personalStockAggregate))))
            }
        }
    }

    fun save(context: Context, stockOrder: StockOrder) {
        val stockOrderRepository: StockOrderRepository = StockOrderRepositoryImpl(context)
        if (stockOrderRepository.list(stockOrder.name).isNotEmpty()) {
            val existingOrders = stockOrderRepository.list(stockOrder.name)
            stockOrderRepository.putAll(stockOrder.name, existingOrders.toMutableSet() + stockOrder)
        } else {
            stockOrderRepository.putReplacingAll(stockOrder.name, stockOrder)
        }
        refresh(context)
    }

    fun personalStockOrders(context: Context, stockSymbol: String): List<StockOrder> {
        return stocks(context, personalStockAggregate)
                .find { stockOrderAggregate -> stockOrderAggregate.stockSymbol == stockSymbol }!!.orders.toList()
    }

    fun stocks(context: Context, lambda: (MutableList<StockOrder>, Context) -> StockOrderAggregate):
            List<StockOrderAggregate> {
        val stockOrderRepository: StockOrderRepository = StockOrderRepositoryImpl(context)
        val stockOrderAggregates: MutableList<StockOrderAggregate> = mutableListOf()
        for (stockSymbol in stockOrderRepository.listOfStockNames()) {
            val stockOrders: Set<StockOrder> = stockOrderRepository.list(stockSymbol)
            if (stockOrders.isEmpty()) {
                // Preventive cleanup, https://github.com/Sundbybergs-IT/Crom-Fortune/issues/20
                stockOrderRepository.remove(stockSymbol)
            } else {
                val sortedStockOrders: MutableList<StockOrder> = stockOrders.toMutableList()
                sortedStockOrders.sortBy { stockOrder -> stockOrder.dateInMillis }
                val stockAggregate = lambda(sortedStockOrders, context)
                if (!showAll && stockAggregate.getQuantity() == 0) {
                    Log.i(TAG, "Hiding this stock because of the filter option.")
                } else {
                    stockOrderAggregates.add(stockAggregate)
                }
            }
        }
        return stockOrderAggregates.sortedBy { stockOrderAggregate -> stockOrderAggregate.displayName }
    }

    fun cromStockOrders(context: Context, stockSymbol: String): List<StockOrder> {
        return stocks(context, cromStockAggregate)
                .find { stockOrderAggregate -> stockOrderAggregate.stockSymbol == stockSymbol }!!.orders.toList()
    }

    fun hasNumberOfStocks(context: Context, stockName: String, quantity: Int): Boolean {
        return StockOrderRepositoryImpl(context).count(stockName) >= quantity
    }

    fun confirmRemove(context: Context, stockName: String) {
        val stockOrderRepository: StockOrderRepository = StockOrderRepositoryImpl(context)
        stockOrderRepository.remove(stockName)
        refresh(context)
    }

    fun refreshData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            StockDataRetrievalCoroutineWorker.refreshFromYahoo(context)
            Log.i(TAG, "Last refreshed: " + (context.applicationContext as CromFortuneApp).lastRefreshed)
        }
    }

    fun showAll(context: Context) {
        showAll = true
        if (_personalStocksViewState.value is ViewState.HasStocks) {
            refresh(context)
        }
    }

    fun showCurrent(context: Context) {
        showAll = false
        if (_personalStocksViewState.value is ViewState.HasStocks) {
            refresh(context)
        }
    }

    sealed class DialogViewState {

        data class ShowDeleteDialog(val stockName: String) : DialogViewState()

    }

    sealed class ViewState {

        object Loading : ViewState()

        data class HasStocks(@StringRes val textResId: Int, val adapterItems: List<NameAndValueAdapterItem>) : ViewState()

        data class HasNoStocks(@StringRes val textResId: Int) : ViewState()

    }

}
