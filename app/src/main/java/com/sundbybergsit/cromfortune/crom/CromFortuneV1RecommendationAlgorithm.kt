package com.sundbybergsit.cromfortune.crom

import android.content.Context
import com.sundbybergsit.cromfortune.algorithm.BuyStockCommand
import com.sundbybergsit.cromfortune.algorithm.Recommendation
import com.sundbybergsit.cromfortune.algorithm.RecommendationAlgorithm
import com.sundbybergsit.cromfortune.algorithm.SellStockCommand
import com.sundbybergsit.cromfortune.stocks.StockOrder
import com.sundbybergsit.cromfortune.stocks.StockPrice
import java.time.Instant
import java.util.*

class CromFortuneV1RecommendationAlgorithm(private val context: Context) : RecommendationAlgorithm() {

    companion object {

        const val DEFAULT_PURCHASE_ORDER_IN_SEK: Double = 3000.0
        const val NORMAL_DIFF_PERCENTAGE: Double = .20
        const val MAX_BUY_PERCENTAGE: Double = .10
        const val MAX_SOLD_PERCENTAGE: Double = .10
        const val MAX_EXTREME_BUY_PERCENTAGE: Double = .20
        const val MAX_EXTREME_SOLD_PERCENTAGE: Double = .75

    }

    override fun getRecommendation(
            stockPrice: StockPrice, currencyRateInSek: Double, commissionFee: Double, previousOrders: Set<StockOrder>,
    ): Recommendation? {
        return getRecommendation(stockPrice.stockSymbol, stockPrice.currency,
                currencyRateInSek, previousOrders, stockPrice.price, commissionFee)
    }

    private fun getRecommendation(
            stockName: String, currency: Currency, rateInSek: Double,
            orders: Set<StockOrder>, currentStockPriceInStockCurrency: Double, commissionFeeInSek: Double,
    ): Recommendation? {
        if (orders.isEmpty()) {
            // Dummy recommendation to mimic first buy
            return Recommendation(BuyStockCommand(context, Instant.now().toEpochMilli(), currency, stockName,
                    currentStockPriceInStockCurrency, 1, commissionFeeInSek))
        }
        val sortedOrders = orders.toSortedSet { s1, s2 -> s1.dateInMillis.compareTo(s2.dateInMillis) }
        var grossQuantity = 0
        var soldQuantity = 0
        var accumulatedCostInSek = 0.0
        for (stockOrder in sortedOrders) {
            if (stockOrder.name == stockName) {
                if (stockOrder.orderAction == "Buy") {
                    grossQuantity += stockOrder.quantity
                    accumulatedCostInSek += rateInSek * stockOrder.pricePerStock * stockOrder.quantity +
                            stockOrder.commissionFee
                } else {
                    soldQuantity += stockOrder.quantity
                }
            }
        }
        val currentStockPriceInSek = currentStockPriceInStockCurrency * rateInSek
        val currentTimeInMillis = System.currentTimeMillis()
        if (grossQuantity - soldQuantity == 0 && isCurrentStockBelowLastSale(sortedOrders.last(), currentStockPriceInStockCurrency)) {
            val buyQuantity: Int = ((DEFAULT_PURCHASE_ORDER_IN_SEK - commissionFeeInSek) / currentStockPriceInSek).toInt()
            val netStockPriceInStockCurrency = ((commissionFeeInSek / rateInSek) + currentStockPriceInStockCurrency * buyQuantity) / buyQuantity
            if (buyQuantity > 0 && isCurrentStockBelowLastSale(sortedOrders.last(), netStockPriceInStockCurrency)) {
                return Recommendation(BuyStockCommand(context, currentTimeInMillis, currency, stockName,
                        currentStockPriceInStockCurrency, buyQuantity, commissionFeeInSek))
            }
        }
        val netQuantity = grossQuantity - soldQuantity
        val averageCostInSek = accumulatedCostInSek / grossQuantity
        val costToExcludeInSek = averageCostInSek * soldQuantity

        val totalPricePerStockInSek = (accumulatedCostInSek - costToExcludeInSek) / netQuantity
        val totalPricePerStockInStockCurrency = totalPricePerStockInSek / rateInSek
        var tradeQuantity = netQuantity / 10
        var recommendation: Recommendation? = null
        var isOkToContinue = true
        while (isOkToContinue) {
            val pricePerStockAfterBuyInStockCurrency = ((netQuantity * averageCostInSek +
                    tradeQuantity * currentStockPriceInSek + commissionFeeInSek) /
                    (netQuantity + tradeQuantity)) / rateInSek
            if (isCurrentStockPriceHighEnoughToSell(tradeQuantity, currentStockPriceInStockCurrency, totalPricePerStockInStockCurrency, commissionFeeInSek / rateInSek)) {
                if (isNotOverSoldForMediumStockPriceIncrease(tradeQuantity, soldQuantity, grossQuantity)) {
                    isOkToContinue = true
                    recommendation = Recommendation(SellStockCommand(context, currentTimeInMillis, currency, stockName,
                            currentStockPriceInStockCurrency, tradeQuantity, commissionFeeInSek))
                    tradeQuantity += 1
                } else {
                    if (isNotOverSoldForHighStockPriceIncrease(tradeQuantity, soldQuantity, grossQuantity)) {
                        isOkToContinue = true
                        recommendation = Recommendation(SellStockCommand(context, currentTimeInMillis, currency, stockName,
                                currentStockPriceInStockCurrency, tradeQuantity, commissionFeeInSek))
                        tradeQuantity += 1
                    } else {
                        return recommendation
                    }
                }
            } else if (currentStockPriceLowEnoughForBuy(currentStockPriceInStockCurrency, pricePerStockAfterBuyInStockCurrency)) {
                if (isNotOverBoughtForMediumStockPriceDecrease(tradeQuantity, soldQuantity, grossQuantity)) {
                    isOkToContinue = true
                    recommendation = Recommendation(BuyStockCommand(context, currentTimeInMillis, currency, stockName,
                            currentStockPriceInStockCurrency, tradeQuantity, commissionFeeInSek))
                    tradeQuantity += 1
                } else {
                    if (isNotOverBoughtForHighStockPriceDecrease(tradeQuantity, soldQuantity, grossQuantity)) {
                        isOkToContinue = true
                        recommendation = Recommendation(BuyStockCommand(context, currentTimeInMillis, currency, stockName,
                                currentStockPriceInStockCurrency, tradeQuantity, commissionFeeInSek))
                        tradeQuantity += 1
                    } else {
                        isOkToContinue = false
                    }
                }
            } else {
                isOkToContinue = false
            }
        }
        return recommendation
    }

