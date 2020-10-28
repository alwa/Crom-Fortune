package com.sundbybergsit.cromfortune.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sundbybergsit.cromfortune.R
import com.sundbybergsit.cromfortune.stocks.StocksPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class HomeViewModel : ViewModel() {

    private val _viewState = MutableLiveData<ViewState>()
    private val _addStockState = MutableLiveData<AddStockState>()

    val viewState: LiveData<ViewState> = _viewState
    val addStockState: LiveData<AddStockState> = _addStockState

    fun refresh(context: Context) {
        val sharedPreferences = context.getSharedPreferences(StocksPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (sharedPreferences.all.isEmpty()) {
            _viewState.postValue(ViewState.HasNoStocks(R.string.home_no_stocks))
        } else {
            _viewState.postValue(ViewState.HasStocks(R.string.home_stocks))
        }
    }

    fun save(context: Context, stockOrder: StockOrder) {
        val sharedPreferences = context.getSharedPreferences(StocksPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (sharedPreferences.contains(stockOrder.name)) {
            _addStockState.postValue(AddStockState.Error(R.string.generic_error_not_supported))
        } else {
            sharedPreferences.edit()
                    .putStringSet(stockOrder.name, setOf(Json.encodeToString(stockOrder))).apply()
            _addStockState.postValue(AddStockState.Saved)
        }
        refresh(context)
    }

    sealed class ViewState {
        data class HasStocks(@StringRes val textResId: Int) : ViewState()
        data class HasNoStocks(@StringRes val textResId: Int) : ViewState()
    }

    sealed class AddStockState {
        object Saved : AddStockState()
        data class Error(@StringRes val errorResId: Int) : AddStockState()
    }

}