    private fun isCurrentStockBelowLastSale(lastSaleOrder: StockOrder, currentStockPrice: Double) =
            lastSaleOrder.pricePerStock >= currentStockPrice * (1 + NORMAL_DIFF_PERCENTAGE)

    private fun isCurrentStockPriceHighEnoughToSell(
            tradeQuantity: Int, stockPrice: Double, totalPricePerStockInStockCurrency: Double, commissionFee: Double,
    ) = tradeQuantity * stockPrice > (tradeQuantity * ((1 + NORMAL_DIFF_PERCENTAGE) * totalPricePerStockInStockCurrency) + commissionFee)

    private fun isNotOverBoughtForHighStockPriceDecrease(tradeQuantity: Int, soldQuantity: Int, grossQuantity: Int) =
            isNotOverBought(tradeQuantity, soldQuantity, grossQuantity, MAX_EXTREME_BUY_PERCENTAGE)

    private fun isNotOverBoughtForMediumStockPriceDecrease(tradeQuantity: Int, soldQuantity: Int, grossQuantity: Int) =
            isNotOverBought(tradeQuantity, soldQuantity, grossQuantity, MAX_BUY_PERCENTAGE)

    private fun isNotOverSoldForHighStockPriceIncrease(tradeQuantity: Int, soldQuantity: Int, grossQuantity: Int) =
            isNotOverSold(tradeQuantity, soldQuantity, grossQuantity, MAX_EXTREME_SOLD_PERCENTAGE)

    private fun isNotOverSoldForMediumStockPriceIncrease(tradeQuantity: Int, soldQuantity: Int, grossQuantity: Int) =
            isNotOverSold(tradeQuantity, soldQuantity, grossQuantity, MAX_SOLD_PERCENTAGE)

    private fun isNotOverBought(tradeQuantity: Int, soldQuantity: Int, grossQuantity: Int, threshold: Double) =
            tradeQuantity > 0 && (soldQuantity + tradeQuantity) <= grossQuantity * threshold

    private fun isNotOverSold(tradeQuantity: Int, soldQuantity: Int, grossQuantity: Int, threshold: Double) =
            tradeQuantity > 0 && (soldQuantity + tradeQuantity) <= grossQuantity * threshold

    private fun currentStockPriceLowEnoughForBuy(stockPrice: Double, predictedStockPriceAfterBuy: Double) =
            stockPrice < (1 - NORMAL_DIFF_PERCENTAGE) * predictedStockPriceAfterBuy

}
